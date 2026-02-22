package vn.edu.phenikaa.timetablex.algorithm;

import vn.edu.phenikaa.timetablex.entity.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Genetic Algorithm cho bài toán xếp Thời khóa biểu.
 *
 * Chromosome: Một cách xếp TKB (tập hợp các assignment).
 * Gene: Một assignment (sectionId, roomId, shiftId, dayOfWeek).
 * Đơn vị thời gian là CA HỌC (Shift) — mỗi gene chiếm trọn một ca trong ngày.
 *
 * CẢI TIẾN v3:
 * - Hỗ trợ Thứ 7 (day=7) với soft penalty nhỏ, ưu tiên Thứ 2–6 trước.
 * - Cache O(1): sectionMap, shiftMap thay cho stream scan O(n) mỗi lần.
 * - Fitness nâng cao: penalty GV >2 buổi/ngày, Saturday soft penalty,
 * bonus phân bố ngày đồng đều, penalty thiếu buổi, penalty ca tối OFFLINE.
 * - GA parameters lớn hơn: Pop 300, Gen 800, Elitism 12%, Greedy seed 50%.
 * - Tournament size 8, tournament chọn theo Pareto (fitness + conflicts).
 * - Mutation: priority conflict-directed + segment swap (hoán đổi (day, shift)
 * giữa 2 gene bất kỳ trong chromosome).
 * - Greedy: dùng WEEKDAYS (2–6) trước, chỉ fallback sang Thứ 7 khi đã đầy.
 */
public class GeneticTimetableScheduler {

    // ─────────────────────────────────────────────────────────────────────────
    // Fields
    // ─────────────────────────────────────────────────────────────────────────

    private final List<ClassSection> sections;
    private final List<Room> rooms;
    private final List<Shift> shifts;
    private final List<TimeSlot> timeSlots;

    /** Slot đã bị chiếm bởi TKB CONFIRMED */
    private final Set<String> blockedRoomShifts;
    private final Set<String> blockedLecturerShifts;

    /** Cache O(1) — tránh stream scan trong mỗi hàm đánh giá */
    private final Map<Long, ClassSection> sectionMap;
    private final Map<Long, Shift> shiftMap;

    /** Số slot khả dụng của mỗi GV sau khi trừ CONFIRMED */
    private final Map<Long, Integer> lecturerAvailableSlots;

    private ProgressCallback progressCallback;

    // ─────────────────────────────────────────────────────────────────────────
    // GA hyperparameters
    // ─────────────────────────────────────────────────────────────────────────

    private static final int POPULATION_SIZE = 400;
    private static final int MAX_GENERATIONS = 1200;
    private static final int STAGNANT_LIMIT = 120; // Dừng sớm nếu không cải thiện
    private static final double CROSSOVER_RATE = 0.88;
    private static final double BASE_MUTATION_RATE = 0.15;
    private static final double MAX_MUTATION_RATE = 0.55;
    private static final double ELITISM_RATE = 0.15;
    private static final int GREEDY_SEED_COUNT = (int) (POPULATION_SIZE * 0.6); // 60% greedy
    private static final int TOURNAMENT_SIZE = 10;

    // ─────────────────────────────────────────────────────────────────────────
    // Fitness weights
    // ─────────────────────────────────────────────────────────────────────────

    private static final int CONFLICT_PENALTY = 1000;
    private static final int EVENING_OFFLINE_PENALTY = 500;
    private static final int SATURDAY_SOFT_PENALTY = 40; // nhẹ: chỉ tránh Thứ 7 khi có thể
    private static final int DAILY_CLUSTER_PENALTY = 100; // GV có >2 buổi/ngày
    private static final int OVERLOAD_PENALTY = 300;
    private static final int MISSING_SESSION_PENALTY = 800; // thiếu buổi so với yêu cầu
    private static final int DISTRIBUTION_BONUS = 15; // phân bố đều ngày trong tuần

    // ─────────────────────────────────────────────────────────────────────────
    // Day lists
    // ─────────────────────────────────────────────────────────────────────────

    /** Ưu tiên chính: Thứ 2–6 */
    private static final List<Integer> WEEKDAYS = List.of(2, 3, 4, 5, 6);
    /** Mở rộng khi cần: bao gồm Thứ 7 */
    private static final List<Integer> ALL_DAYS = List.of(2, 3, 4, 5, 6, 7);

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────────

    public GeneticTimetableScheduler(
            List<ClassSection> sections,
            List<Room> rooms,
            List<Shift> shifts,
            List<TimeSlot> timeSlots,
            Set<String> blockedRoomShifts,
            Set<String> blockedLecturerShifts) {

        this.sections = sections;
        this.rooms = rooms;
        this.shifts = shifts;
        this.timeSlots = timeSlots;
        this.blockedRoomShifts = blockedRoomShifts != null ? blockedRoomShifts : Collections.emptySet();
        this.blockedLecturerShifts = blockedLecturerShifts != null ? blockedLecturerShifts : Collections.emptySet();
        this.progressCallback = null;

        // Build caches
        this.sectionMap = sections.stream().collect(Collectors.toMap(ClassSection::getId, s -> s));
        this.shiftMap = shifts.stream().collect(Collectors.toMap(Shift::getId, s -> s));

        this.lecturerAvailableSlots = computeLecturerAvailableSlots();
    }

    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }

    public interface ProgressCallback {
        void onProgress(int generation, int maxGenerations, double bestFitness, int conflicts);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Chromosome & Gene
    // ─────────────────────────────────────────────────────────────────────────

    public static class Chromosome {
        private List<Gene> genes;
        private double fitness;
        private int conflicts;

        public Chromosome() {
            this.genes = new ArrayList<>();
            this.fitness = 0;
            this.conflicts = 0;
        }

        public Chromosome(List<Gene> genes) {
            this.genes = new ArrayList<>(genes);
            this.fitness = 0;
            this.conflicts = 0;
        }

        public Chromosome copy() {
            Chromosome c = new Chromosome();
            c.genes = new ArrayList<>(this.genes);
            c.fitness = this.fitness;
            c.conflicts = this.conflicts;
            return c;
        }

        public List<Gene> getGenes() {
            return genes;
        }

        public double getFitness() {
            return fitness;
        }

        public int getConflicts() {
            return conflicts;
        }
    }

    /**
     * Gene: một assignment — lớp học phần xếp vào (phòng, ca học, thứ).
     * Đơn vị thời gian là CA HỌC (shiftId).
     */
    public static class Gene {
        Long sectionId;
        Long roomId;
        Long shiftId;
        Integer dayOfWeek; // 2=T2, 3=T3, 4=T4, 5=T5, 6=T6, 7=T7

        public Gene(Long sectionId, Long roomId, Long shiftId, Integer dayOfWeek) {
            this.sectionId = sectionId;
            this.roomId = roomId;
            this.shiftId = shiftId;
            this.dayOfWeek = dayOfWeek;
        }

        public Gene copy() {
            return new Gene(sectionId, roomId, shiftId, dayOfWeek);
        }

        public Long getSectionId() {
            return sectionId;
        }

        public Long getRoomId() {
            return roomId;
        }

        public Long getShiftId() {
            return shiftId;
        }

        public Integer getDayOfWeek() {
            return dayOfWeek;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Main GA loop
    // ─────────────────────────────────────────────────────────────────────────

    public GeneticResult run() {
        List<Chromosome> population = initializePopulation();

        Chromosome bestChromosome = null;
        double bestFitness = Double.NEGATIVE_INFINITY;
        int stagnantGenerations = 0;
        int totalRequired = getTotalRequiredSessions();

        for (int gen = 0; gen < MAX_GENERATIONS; gen++) {
            boolean improved = false;
            for (Chromosome chr : population) {
                evaluateFitness(chr);
                if (chr.fitness > bestFitness) {
                    bestFitness = chr.fitness;
                    bestChromosome = chr.copy();
                    stagnantGenerations = 0;
                    improved = true;
                }
            }
            if (!improved)
                stagnantGenerations++;

            if (progressCallback != null && bestChromosome != null) {
                progressCallback.onProgress(gen + 1, MAX_GENERATIONS,
                        bestChromosome.fitness, bestChromosome.conflicts);
            }

            // Dừng sớm: không conflict + đủ số buổi
            if (bestChromosome != null
                    && bestChromosome.conflicts == 0
                    && bestChromosome.genes.size() >= totalRequired) {
                if (progressCallback != null) {
                    progressCallback.onProgress(MAX_GENERATIONS, MAX_GENERATIONS,
                            bestChromosome.fitness, 0);
                }
                break;
            }
            if (stagnantGenerations >= STAGNANT_LIMIT) {
                break; // Tránh chạy vô ích
            }

            // Diversity: khi stagnant, thay thế 15% population kém nhất bằng random mới
            if (stagnantGenerations > 0 && stagnantGenerations % 40 == 20) {
                population.sort((a, b) -> Double.compare(b.fitness, a.fitness));
                int replaceFrom = (int) (POPULATION_SIZE * 0.85);
                for (int i = replaceFrom; i < population.size(); i++) {
                    population.set(i, createGreedyChromosome());
                }
            }

            // Adaptive mutation tăng khi stagnant
            double adaptiveMutation = Math.min(MAX_MUTATION_RATE,
                    BASE_MUTATION_RATE + (stagnantGenerations / 80.0) * 0.05);

            population = evolve(population, adaptiveMutation);
        }

        if (progressCallback != null && bestChromosome != null) {
            progressCallback.onProgress(MAX_GENERATIONS, MAX_GENERATIONS,
                    bestChromosome.fitness, bestChromosome.conflicts);
        }
        if (bestChromosome != null) {
            evaluateFitness(bestChromosome);
            // Chạy repair cuối cùng để giảm conflict trước khi trả về
            Random rnd = new Random();
            for (int rep = 0; rep < 3 && bestChromosome.conflicts > 0; rep++)
                repairConflicts(bestChromosome, rnd);
            evaluateFitness(bestChromosome);
        }

        return new GeneticResult(bestChromosome, bestFitness);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Khởi tạo population
    // ─────────────────────────────────────────────────────────────────────────

    private List<Chromosome> initializePopulation() {
        List<Chromosome> population = new ArrayList<>();

        // 50% greedy seeds (conflict-free tối đa)
        for (int i = 0; i < GREEDY_SEED_COUNT; i++) {
            population.add(createGreedyChromosome());
        }

        // 50% ngẫu nhiên (dùng ALL_DAYS để đa dạng)
        for (int i = GREEDY_SEED_COUNT; i < POPULATION_SIZE; i++) {
            population.add(createRandomChromosome());
        }

        return population;
    }

    /**
     * Tạo chromosome ngẫu nhiên — dùng ALL_DAYS (bao gồm Thứ 7).
     * Tránh 2 buổi cùng section trùng ngày-ca.
     */
    private Chromosome createRandomChromosome() {
        Chromosome chromosome = new Chromosome();
        Random rnd = new Random();

        for (ClassSection section : sections) {
            Course course = section.getCourseOffering().getCourse();
            int sessionsPerWeek = determineSessionsPerWeek(section, course);
            String requiredRoomType = determineRequiredRoomType(section, course);

            List<Room> suitableRooms = getSuitableRooms(requiredRoomType);
            List<Shift> allowedShifts = getAllowedShifts(course);

            Set<String> usedDayShifts = new HashSet<>();
            List<Integer> days = new ArrayList<>(ALL_DAYS);

            for (int s = 0; s < sessionsPerWeek; s++) {
                Collections.shuffle(days, rnd);
                Collections.shuffle(allowedShifts, rnd);
                Collections.shuffle(suitableRooms, rnd);

                boolean assigned = false;
                outer: for (Integer day : days) {
                    for (Shift shift : allowedShifts) {
                        String dsKey = day + "-" + shift.getId();
                        if (usedDayShifts.contains(dsKey))
                            continue;

                        Lecturer lec = section.getLecturer();
                        if (lec != null && blockedLecturerShifts.contains(
                                lec.getId() + "-" + day + "-" + shift.getId()))
                            continue;

                        for (Room room : suitableRooms) {
                            if (blockedRoomShifts.contains(room.getId() + "-" + day + "-" + shift.getId()))
                                continue;
                            chromosome.genes.add(new Gene(section.getId(), room.getId(), shift.getId(), day));
                            usedDayShifts.add(dsKey);
                            assigned = true;
                            break outer;
                        }
                    }
                }

                if (!assigned) {
                    // Fallback: thêm gene có thể conflict (để GA có gene để sửa)
                    for (int t = 0; t < 60; t++) {
                        int day = days.get(rnd.nextInt(days.size()));
                        Shift shift = allowedShifts.get(rnd.nextInt(allowedShifts.size()));
                        String dsKey = day + "-" + shift.getId();
                        if (usedDayShifts.contains(dsKey))
                            continue;
                        Lecturer lec = section.getLecturer();
                        if (lec != null && blockedLecturerShifts.contains(
                                lec.getId() + "-" + day + "-" + shift.getId()))
                            continue;
                        Room room = suitableRooms.get(rnd.nextInt(suitableRooms.size()));
                        chromosome.genes.add(new Gene(section.getId(), room.getId(), shift.getId(), day));
                        usedDayShifts.add(dsKey);
                        break;
                    }
                }
            }
        }

        return chromosome;
    }

    /**
     * Tạo chromosome greedy: gán slot lần lượt, ưu tiên Thứ 2–6, fallback Thứ 7.
     *
     * Thứ tự sections: TH trước (phòng PM/TN khan hiếm), rồi LT.
     * Trong cùng loại: tỉ lệ lấp đầy (required/available) cao → xếp trước.
     */
    private Chromosome createGreedyChromosome() {
        Chromosome chromosome = new Chromosome();
        Set<String> usedRoomShifts = new HashSet<>(blockedRoomShifts);
        Set<String> usedLecturerShifts = new HashSet<>(blockedLecturerShifts);
        Map<Long, Integer> lecturerAssigned = new HashMap<>();
        Random rnd = new Random();

        // Tổng session mỗi GV cần
        Map<Long, Integer> lecturerRequired = new HashMap<>();
        for (ClassSection s : sections) {
            if (s.getLecturer() == null)
                continue;
            lecturerRequired.merge(s.getLecturer().getId(), calcSessionsPerWeek(s), (a, b) -> a + b);
        }

        // Sắp xếp sections theo độ khó xếp (TH → fill cao → LT)
        List<ClassSection> sorted = new ArrayList<>(sections);
        sorted.sort((s1, s2) -> {
            boolean th1 = s1.getSectionType() == ClassSection.SectionType.TH;
            boolean th2 = s2.getSectionType() == ClassSection.SectionType.TH;
            if (th1 && !th2)
                return -1;
            if (!th1 && th2)
                return 1;
            double fill1 = fillRatio(s1, lecturerRequired);
            double fill2 = fillRatio(s2, lecturerRequired);
            return Double.compare(fill2, fill1);
        });

        for (ClassSection section : sorted) {
            Course course = section.getCourseOffering().getCourse();
            Lecturer lecturer = section.getLecturer();
            int sessionsPerWeek = determineSessionsPerWeek(section, course);

            // Kiểm tra GV còn slot không
            int cap = lecturer != null ? lecturerAvailableSlots.getOrDefault(lecturer.getId(), Integer.MAX_VALUE)
                    : Integer.MAX_VALUE;
            int assigned = lecturer != null ? lecturerAssigned.getOrDefault(lecturer.getId(), 0) : 0;
            int canAssign = Math.min(sessionsPerWeek, cap - assigned);
            if (canAssign <= 0)
                continue;

            List<Room> suitableRooms = getSuitableRooms(determineRequiredRoomType(section, course));
            List<Shift> allowedShifts = getAllowedShifts(course);
            Set<String> usedDS = new HashSet<>();

            for (int s = 0; s < canAssign; s++) {
                // Thử WEEKDAYS (T2–T6) trước, rồi mới fallback sang Thứ 7
                List<Integer> shuffledPrimary = new ArrayList<>(WEEKDAYS);
                Collections.shuffle(shuffledPrimary, rnd);
                List<Shift> shuffledShifts = new ArrayList<>(allowedShifts);
                Collections.shuffle(shuffledShifts, rnd);
                List<Room> shuffledRooms = new ArrayList<>(suitableRooms);
                Collections.shuffle(shuffledRooms, rnd);

                // Thứ tự tìm: T2-T6 → T7
                List<Integer> searchOrder = new ArrayList<>(shuffledPrimary);
                searchOrder.add(7);

                boolean ok = tryAssign(chromosome, section, lecturer,
                        searchOrder, shuffledShifts, shuffledRooms,
                        usedRoomShifts, usedLecturerShifts, usedDS, lecturerAssigned, false);

                // Fallback nhẹ: chấp nhận room conflict nhưng giữ lecturer OK
                if (!ok) {
                    ok = tryAssign(chromosome, section, lecturer,
                            searchOrder, shuffledShifts, shuffledRooms,
                            usedRoomShifts, usedLecturerShifts, usedDS, lecturerAssigned, true);
                }

                if (!ok) {
                    // Cuối cùng: ép thêm gene (GA sẽ sửa sau)
                    int day = searchOrder.get(rnd.nextInt(searchOrder.size()));
                    Shift shift = shuffledShifts.get(rnd.nextInt(shuffledShifts.size()));
                    String dsKey = day + "-" + shift.getId();
                    if (!usedDS.contains(dsKey)) {
                        String lk = lecturer != null ? lecturer.getId() + "-" + day + "-" + shift.getId() : null;
                        if (lk == null || !usedLecturerShifts.contains(lk)) {
                            Room room = shuffledRooms.get(rnd.nextInt(shuffledRooms.size()));
                            chromosome.genes.add(new Gene(section.getId(), room.getId(), shift.getId(), day));
                            usedDS.add(dsKey);
                            if (lk != null)
                                usedLecturerShifts.add(lk);
                            if (lecturer != null)
                                lecturerAssigned.merge(lecturer.getId(), 1, (a, b) -> a + b);
                        }
                    }
                }
            }
        }

        return chromosome;
    }

    /**
     * Thử gán một session cho section.
     * allowRoomConflict=true: bỏ qua room conflict nhưng vẫn tôn trọng lecturer.
     */
    private boolean tryAssign(
            Chromosome chromosome, ClassSection section, Lecturer lecturer,
            List<Integer> days, List<Shift> shiftList, List<Room> roomList,
            Set<String> usedRoomShifts, Set<String> usedLecturerShifts,
            Set<String> usedDS, Map<Long, Integer> lecturerAssigned,
            boolean allowRoomConflict) {

        for (Integer day : days) {
            for (Shift shift : shiftList) {
                String dsKey = day + "-" + shift.getId();
                if (usedDS.contains(dsKey))
                    continue;

                String lk = lecturer != null ? lecturer.getId() + "-" + day + "-" + shift.getId() : null;
                if (lk != null && usedLecturerShifts.contains(lk))
                    continue;

                for (Room room : roomList) {
                    String rk = room.getId() + "-" + day + "-" + shift.getId();
                    if (!allowRoomConflict && usedRoomShifts.contains(rk))
                        continue;

                    chromosome.genes.add(new Gene(section.getId(), room.getId(), shift.getId(), day));
                    usedRoomShifts.add(rk);
                    if (lk != null)
                        usedLecturerShifts.add(lk);
                    usedDS.add(dsKey);
                    if (lecturer != null)
                        lecturerAssigned.merge(lecturer.getId(), 1, (a, b) -> a + b);
                    return true;
                }
            }
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fitness function
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fitness (v3): cao hơn = tốt hơn.
     *
     * Penalties:
     * - Room conflict / Lecturer conflict × CONFLICT_PENALTY
     * - OFFLINE/HYBRID/ONLINE_COURSERA vào ca tối × EVENING_OFFLINE_PENALTY
     * - Buổi xếp vào Thứ 7 × SATURDAY_SOFT_PENALTY
     * - GV có >2 buổi trong cùng ngày × DAILY_CLUSTER_PENALTY (mỗi buổi thừa)
     * - GV vượt cap × OVERLOAD_PENALTY
     * - Thiếu buổi so với yêu cầu × MISSING_SESSION_PENALTY
     *
     * Bonus:
     * - Số ngày khác nhau cho cùng section × DISTRIBUTION_BONUS
     */
    private void evaluateFitness(Chromosome chromosome) {
        int conflictCount = 0;
        int eveningViolations = 0;
        int saturdayCount = 0;
        int dailyClusterPenalty = 0;
        int overloadPenalty = 0;
        int missingPenalty = 0;
        int distributionScore = 0;

        Map<String, Integer> roomUsage = new HashMap<>();
        Map<String, Integer> lecturerUsage = new HashMap<>();
        // lecturerId → (day → count)
        Map<Long, Map<Integer, Integer>> lecturerDailyCount = new HashMap<>();
        Map<Long, Integer> lecturerTotal = new HashMap<>();
        Map<Long, Integer> sectionCount = new HashMap<>();

        for (Gene gene : chromosome.genes) {
            ClassSection section = sectionMap.get(gene.sectionId);
            if (section == null)
                continue;

            Course course = section.getCourseOffering().getCourse();
            Lecturer lecturer = section.getLecturer();

            // ── Room conflict ────────────────────────────────────────────────
            String rk = gene.roomId + "-" + gene.dayOfWeek + "-" + gene.shiftId;
            if (blockedRoomShifts.contains(rk))
                conflictCount++;
            roomUsage.merge(rk, 1, (a, b) -> a + b);
            if (roomUsage.get(rk) > 1)
                conflictCount++;

            // ── Lecturer conflict ────────────────────────────────────────────
            if (lecturer != null) {
                String lk = lecturer.getId() + "-" + gene.dayOfWeek + "-" + gene.shiftId;
                if (blockedLecturerShifts.contains(lk))
                    conflictCount++;
                lecturerUsage.merge(lk, 1, (a, b) -> a + b);
                if (lecturerUsage.get(lk) > 1)
                    conflictCount++;

                lecturerTotal.merge(lecturer.getId(), 1, (a, b) -> a + b);

                // Đếm buổi/ngày cho GV
                lecturerDailyCount
                        .computeIfAbsent(lecturer.getId(), k -> new HashMap<>())
                        .merge(gene.dayOfWeek, 1, (a, b) -> a + b);
            }

            // ── OFFLINE vào ca tối ──────────────────────────────────────────
            Shift shift = shiftMap.get(gene.shiftId);
            if (shift != null && isEveningShift(shift) && isOfflineCourse(course)) {
                eveningViolations++;
            }

            // ── Saturday soft penalty ────────────────────────────────────────
            if (gene.dayOfWeek == 7)
                saturdayCount++;

            // ── Section gene count ───────────────────────────────────────────
            sectionCount.merge(gene.sectionId, 1, (a, b) -> a + b);
        }

        // ── GV: daily cluster penalty (>2 buổi/ngày) ────────────────────────
        for (Map<Integer, Integer> dayMap : lecturerDailyCount.values()) {
            for (int cnt : dayMap.values()) {
                if (cnt > 2) {
                    dailyClusterPenalty += (cnt - 2) * DAILY_CLUSTER_PENALTY;
                }
            }
        }

        // ── GV: overload penalty ─────────────────────────────────────────────
        for (Map.Entry<Long, Integer> e : lecturerTotal.entrySet()) {
            int cap = lecturerAvailableSlots.getOrDefault(e.getKey(), Integer.MAX_VALUE);
            if (e.getValue() > cap) {
                overloadPenalty += (e.getValue() - cap) * OVERLOAD_PENALTY;
            }
        }

        // ── Thiếu buổi penalty ───────────────────────────────────────────────
        for (ClassSection section : sections) {
            int required = determineSessionsPerWeek(section, section.getCourseOffering().getCourse());
            int actual = sectionCount.getOrDefault(section.getId(), 0);
            if (actual < required) {
                missingPenalty += (required - actual) * MISSING_SESSION_PENALTY;
            }
        }

        // ── Distribution bonus ───────────────────────────────────────────────
        Map<Long, Set<Integer>> sectionDays = new HashMap<>();
        for (Gene g : chromosome.genes) {
            sectionDays.computeIfAbsent(g.sectionId, k -> new HashSet<>()).add(g.dayOfWeek);
        }
        for (Set<Integer> days : sectionDays.values()) {
            if (days.size() >= 2)
                distributionScore += days.size();
        }

        chromosome.conflicts = conflictCount;
        chromosome.fitness = (double) distributionScore * DISTRIBUTION_BONUS
                - (double) conflictCount * CONFLICT_PENALTY
                - (double) eveningViolations * EVENING_OFFLINE_PENALTY
                - (double) saturdayCount * SATURDAY_SOFT_PENALTY
                - (double) dailyClusterPenalty
                - (double) overloadPenalty
                - (double) missingPenalty;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Evolve: Selection + Crossover + Mutation
    // ─────────────────────────────────────────────────────────────────────────

    private List<Chromosome> evolve(List<Chromosome> population, double mutationRate) {
        population.sort((a, b) -> Double.compare(b.fitness, a.fitness));

        List<Chromosome> next = new ArrayList<>();

        // Elitism
        int eliteCount = (int) (POPULATION_SIZE * ELITISM_RATE);
        for (int i = 0; i < eliteCount; i++) {
            next.add(population.get(i).copy());
        }

        Random rnd = new Random();
        while (next.size() < POPULATION_SIZE) {
            Chromosome p1 = tournamentSelection(population, rnd);
            Chromosome p2 = tournamentSelection(population, rnd);

            Chromosome[] offspring = crossover(p1, p2, rnd);

            if (rnd.nextDouble() < mutationRate)
                mutate(offspring[0], rnd);
            if (rnd.nextDouble() < mutationRate)
                mutate(offspring[1], rnd);

            // 18% chance: swap mutation (hoán đổi day-shift giữa 2 genes)
            if (rnd.nextDouble() < 0.18)
                swapMutate(offspring[0], rnd);
            if (rnd.nextDouble() < 0.18)
                swapMutate(offspring[1], rnd);

            // 8% chance: repair conflicts trên offspring (ưu tiên elite)
            if (rnd.nextDouble() < 0.08 && offspring[0].conflicts > 0)
                repairConflicts(offspring[0], rnd);
            if (rnd.nextDouble() < 0.08 && offspring[1].conflicts > 0)
                repairConflicts(offspring[1], rnd);

            next.add(offspring[0]);
            if (next.size() < POPULATION_SIZE)
                next.add(offspring[1]);
        }

        return next;
    }

    /**
     * Tournament selection: chọn tốt nhất từ TOURNAMENT_SIZE cá thể ngẫu nhiên.
     * Pareto-aware: ưu tiên chromosome ít conflict hơn, nếu bằng nhau thì theo
     * fitness.
     */
    private Chromosome tournamentSelection(List<Chromosome> population, Random rnd) {
        List<Chromosome> tournament = new ArrayList<>();
        for (int i = 0; i < TOURNAMENT_SIZE; i++) {
            tournament.add(population.get(rnd.nextInt(population.size())));
        }
        tournament.sort((a, b) -> {
            // Ưu tiên conflict ít hơn; nếu bằng thì fitness cao hơn
            if (a.conflicts != b.conflicts)
                return Integer.compare(a.conflicts, b.conflicts);
            return Double.compare(b.fitness, a.fitness);
        });
        return tournament.get(0);
    }

    /**
     * Crossover: với mỗi section, lấy genes từ parent tốt hơn (70% bias).
     * Nếu parent có conflicts bằng nhau, chọn theo fitness.
     */
    private Chromosome[] crossover(Chromosome p1, Chromosome p2, Random rnd) {
        if (rnd.nextDouble() > CROSSOVER_RATE) {
            return new Chromosome[] { p1.copy(), p2.copy() };
        }

        Map<Long, List<Gene>> g1 = p1.genes.stream().collect(Collectors.groupingBy(Gene::getSectionId));
        Map<Long, List<Gene>> g2 = p2.genes.stream().collect(Collectors.groupingBy(Gene::getSectionId));

        Set<Long> allIds = new HashSet<>(g1.keySet());
        allIds.addAll(g2.keySet());

        Chromosome c1 = new Chromosome();
        Chromosome c2 = new Chromosome();

        boolean p1Better = (p1.conflicts < p2.conflicts)
                || (p1.conflicts == p2.conflicts && p1.fitness >= p2.fitness);

        for (Long sid : allIds) {
            List<Gene> genes1 = g1.getOrDefault(sid, Collections.emptyList());
            List<Gene> genes2 = g2.getOrDefault(sid, Collections.emptyList());

            List<Gene> chosen1, chosen2;
            if (rnd.nextDouble() < 0.70) {
                chosen1 = p1Better ? genes1 : genes2;
            } else {
                chosen1 = p1Better ? genes2 : genes1;
            }
            chosen2 = (chosen1 == genes1 || genes1.isEmpty()) ? genes2 : genes1;
            if (chosen2.isEmpty())
                chosen2 = chosen1;

            for (Gene g : chosen1)
                c1.genes.add(g.copy());
            for (Gene g : chosen2)
                c2.genes.add(g.copy());
        }

        return new Chromosome[] { c1, c2 };
    }

    /**
     * Conflict-directed mutation (v3):
     * Ưu tiên đột biến gene conflict → tìm slot tự do trong ALL_DAYS.
     * Ưu tiên WEEKDAYS, tránh Thứ 7 nếu có thể (Saturday soft penalty).
     */
    private void mutate(Chromosome chromosome, Random rnd) {
        if (chromosome.genes.isEmpty())
            return;

        // Bước 1: Tìm gene đang conflict
        Map<String, List<Integer>> roomMap = new HashMap<>();
        Map<String, List<Integer>> lecturerMap = new HashMap<>();
        for (int i = 0; i < chromosome.genes.size(); i++) {
            Gene g = chromosome.genes.get(i);
            String rk = g.roomId + "-" + g.dayOfWeek + "-" + g.shiftId;
            roomMap.computeIfAbsent(rk, k -> new ArrayList<>()).add(i);

            ClassSection sec = sectionMap.get(g.sectionId);
            if (sec != null && sec.getLecturer() != null) {
                String lk = sec.getLecturer().getId() + "-" + g.dayOfWeek + "-" + g.shiftId;
                lecturerMap.computeIfAbsent(lk, k -> new ArrayList<>()).add(i);
            }
        }

        Set<Integer> conflictIdx = new HashSet<>();
        for (List<Integer> idxList : roomMap.values()) {
            if (idxList.size() > 1)
                conflictIdx.addAll(idxList.subList(1, idxList.size()));
        }
        for (List<Integer> idxList : lecturerMap.values()) {
            if (idxList.size() > 1)
                conflictIdx.addAll(idxList.subList(1, idxList.size()));
        }

        List<Integer> targets = new ArrayList<>(conflictIdx);
        int extra = Math.max(1, chromosome.genes.size() / 15);
        for (int i = 0; i < extra; i++) {
            targets.add(rnd.nextInt(chromosome.genes.size()));
        }

        // Bước 2: Tracking slot hiện tại để tránh đột biến vào slot đang bị dùng
        Set<String> currRoomSlots = chromosome.genes.stream()
                .map(g -> g.roomId + "-" + g.dayOfWeek + "-" + g.shiftId)
                .collect(Collectors.toCollection(HashSet::new));
        Set<String> currLecSlots = new HashSet<>();
        for (Gene g : chromosome.genes) {
            ClassSection sec = sectionMap.get(g.sectionId);
            if (sec != null && sec.getLecturer() != null) {
                currLecSlots.add(sec.getLecturer().getId() + "-" + g.dayOfWeek + "-" + g.shiftId);
            }
        }

        // Tạo thứ tự tìm: T2-T6 trước, T7 sau (để ưu tiên weekday)
        List<Integer> searchOrder = new ArrayList<>(WEEKDAYS);
        searchOrder.add(7);

        for (int geneIdx : targets) {
            if (geneIdx >= chromosome.genes.size())
                continue;
            Gene gene = chromosome.genes.get(geneIdx);

            ClassSection section = sectionMap.get(gene.sectionId);
            if (section == null)
                continue;

            Course course = section.getCourseOffering().getCourse();
            Lecturer lec = section.getLecturer();

            List<Room> suitableRooms = getSuitableRooms(determineRequiredRoomType(section, course));
            List<Shift> allowedShifts = getAllowedShifts(course);
            if (suitableRooms.isEmpty() || allowedShifts.isEmpty())
                continue;

            // Xóa slot cũ khỏi tracking
            String oldRk = gene.roomId + "-" + gene.dayOfWeek + "-" + gene.shiftId;
            currRoomSlots.remove(oldRk);
            String oldLk = lec != null ? lec.getId() + "-" + gene.dayOfWeek + "-" + gene.shiftId : null;
            if (oldLk != null)
                currLecSlots.remove(oldLk);

            List<Integer> shuffDays = new ArrayList<>(searchOrder);
            Collections.shuffle(shuffDays.subList(0, WEEKDAYS.size()), rnd); // shuffle chỉ T2-T6
            List<Shift> shuffShifts = new ArrayList<>(allowedShifts);
            Collections.shuffle(shuffShifts, rnd);
            List<Room> shuffRooms = new ArrayList<>(suitableRooms);
            Collections.shuffle(shuffRooms, rnd);

            boolean mutated = false;
            outer2: for (Integer day : shuffDays) {
                for (Shift shift : shuffShifts) {
                    String newLk = lec != null ? lec.getId() + "-" + day + "-" + shift.getId() : null;
                    if (newLk != null && (blockedLecturerShifts.contains(newLk)
                            || currLecSlots.contains(newLk)))
                        continue;

                    // Không xếp 2 buổi cùng section trùng ngày-ca
                    final int finalDay = day;
                    final long finalShiftId = shift.getId();
                    boolean sectionDsOk = chromosome.genes.stream()
                            .filter(g -> g != gene && g.sectionId.equals(gene.sectionId))
                            .noneMatch(g -> g.dayOfWeek.equals(finalDay) && g.shiftId.equals(finalShiftId));
                    if (!sectionDsOk)
                        continue;

                    for (Room room : shuffRooms) {
                        String newRk = room.getId() + "-" + day + "-" + shift.getId();
                        if (blockedRoomShifts.contains(newRk) || currRoomSlots.contains(newRk))
                            continue;

                        gene.roomId = room.getId();
                        gene.shiftId = shift.getId();
                        gene.dayOfWeek = day;
                        currRoomSlots.add(newRk);
                        if (newLk != null)
                            currLecSlots.add(newLk);
                        mutated = true;
                        break outer2;
                    }
                }
            }

            // Fallback: chọn ngẫu nhiên trong ALL_DAYS, tránh blocked
            if (!mutated) {
                for (int attempt = 0; attempt < 40; attempt++) {
                    int day = shuffDays.get(rnd.nextInt(shuffDays.size()));
                    Shift shift = shuffShifts.get(rnd.nextInt(shuffShifts.size()));
                    Room room = shuffRooms.get(rnd.nextInt(shuffRooms.size()));
                    String rk = room.getId() + "-" + day + "-" + shift.getId();
                    String lk2 = lec != null ? lec.getId() + "-" + day + "-" + shift.getId() : null;
                    if (blockedRoomShifts.contains(rk))
                        continue;
                    if (lk2 != null && blockedLecturerShifts.contains(lk2))
                        continue;
                    gene.roomId = room.getId();
                    gene.shiftId = shift.getId();
                    gene.dayOfWeek = day;
                    currRoomSlots.add(rk);
                    if (lk2 != null)
                        currLecSlots.add(lk2);
                    break;
                }
            }
        }
    }

    /**
     * Sửa conflict: đưa các gene conflict sang slot tự do. Duyệt theo thứ tự ưu tiên
     * (WEEKDAYS trước) để tìm slot không trùng phòng/GV.
     */
    private void repairConflicts(Chromosome chromosome, Random rnd) {
        if (chromosome.genes.isEmpty())
            return;
        evaluateFitness(chromosome);
        Map<String, List<Integer>> roomIdx = new HashMap<>();
        Map<String, List<Integer>> lecIdx = new HashMap<>();
        for (int i = 0; i < chromosome.genes.size(); i++) {
            Gene g = chromosome.genes.get(i);
            roomIdx.computeIfAbsent(g.roomId + "-" + g.dayOfWeek + "-" + g.shiftId, k -> new ArrayList<>()).add(i);
            ClassSection sec = sectionMap.get(g.sectionId);
            if (sec != null && sec.getLecturer() != null) {
                lecIdx.computeIfAbsent(sec.getLecturer().getId() + "-" + g.dayOfWeek + "-" + g.shiftId, k -> new ArrayList<>()).add(i);
            }
        }
        Set<Integer> conflictIdx = new HashSet<>();
        for (List<Integer> L : roomIdx.values()) {
            for (int j = 1; j < L.size(); j++) conflictIdx.add(L.get(j));
        }
        for (List<Integer> L : lecIdx.values()) {
            for (int j = 1; j < L.size(); j++) conflictIdx.add(L.get(j));
        }
        Set<String> currRoom = chromosome.genes.stream()
                .map(g -> g.roomId + "-" + g.dayOfWeek + "-" + g.shiftId)
                .collect(Collectors.toSet());
        Set<String> currLec = new HashSet<>();
        for (Gene g : chromosome.genes) {
            ClassSection sec = sectionMap.get(g.sectionId);
            if (sec != null && sec.getLecturer() != null)
                currLec.add(sec.getLecturer().getId() + "-" + g.dayOfWeek + "-" + g.shiftId);
        }
        List<Integer> days = new ArrayList<>(WEEKDAYS);
        days.add(7);
        for (int idx : conflictIdx) {
            if (idx >= chromosome.genes.size()) continue;
            Gene gene = chromosome.genes.get(idx);
            ClassSection section = sectionMap.get(gene.sectionId);
            if (section == null) continue;
            Course course = section.getCourseOffering().getCourse();
            Lecturer lec = section.getLecturer();
            List<Room> rooms = getSuitableRooms(determineRequiredRoomType(section, course));
            List<Shift> shifts = getAllowedShifts(course);
            if (rooms.isEmpty() || shifts.isEmpty()) continue;
            Collections.shuffle(days, rnd);
            Collections.shuffle(shifts, rnd);
            Collections.shuffle(rooms, rnd);
            boolean fixed = false;
            outer: for (Integer d : days) {
                for (Shift sh : shifts) {
                    final int fd = d;
                    final long fsid = sh.getId();
                    if (chromosome.genes.stream().anyMatch(g -> g != gene && g.sectionId.equals(gene.sectionId) && g.dayOfWeek == fd && g.shiftId.equals(fsid)))
                        continue;
                    String lk = lec != null ? lec.getId() + "-" + d + "-" + sh.getId() : null;
                    if (lk != null && (blockedLecturerShifts.contains(lk) || currLec.contains(lk)))
                        continue;
                    for (Room room : rooms) {
                        String rk = room.getId() + "-" + d + "-" + sh.getId();
                        if (blockedRoomShifts.contains(rk) || currRoom.contains(rk)) continue;
                        gene.roomId = room.getId();
                        gene.shiftId = sh.getId();
                        gene.dayOfWeek = d;
                        currRoom.add(rk);
                        if (lk != null) currLec.add(lk);
                        fixed = true;
                        break outer;
                    }
                }
            }
            if (fixed) {
                currRoom.add(gene.roomId + "-" + gene.dayOfWeek + "-" + gene.shiftId);
                if (lec != null) currLec.add(lec.getId() + "-" + gene.dayOfWeek + "-" + gene.shiftId);
            }
        }
    }

    /**
     * Swap mutation: hoán đổi (dayOfWeek, shiftId) giữa 2 gene ngẫu nhiên
     * trong chromosome — tăng đa dạng mà không phá cấu trúc phòng-section.
     */
    private void swapMutate(Chromosome chromosome, Random rnd) {
        if (chromosome.genes.size() < 2)
            return;
        int i = rnd.nextInt(chromosome.genes.size());
        int j = rnd.nextInt(chromosome.genes.size());
        if (i == j)
            return;
        Gene gi = chromosome.genes.get(i);
        Gene gj = chromosome.genes.get(j);
        Long tmpShift = gi.shiftId;
        gi.shiftId = gj.shiftId;
        gj.shiftId = tmpShift;
        Integer tmpDay = gi.dayOfWeek;
        gi.dayOfWeek = gj.dayOfWeek;
        gj.dayOfWeek = tmpDay;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper methods
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tính số slot thực tế khả dụng cho mỗi GV (6 ngày: T2–T7, trừ CONFIRMED).
     */
    private Map<Long, Integer> computeLecturerAvailableSlots() {
        int totalSlots = shifts.size() * 6; // T2–T7 = 6 ngày
        Map<Long, Integer> result = new HashMap<>();
        Set<Long> ids = sections.stream()
                .filter(s -> s.getLecturer() != null)
                .map(s -> s.getLecturer().getId())
                .collect(Collectors.toSet());
        for (Long lid : ids) {
            int blocked = 0;
            for (int day = 2; day <= 7; day++) {
                for (Shift shift : shifts) {
                    if (blockedLecturerShifts.contains(lid + "-" + day + "-" + shift.getId()))
                        blocked++;
                }
            }
            result.put(lid, totalSlots - blocked);
        }
        return result;
    }

    /**
     * Tỉ lệ lấp đầy = required / available — dùng để ưu tiên xếp section khó trước
     */
    private double fillRatio(ClassSection section, Map<Long, Integer> required) {
        if (section.getLecturer() == null)
            return 0;
        Long lid = section.getLecturer().getId();
        int avail = lecturerAvailableSlots.getOrDefault(lid, 1);
        int req = required.getOrDefault(lid, 0);
        return avail > 0 ? (double) req / avail : 0;
    }

    /**
     * Danh sách ca được phép: OFFLINE/HYBRID/ONLINE_COURSERA không được xếp vào ca
     * tối
     */
    private List<Shift> getAllowedShifts(Course course) {
        if (!isOfflineCourse(course))
            return new ArrayList<>(shifts);
        return shifts.stream()
                .filter(s -> !isEveningShift(s))
                .collect(Collectors.toList());
    }

    /**
     * Phòng phù hợp: LT dùng LT/ONLINE; TH dùng tất cả loại TH (PM, TN, SB, XT, BV, DN)
     * để tăng cơ hội tìm slot khi phòng PM khan hiếm.
     */
    private List<Room> getSuitableRooms(String requiredType) {
        if (VALID_TH_ROOM_TYPES.contains(requiredType)) {
            // TH: ưu tiên loại yêu cầu, rồi các loại TH khác
            List<Room> preferred = rooms.stream().filter(r -> requiredType.equals(r.getType())).toList();
            List<Room> otherTh = rooms.stream()
                    .filter(r -> VALID_TH_ROOM_TYPES.contains(r.getType()) && !requiredType.equals(r.getType()))
                    .toList();
            List<Room> result = new ArrayList<>(preferred);
            result.addAll(otherTh);
            return result.isEmpty() ? new ArrayList<>(rooms) : result;
        }
        List<Room> filtered = rooms.stream()
                .filter(r -> requiredType.equals(r.getType()))
                .collect(Collectors.toList());
        return filtered.isEmpty() ? new ArrayList<>(rooms) : filtered;
    }

    private boolean isEveningShift(Shift shift) {
        return (shift.getStartPeriod() != null && shift.getStartPeriod() >= 10)
                || (shift.getName() != null && shift.getName().toLowerCase().contains("tối"));
    }

    private boolean isOfflineCourse(Course course) {
        return course.getLearningMethod() == Course.LearningMethod.OFFLINE
                || course.getLearningMethod() == Course.LearningMethod.HYBRID
                || course.getLearningMethod() == Course.LearningMethod.ONLINE_COURSERA;
    }

    public TimeSlot getFirstTimeSlotOfShift(Shift shift) {
        if (shift.getStartPeriod() == null)
            return null;
        return timeSlots.stream()
                .filter(ts -> ts.getPeriodIndex().equals(shift.getStartPeriod()))
                .findFirst().orElse(null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sessions per week
    // ─────────────────────────────────────────────────────────────────────────

    private int determineSessionsPerWeek(ClassSection section, Course course) {
        return calcSessionsPerWeek(section, course);
    }

    /** Public static: TimetableService dùng cho pre-flight check */
    public static int calcSessionsPerWeek(ClassSection section) {
        return calcSessionsPerWeek(section, section.getCourseOffering().getCourse());
    }

    private static int calcSessionsPerWeek(ClassSection section, Course course) {
        if (section.getSectionType() == ClassSection.SectionType.TH) {
            return (course.getPracticeCredits() != null && course.getPracticeCredits() >= 3.0) ? 2 : 1;
        } else {
            return (course.getTheoryCredits() != null && course.getTheoryCredits() >= 4.0) ? 2 : 1;
        }
    }

    /**
     * Các loại phòng/địa điểm dành cho phần thực hành (TH).
     * Không bao gồm LT và ONLINE vì những loại đó dùng cho lý thuyết/trực tuyến.
     */
    private static final Set<String> VALID_TH_ROOM_TYPES = Set.of("PM", "TN", "SB", "XT", "BV", "DN", "ONLINE");

    /**
     * Xác định loại phòng/địa điểm cần thiết cho section.
     *
     * TH: lấy requiredRoomType từ course nếu hợp lệ, fallback về PM.
     * LT + ONLINE_ELEARNING: nếu course yêu cầu ONLINE thì dùng phòng ảo (100%
     * online).
     * LT + OFFLINE/HYBRID/ONLINE_COURSERA: luôn dùng LT (Coursera = hybrid cần
     * phòng offline).
     */
    private String determineRequiredRoomType(ClassSection section, Course course) {
        String rt = course.getRequiredRoomType();
        if (section.getSectionType() == ClassSection.SectionType.TH) {
            return (rt != null && VALID_TH_ROOM_TYPES.contains(rt)) ? rt : "PM";
        }
        // Phần lý thuyết: chỉ E-learning 100% online mới dùng phòng ảo
        boolean isOnlineOnly = course.getLearningMethod() == Course.LearningMethod.ONLINE_ELEARNING;
        if (isOnlineOnly && "ONLINE".equals(rt)) {
            return "ONLINE";
        }
        return "LT";
    }

    private int getTotalRequiredSessions() {
        int total = 0;
        for (ClassSection section : sections) {
            total += determineSessionsPerWeek(section, section.getCourseOffering().getCourse());
        }
        return total;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Result
    // ─────────────────────────────────────────────────────────────────────────

    public static class GeneticResult {
        public final Chromosome bestChromosome;
        public final double bestFitness;

        public GeneticResult(Chromosome bestChromosome, double bestFitness) {
            this.bestChromosome = bestChromosome;
            this.bestFitness = bestFitness;
        }
    }
}
