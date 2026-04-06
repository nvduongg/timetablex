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
 * CẢI TIẾN v4:
 * - Smart swap mutation: chỉ swap (day, shift) khi slot mới không gây conflict.
 * - Conflict-aware crossover: chọn genes từ parent sao cho ít conflict với child đang xây.
 * - Room load balancing: penalty khi phòng bị dùng quá nhiều so với trung bình.
 * - Local search: hill climbing trên elite để tinh chỉnh phòng/thời gian.
 */
public class GeneticTimetableScheduler {

    // ─────────────────────────────────────────────────────────────────────────
    // Fields
    // ─────────────────────────────────────────────────────────────────────────

    private final List<ClassSection> sections;
    private final List<Room> rooms;
    private final List<Shift> shifts;
    private final List<TimeSlot> timeSlots;
    private final Map<Integer, TimeSlot> periodToSlot;
    private final TimetableRegulationConfig regConfig;
    private final int eveningShiftStartPeriodFrom;
    /** Chuỗi con trong tên ca (vd. "Ca 5") để nhận diện ca tối bổ sung cho tiết. */
    private final List<String> eveningShiftNameMarkers;
    private final Map<Long, Room> roomById;
    private final long maxRuntimeMs;
    private final int maxGenerations;

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

    private static final int POPULATION_SIZE = 220;
    private static final int STAGNANT_LIMIT = 50; // Dừng sớm nếu không cải thiện
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
    private static final int EVENING_OFFLINE_PENALTY = 4000;
    /** Ca tối mà phòng không phải ONLINE (học trực tiếp ở ca tối). */
    private static final int EVENING_NON_ONLINE_ROOM_PENALTY = 5000;
    /** Phòng không khớp loại yêu cầu (PM/LT/…). */
    private static final int ROOM_TYPE_MISMATCH_PENALTY = 1500;
    private static final int SATURDAY_SOFT_PENALTY = 40; // nhẹ: chỉ tránh Thứ 7 khi có thể
    private static final int DAILY_CLUSTER_PENALTY = 100; // GV có >2 buổi/ngày
    private static final int OVERLOAD_PENALTY = 300;
    private static final int MISSING_SESSION_PENALTY = 800; // thiếu buổi so với yêu cầu
    private static final int DISTRIBUTION_BONUS = 15; // phân bố đều ngày trong tuần
    private static final int ROOM_IMBALANCE_PENALTY = 8; // phòng dùng quá nhiều so với trung bình

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
            Set<String> blockedLecturerShifts,
            int eveningShiftStartPeriodFrom,
            List<String> eveningShiftNameMarkers,
            long maxRuntimeMs,
            int maxGenerations,
            TimetableRegulationConfig regConfig) {

        this.sections = sections;
        this.rooms = rooms;
        this.shifts = shifts;
        this.timeSlots = timeSlots;
        this.regConfig = regConfig != null ? regConfig : TimetableRegulationConfig.defaults();
        this.periodToSlot = TimetableRegulationHelper.toPeriodMap(timeSlots);
        this.blockedRoomShifts = blockedRoomShifts != null ? blockedRoomShifts : Collections.emptySet();
        this.blockedLecturerShifts = blockedLecturerShifts != null ? blockedLecturerShifts : Collections.emptySet();
        this.progressCallback = null;
        this.eveningShiftStartPeriodFrom = eveningShiftStartPeriodFrom;
        this.eveningShiftNameMarkers = eveningShiftNameMarkers != null ? eveningShiftNameMarkers : List.of();
        this.roomById = rooms.stream().collect(Collectors.toMap(Room::getId, r -> r));
        this.maxRuntimeMs = maxRuntimeMs > 0 ? maxRuntimeMs : 240_000L;
        this.maxGenerations = maxGenerations > 0 ? maxGenerations : 500;

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
        List<Gene> genes;  // package-private để SA có thể truy cập
        double fitness;
        int conflicts;

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
        long startTime = System.currentTimeMillis();
        List<Chromosome> population = initializePopulation();

        Chromosome bestChromosome = null;
        double bestFitness = Double.NEGATIVE_INFINITY;
        int stagnantGenerations = 0;
        int totalRequired = getTotalRequiredSessions();

        for (int gen = 0; gen < maxGenerations; gen++) {
            if (System.currentTimeMillis() - startTime > maxRuntimeMs) {
                break;
            }
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
                progressCallback.onProgress(gen + 1, maxGenerations,
                        bestChromosome.fitness, bestChromosome.conflicts);
            }

            if (bestChromosome != null
                    && bestChromosome.conflicts == 0
                    && bestChromosome.genes.size() >= totalRequired) {
                if (progressCallback != null) {
                    progressCallback.onProgress(maxGenerations, maxGenerations,
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

            // Mỗi 120 thế hệ: local search (giảm tần suất để chạy nhanh hơn)
            if ((gen + 1) % 120 == 0 && bestChromosome != null) {
                Chromosome bestInPop = population.stream()
                        .max((a, b) -> Double.compare(a.fitness, b.fitness))
                        .orElse(null);
                if (bestInPop != null) {
                    localSearchImprove(bestInPop, new Random());
                    evaluateFitness(bestInPop);
                }
            }
        }

        if (progressCallback != null && bestChromosome != null) {
            progressCallback.onProgress(maxGenerations, maxGenerations,
                    bestChromosome.fitness, bestChromosome.conflicts);
        }
        if (bestChromosome != null) {
            evaluateFitness(bestChromosome);
            Random rnd = new Random();
            for (int rep = 0; rep < 3 && bestChromosome.conflicts > 0; rep++)
                repairConflicts(bestChromosome, rnd);
            evaluateFitness(bestChromosome);
            localSearchImprove(bestChromosome, rnd);
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
            List<Shift> allowedShifts = getAllowedShifts(course);

            Set<String> usedDayShifts = new HashSet<>();
            List<Integer> days = new ArrayList<>(ALL_DAYS);

            for (int s = 0; s < sessionsPerWeek; s++) {
                Collections.shuffle(days, rnd);
                Collections.shuffle(allowedShifts, rnd);

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

                        List<Room> forShift = roomsForSectionAndShift(section, course, shift);
                        List<Room> shuffledRooms = new ArrayList<>(forShift);
                        Collections.shuffle(shuffledRooms, rnd);
                        for (Room room : shuffledRooms) {
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
                        List<Room> forShift = roomsForSectionAndShift(section, course, shift);
                        if (forShift.isEmpty())
                            continue;
                        Room room = forShift.get(rnd.nextInt(forShift.size()));
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
            lecturerRequired.merge(s.getLecturer().getId(),
                    determineSessionsPerWeek(s, s.getCourseOffering().getCourse()), (a, b) -> a + b);
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

            List<Shift> allowedShifts = getAllowedShifts(course);
            Set<String> usedDS = new HashSet<>();

            for (int s = 0; s < canAssign; s++) {
                List<Integer> shuffledPrimary = new ArrayList<>(WEEKDAYS);
                Collections.shuffle(shuffledPrimary, rnd);
                List<Shift> shuffledShifts = new ArrayList<>(allowedShifts);
                Collections.shuffle(shuffledShifts, rnd);

                List<Integer> searchOrder = new ArrayList<>(shuffledPrimary);
                searchOrder.add(7);

                boolean ok = tryAssign(chromosome, section, lecturer,
                        searchOrder, shuffledShifts, rnd,
                        usedRoomShifts, usedLecturerShifts, usedDS, lecturerAssigned, false);

                if (!ok) {
                    ok = tryAssign(chromosome, section, lecturer,
                            searchOrder, shuffledShifts, rnd,
                            usedRoomShifts, usedLecturerShifts, usedDS, lecturerAssigned, true);
                }

                if (!ok) {
                    int day = searchOrder.get(rnd.nextInt(searchOrder.size()));
                    Shift shift = shuffledShifts.get(rnd.nextInt(shuffledShifts.size()));
                    String dsKey = day + "-" + shift.getId();
                    if (!usedDS.contains(dsKey)) {
                        String lk = lecturer != null ? lecturer.getId() + "-" + day + "-" + shift.getId() : null;
                        if (lk == null || !usedLecturerShifts.contains(lk)) {
                            List<Room> forShift = roomsForSectionAndShift(section, course, shift);
                            if (!forShift.isEmpty()) {
                                Room room = forShift.get(rnd.nextInt(forShift.size()));
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
        }

        return chromosome;
    }

    /**
     * Thử gán một session cho section.
     * allowRoomConflict=true: bỏ qua room conflict nhưng vẫn tôn trọng lecturer.
     */
    private boolean tryAssign(
            Chromosome chromosome, ClassSection section, Lecturer lecturer,
            List<Integer> days, List<Shift> shiftList, Random rnd,
            Set<String> usedRoomShifts, Set<String> usedLecturerShifts,
            Set<String> usedDS, Map<Long, Integer> lecturerAssigned,
            boolean allowRoomConflict) {

        Course course = section.getCourseOffering().getCourse();
        for (Integer day : days) {
            for (Shift shift : shiftList) {
                String dsKey = day + "-" + shift.getId();
                if (usedDS.contains(dsKey))
                    continue;

                String lk = lecturer != null ? lecturer.getId() + "-" + day + "-" + shift.getId() : null;
                if (lk != null && usedLecturerShifts.contains(lk))
                    continue;

                List<Room> roomList = new ArrayList<>(roomsForSectionAndShift(section, course, shift));
                Collections.shuffle(roomList, rnd);
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
        int eveningPhysicalRoomViolations = 0;
        int roomTypeMismatch = 0;
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

            // ── Ca tối: không xếp học trực tiếp; ca tối chỉ phòng ONLINE ───────
            Shift shift = shiftMap.get(gene.shiftId);
            Room room = roomById.get(gene.roomId);
            if (shift != null && isEveningShift(shift) && !isEveningAllowedForCourse(course)) {
                eveningViolations++;
            }
            if (shift != null && room != null && isEveningShift(shift)
                    && !"ONLINE".equalsIgnoreCase(room.getType())) {
                eveningPhysicalRoomViolations++;
            }
            if (shift != null && room != null && !isEveningShift(shift)) {
                String req = TimetableRegulationHelper.determineRequiredRoomType(section, course);
                if (!req.equalsIgnoreCase(room.getType()))
                    roomTypeMismatch++;
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

        TimetableRegulationHelper.RegulationPenaltyBreakdown reg = TimetableRegulationHelper.computeRegulationPenalties(
                chromosome.genes, sectionMap, shiftMap, periodToSlot, regConfig);
        int regulationPenalty = reg.penaltyLecturerWeek() + reg.penaltyLecturerDay()
                + reg.penaltyStudentWeek() + reg.penaltyStudentDay();
        conflictCount += reg.outsideWindowViolations();

        // ── Distribution bonus ───────────────────────────────────────────────
        Map<Long, Set<Integer>> sectionDays = new HashMap<>();
        for (Gene g : chromosome.genes) {
            sectionDays.computeIfAbsent(g.sectionId, k -> new HashSet<>()).add(g.dayOfWeek);
        }
        for (Set<Integer> days : sectionDays.values()) {
            if (days.size() >= 2)
                distributionScore += days.size();
        }

        // ── Room load imbalance ───────────────────────────────────────────────
        Map<Long, Integer> roomUsageCount = new HashMap<>();
        for (Gene g : chromosome.genes) {
            roomUsageCount.merge(g.roomId, 1, (a, b) -> a + b);
        }
        int roomImbalance = 0;
        if (!roomUsageCount.isEmpty()) {
            double avg = (double) chromosome.genes.size() / roomUsageCount.size();
            for (int cnt : roomUsageCount.values()) {
                if (cnt > avg + 1.5)
                    roomImbalance += (int) (cnt - avg);
            }
        }

        chromosome.conflicts = conflictCount;
        chromosome.fitness = (double) distributionScore * DISTRIBUTION_BONUS
                - (double) conflictCount * CONFLICT_PENALTY
                - (double) eveningViolations * EVENING_OFFLINE_PENALTY
                - (double) eveningPhysicalRoomViolations * EVENING_NON_ONLINE_ROOM_PENALTY
                - (double) roomTypeMismatch * ROOM_TYPE_MISMATCH_PENALTY
                - (double) saturdayCount * SATURDAY_SOFT_PENALTY
                - (double) dailyClusterPenalty
                - (double) overloadPenalty
                - (double) missingPenalty
                - (double) roomImbalance * ROOM_IMBALANCE_PENALTY
                - (double) regulationPenalty;
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

            // Đánh giá fitness để biết conflicts
            evaluateFitness(offspring[0]);
            evaluateFitness(offspring[1]);

            // Repair: chắc chắn khi conflict >= 3, hoặc 18% chance khi có conflict
            if (offspring[0].conflicts > 0 && (offspring[0].conflicts >= 3 || rnd.nextDouble() < 0.18))
                repairConflicts(offspring[0], rnd);
            if (offspring[1].conflicts > 0 && (offspring[1].conflicts >= 3 || rnd.nextDouble() < 0.18))
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
     * Conflict-aware crossover: với mỗi section, chọn genes từ parent có ít conflict
     * hơn với child đang xây. Bias 70% cho parent tốt hơn khi số conflict ngang nhau.
     */
    private Chromosome[] crossover(Chromosome p1, Chromosome p2, Random rnd) {
        if (rnd.nextDouble() > CROSSOVER_RATE) {
            return new Chromosome[] { p1.copy(), p2.copy() };
        }

        Map<Long, List<Gene>> g1 = p1.genes.stream().collect(Collectors.groupingBy(Gene::getSectionId));
        Map<Long, List<Gene>> g2 = p2.genes.stream().collect(Collectors.groupingBy(Gene::getSectionId));

        List<Long> allIds = new ArrayList<>(g1.keySet());
        for (Long id : g2.keySet())
            if (!allIds.contains(id)) allIds.add(id);

        Chromosome c1 = new Chromosome();
        Chromosome c2 = new Chromosome();

        boolean p1Better = (p1.conflicts < p2.conflicts)
                || (p1.conflicts == p2.conflicts && p1.fitness >= p2.fitness);

        for (Long sid : allIds) {
            List<Gene> genes1 = g1.getOrDefault(sid, Collections.emptyList());
            List<Gene> genes2 = g2.getOrDefault(sid, Collections.emptyList());

            int conflicts1WithC1 = countConflictsWithChild(genes1, c1.genes);
            int conflicts2WithC1 = countConflictsWithChild(genes2, c1.genes);
            int conflicts1WithC2 = countConflictsWithChild(genes1, c2.genes);
            int conflicts2WithC2 = countConflictsWithChild(genes2, c2.genes);

            List<Gene> chosen1 = conflicts1WithC1 <= conflicts2WithC1 ? genes1 : genes2;
            if (conflicts1WithC1 == conflicts2WithC1 && rnd.nextDouble() < 0.70)
                chosen1 = p1Better ? genes1 : genes2;

            List<Gene> chosen2 = conflicts2WithC2 <= conflicts1WithC2 ? genes2 : genes1;
            if (conflicts1WithC2 == conflicts2WithC2 && rnd.nextDouble() < 0.70)
                chosen2 = p1Better ? genes1 : genes2;

            if (chosen1.isEmpty()) chosen1 = genes2.isEmpty() ? genes1 : genes2;
            if (chosen2.isEmpty()) chosen2 = genes1.isEmpty() ? genes2 : genes1;

            for (Gene g : chosen1)
                c1.genes.add(g.copy());
            for (Gene g : chosen2)
                c2.genes.add(g.copy());
        }

        return new Chromosome[] { c1, c2 };
    }

    /** Đếm số conflict khi thêm genes mới vào child (so với các gene đã có). */
    private int countConflictsWithChild(List<Gene> newGenes, List<Gene> existingGenes) {
        Set<String> roomSlots = existingGenes.stream()
                .map(g -> g.roomId + "-" + g.dayOfWeek + "-" + g.shiftId)
                .collect(Collectors.toSet());
        Set<String> lecturerSlots = new HashSet<>();
        for (Gene g : existingGenes) {
            ClassSection sec = sectionMap.get(g.sectionId);
            if (sec != null && sec.getLecturer() != null)
                lecturerSlots.add(sec.getLecturer().getId() + "-" + g.dayOfWeek + "-" + g.shiftId);
        }
        int conflicts = 0;
        for (Gene g : newGenes) {
            String rk = g.roomId + "-" + g.dayOfWeek + "-" + g.shiftId;
            if (blockedRoomShifts.contains(rk) || roomSlots.contains(rk)) conflicts++;
            ClassSection sec = sectionMap.get(g.sectionId);
            if (sec != null && sec.getLecturer() != null) {
                String lk = sec.getLecturer().getId() + "-" + g.dayOfWeek + "-" + g.shiftId;
                if (blockedLecturerShifts.contains(lk) || lecturerSlots.contains(lk)) conflicts++;
            }
        }
        return conflicts;
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

            List<Shift> allowedShifts = getAllowedShifts(course);
            if (allowedShifts.isEmpty())
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

                    List<Room> shuffRooms = new ArrayList<>(roomsForSectionAndShift(section, course, shift));
                    Collections.shuffle(shuffRooms, rnd);
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

            if (!mutated) {
                for (int attempt = 0; attempt < 40; attempt++) {
                    int day = shuffDays.get(rnd.nextInt(shuffDays.size()));
                    Shift shift = shuffShifts.get(rnd.nextInt(shuffShifts.size()));
                    List<Room> forShift = roomsForSectionAndShift(section, course, shift);
                    if (forShift.isEmpty())
                        continue;
                    Room room = forShift.get(rnd.nextInt(forShift.size()));
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
            List<Shift> shifts = getAllowedShifts(course);
            if (shifts.isEmpty()) continue;
            Collections.shuffle(days, rnd);
            Collections.shuffle(shifts, rnd);
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
                    List<Room> rooms = new ArrayList<>(roomsForSectionAndShift(section, course, sh));
                    Collections.shuffle(rooms, rnd);
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
     * Local search (hill climbing): thử di chuyển gene sang slot khác; nếu fitness
     * tốt hơn thì giữ. Giới hạn nhẹ để tránh chạy quá lâu.
     */
    private void localSearchImprove(Chromosome chromosome, Random rnd) {
        if (chromosome.genes.isEmpty())
            return;

        List<Integer> days = new ArrayList<>(ALL_DAYS);
        int maxAttempts = Math.min(40, chromosome.genes.size() * 3);
        int improved = 0;

        for (int attempt = 0; attempt < maxAttempts && improved < 8; attempt++) {
            int idx = rnd.nextInt(chromosome.genes.size());
            Gene gene = chromosome.genes.get(idx);
            ClassSection section = sectionMap.get(gene.sectionId);
            if (section == null)
                continue;

            Course course = section.getCourseOffering().getCourse();
            Lecturer lec = section.getLecturer();
            List<Shift> shifts = getAllowedShifts(course);
            if (shifts.isEmpty())
                continue;

            // Build current usage excluding this gene
            Set<String> usedRoom = new HashSet<>();
            Set<String> usedLec = new HashSet<>();
            Set<String> usedDS = new HashSet<>();
            for (int k = 0; k < chromosome.genes.size(); k++) {
                if (k == idx) continue;
                Gene g = chromosome.genes.get(k);
                usedRoom.add(g.roomId + "-" + g.dayOfWeek + "-" + g.shiftId);
                usedDS.add(g.sectionId + "-" + g.dayOfWeek + "-" + g.shiftId);
                ClassSection s = sectionMap.get(g.sectionId);
                if (s != null && s.getLecturer() != null)
                    usedLec.add(s.getLecturer().getId() + "-" + g.dayOfWeek + "-" + g.shiftId);
            }

            Collections.shuffle(days, rnd);
            List<Shift> shufShifts = new ArrayList<>(shifts);
            Collections.shuffle(shufShifts, rnd);

            long oldRoom = gene.roomId;
            int oldDay = gene.dayOfWeek;
            long oldShift = gene.shiftId;
            double beforeFitness = chromosome.fitness;
            boolean foundBetter = false;

            for (Integer d : days) {
                for (Shift sh : shufShifts) {
                    String dsKey = gene.sectionId + "-" + d + "-" + sh.getId();
                    if (usedDS.contains(dsKey)) continue;
                    String lk = lec != null ? lec.getId() + "-" + d + "-" + sh.getId() : null;
                    if (lk != null && (blockedLecturerShifts.contains(lk) || usedLec.contains(lk))) continue;

                    List<Room> shufRooms = new ArrayList<>(roomsForSectionAndShift(section, course, sh));
                    Collections.shuffle(shufRooms, rnd);
                    for (Room room : shufRooms) {
                        String rk = room.getId() + "-" + d + "-" + sh.getId();
                        if (blockedRoomShifts.contains(rk) || usedRoom.contains(rk)) continue;

                        gene.roomId = room.getId();
                        gene.dayOfWeek = d;
                        gene.shiftId = sh.getId();
                        evaluateFitness(chromosome);

                        if (chromosome.fitness > beforeFitness) {
                            improved++;
                            foundBetter = true;
                            break;
                        }
                        gene.roomId = oldRoom;
                        gene.dayOfWeek = oldDay;
                        gene.shiftId = oldShift;
                    }
                    if (foundBetter) break;
                }
                if (foundBetter) break;
            }
            if (!foundBetter) {
                gene.roomId = oldRoom;
                gene.dayOfWeek = oldDay;
                gene.shiftId = oldShift;
                evaluateFitness(chromosome);
            }
        }
    }

    /**
     * Smart swap mutation: hoán đổi (dayOfWeek, shiftId) giữa 2 gene CHỈ KHI
     * slot mới không gây conflict (room/lecturer trùng). Tăng đa dạng mà không phá TKB.
     */
    private void swapMutate(Chromosome chromosome, Random rnd) {
        if (chromosome.genes.size() < 2)
            return;

        // Build used slots EXCLUDING candidate genes (we'll try multiple pairs)
        Set<String> usedRoomSlots = new HashSet<>();
        Set<String> usedLecturerSlots = new HashSet<>();
        Set<String> usedSectionDayShift = new HashSet<>();
        for (int k = 0; k < chromosome.genes.size(); k++) {
            Gene g = chromosome.genes.get(k);
            usedRoomSlots.add(g.roomId + "-" + g.dayOfWeek + "-" + g.shiftId);
            usedSectionDayShift.add(g.sectionId + "-" + g.dayOfWeek + "-" + g.shiftId);
            ClassSection sec = sectionMap.get(g.sectionId);
            if (sec != null && sec.getLecturer() != null)
                usedLecturerSlots.add(sec.getLecturer().getId() + "-" + g.dayOfWeek + "-" + g.shiftId);
        }

        // Thử tối đa 15 cặp để tìm swap hợp lệ
        for (int attempt = 0; attempt < 15; attempt++) {
            int i = rnd.nextInt(chromosome.genes.size());
            int j = rnd.nextInt(chromosome.genes.size());
            if (i == j)
                continue;

            Gene gi = chromosome.genes.get(i);
            Gene gj = chromosome.genes.get(j);
            ClassSection secI = sectionMap.get(gi.sectionId);
            ClassSection secJ = sectionMap.get(gj.sectionId);
            if (secI == null || secJ == null)
                continue;

            Lecturer lecI = secI.getLecturer();
            Lecturer lecJ = secJ.getLecturer();

            // Slot mới cho gene i: (room_i, day_j, shift_j)
            String newRi = gi.roomId + "-" + gj.dayOfWeek + "-" + gj.shiftId;
            String newLi = lecI != null ? lecI.getId() + "-" + gj.dayOfWeek + "-" + gj.shiftId : null;
            String newSi = gi.sectionId + "-" + gj.dayOfWeek + "-" + gj.shiftId;

            // Slot mới cho gene j: (room_j, day_i, shift_i)
            String newRj = gj.roomId + "-" + gi.dayOfWeek + "-" + gi.shiftId;
            String newLj = lecJ != null ? lecJ.getId() + "-" + gi.dayOfWeek + "-" + gi.shiftId : null;
            String newSj = gj.sectionId + "-" + gi.dayOfWeek + "-" + gi.shiftId;

            // Bỏ slot cũ của i và j khỏi "used" (giả lập trước khi swap)
            usedRoomSlots.remove(gi.roomId + "-" + gi.dayOfWeek + "-" + gi.shiftId);
            usedRoomSlots.remove(gj.roomId + "-" + gj.dayOfWeek + "-" + gj.shiftId);
            usedLecturerSlots.remove(lecI != null ? lecI.getId() + "-" + gi.dayOfWeek + "-" + gi.shiftId : null);
            usedLecturerSlots.remove(lecJ != null ? lecJ.getId() + "-" + gj.dayOfWeek + "-" + gj.shiftId : null);
            usedSectionDayShift.remove(gi.sectionId + "-" + gi.dayOfWeek + "-" + gi.shiftId);
            usedSectionDayShift.remove(gj.sectionId + "-" + gj.dayOfWeek + "-" + gj.shiftId);

            boolean valid = true;
            if (blockedRoomShifts.contains(newRi) || usedRoomSlots.contains(newRi)) valid = false;
            if (newLi != null && (blockedLecturerShifts.contains(newLi) || usedLecturerSlots.contains(newLi))) valid = false;
            if (usedSectionDayShift.contains(newSi)) valid = false;
            if (blockedRoomShifts.contains(newRj) || usedRoomSlots.contains(newRj)) valid = false;
            if (newLj != null && (blockedLecturerShifts.contains(newLj) || usedLecturerSlots.contains(newLj))) valid = false;
            if (usedSectionDayShift.contains(newSj)) valid = false;

            if (valid) {
                Long oldShiftI = gi.shiftId;
                Integer oldDayI = gi.dayOfWeek;
                gi.shiftId = gj.shiftId;
                gi.dayOfWeek = gj.dayOfWeek;
                gj.shiftId = oldShiftI;
                gj.dayOfWeek = oldDayI;
                return;
            }

            // Khôi phục used sets để thử cặp khác
            usedRoomSlots.add(gi.roomId + "-" + gi.dayOfWeek + "-" + gi.shiftId);
            usedRoomSlots.add(gj.roomId + "-" + gj.dayOfWeek + "-" + gj.shiftId);
            if (lecI != null) usedLecturerSlots.add(lecI.getId() + "-" + gi.dayOfWeek + "-" + gi.shiftId);
            if (lecJ != null) usedLecturerSlots.add(lecJ.getId() + "-" + gj.dayOfWeek + "-" + gj.shiftId);
            usedSectionDayShift.add(gi.sectionId + "-" + gi.dayOfWeek + "-" + gi.shiftId);
            usedSectionDayShift.add(gj.sectionId + "-" + gj.dayOfWeek + "-" + gj.shiftId);
        }
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
     * Ca được phép: chỉ ONLINE_ELEARNING và ONLINE_COURSERA được xếp ca tối;
     * OFFLINE/HYBRID chỉ ca ngày.
     */
    private List<Shift> getAllowedShifts(Course course) {
        if (TimetableRegulationHelper.isEveningAllowedForCourse(course))
            return new ArrayList<>(shifts);
        return shifts.stream()
                .filter(s -> !isEveningShift(s))
                .collect(Collectors.toList());
    }

    private boolean isEveningAllowedForCourse(Course course) {
        return TimetableRegulationHelper.isEveningAllowedForCourse(course);
    }

    private boolean isEveningShift(Shift shift) {
        return TimetableRegulationHelper.isEveningShift(shift, eveningShiftStartPeriodFrom, eveningShiftNameMarkers);
    }

    /**
     * Phòng theo loại yêu cầu — không fallback sang “mọi phòng” (tránh LT/TH rơi vào ONLINE/PM lẫn lộn).
     */
    private List<Room> getSuitableRooms(String requiredType) {
        if (requiredType == null)
            return new ArrayList<>();
        String t = requiredType.trim();
        return rooms.stream()
                .filter(r -> t.equalsIgnoreCase(r.getType()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Phòng khả dụng cho (section, ca): ca tối + môn được phép ca tối → chỉ phòng ONLINE;
     * ngược lại → đúng {@link TimetableRegulationHelper#determineRequiredRoomType}.
     */
    private List<Room> roomsForSectionAndShift(ClassSection section, Course course, Shift shift) {
        if (shift != null && isEveningShift(shift) && isEveningAllowedForCourse(course)) {
            List<Room> onl = rooms.stream()
                    .filter(r -> "ONLINE".equalsIgnoreCase(r.getType()))
                    .collect(Collectors.toCollection(ArrayList::new));
            if (!onl.isEmpty())
                return onl;
        }
        return getSuitableRooms(TimetableRegulationHelper.determineRequiredRoomType(section, course));
    }

    private String determineRequiredRoomType(ClassSection section, Course course) {
        return TimetableRegulationHelper.determineRequiredRoomType(section, course);
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
        return TimetableRegulationHelper.calcSessionsPerWeek(section, course, regConfig.semesterWeeks(), timeSlots,
                shifts);
    }

    /** Pre-flight: số buổi/tuần theo Quy chế (tín chỉ → giờ học kỳ → giờ/tuần / độ dài ca). */
    public static int calcSessionsPerWeek(ClassSection section, int semesterWeeks, List<TimeSlot> timeSlots,
            List<Shift> shifts) {
        return TimetableRegulationHelper.calcSessionsPerWeek(section, section.getCourseOffering().getCourse(),
                semesterWeeks, timeSlots, shifts);
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
