package vn.edu.phenikaa.timetablex.algorithm;

import vn.edu.phenikaa.timetablex.entity.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Simulated Annealing cho bài toán xếp Thời khóa biểu.
 *
 * Dùng cùng biểu diễn Chromosome/Gene với GA để tương thích với TimetableService.
 * SA phù hợp cho bài toán tối ưu tổ hợp, có thể cân bằng tốt giữa exploration và exploitation.
 *
 * Quy trình:
 * - Khởi tạo: greedy solution (giống GA)
 * - Neighborhood: (1) Move gene sang slot khác (room, day, shift), (2) Swap day-shift giữa 2 genes
 * - Cooling: geometric T = T0 * alpha^k
 * - Acceptance: exp(-ΔE/T) khi giải pháp mới kém hơn
 */
public class SimulatedAnnealingTimetableScheduler {

    private final List<ClassSection> sections;
    private final List<Room> rooms;
    private final List<Shift> shifts;
    private final Set<String> blockedRoomShifts;
    private final Set<String> blockedLecturerShifts;
    private final Map<Long, ClassSection> sectionMap;
    private final Map<Long, Shift> shiftMap;
    private final Map<Long, Integer> lecturerAvailableSlots;
    private final int eveningShiftStartPeriodFrom;

    // SA parameters — tối ưu để giảm conflict
    private static final double T0 = 800.0;           // Nhiệt độ ban đầu cao hơn → khám phá nhiều
    private static final double T_MIN = 0.2;          // Nhiệt độ dừng thấp hơn
    private static final double ALPHA = 0.9999;       // Làm nguội rất chậm
    private static final int MAX_ITERATIONS = 180000; // Tăng số iteration
    private static final long MAX_RUNTIME_MS = 8 * 60 * 1000; // 8 phút
    private static final int PROGRESS_INTERVAL = 500;

    // Fitness weights (giống GA)
    private static final int CONFLICT_PENALTY = 1000;
    private static final int EVENING_OFFLINE_PENALTY = 500;
    private static final int SATURDAY_SOFT_PENALTY = 40;
    private static final int DAILY_CLUSTER_PENALTY = 100;
    private static final int OVERLOAD_PENALTY = 300;
    private static final int MISSING_SESSION_PENALTY = 800;
    private static final int DISTRIBUTION_BONUS = 15;
    private static final int ROOM_IMBALANCE_PENALTY = 8;

    private static final List<Integer> WEEKDAYS = List.of(2, 3, 4, 5, 6);
    private static final List<Integer> ALL_DAYS = List.of(2, 3, 4, 5, 6, 7);
    private static final Set<String> VALID_TH_ROOM_TYPES = Set.of("PM", "TN", "SB", "XT", "BV", "DN", "ONLINE");

    private GeneticTimetableScheduler.ProgressCallback progressCallback;

    public SimulatedAnnealingTimetableScheduler(
            List<ClassSection> sections,
            List<Room> rooms,
            List<Shift> shifts,
            List<TimeSlot> timeSlots,
            Set<String> blockedRoomShifts,
            Set<String> blockedLecturerShifts,
            int eveningShiftStartPeriodFrom) {

        this.sections = sections;
        this.rooms = rooms;
        this.shifts = shifts;
        this.blockedRoomShifts = blockedRoomShifts != null ? blockedRoomShifts : Collections.emptySet();
        this.blockedLecturerShifts = blockedLecturerShifts != null ? blockedLecturerShifts : Collections.emptySet();
        this.sectionMap = sections.stream().collect(Collectors.toMap(ClassSection::getId, s -> s));
        this.shiftMap = shifts.stream().collect(Collectors.toMap(Shift::getId, s -> s));
        this.lecturerAvailableSlots = computeLecturerAvailableSlots();
        this.eveningShiftStartPeriodFrom = eveningShiftStartPeriodFrom;
    }

    public void setProgressCallback(GeneticTimetableScheduler.ProgressCallback callback) {
        this.progressCallback = callback;
    }

    /**
     * Chạy thuật toán SA và trả về kết quả tương thích với GA (GeneticResult).
     */
    public GeneticTimetableScheduler.GeneticResult run() {
        long startTime = System.currentTimeMillis();
        Random rnd = new Random();

        GeneticTimetableScheduler.Chromosome current = createGreedySolution();
        evaluateFitness(current);
        GeneticTimetableScheduler.Chromosome best = current.copy();
        double bestFitness = current.fitness;
        int totalRequired = getTotalRequiredSessions();

        double T = T0;
        int iterationsWithoutImprove = 0;
        final int stagnationLimit = 5000;

        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            if (System.currentTimeMillis() - startTime > MAX_RUNTIME_MS)
                break;

            // Dừng sớm khi đã tối ưu
            if (current.conflicts == 0 && current.genes.size() >= totalRequired) {
                best = current.copy();
                bestFitness = current.fitness;
                break;
            }

            if (T < T_MIN)
                break;

            GeneticTimetableScheduler.Chromosome neighbor = getNeighbor(current.copy(), current.conflicts, rnd);
            evaluateFitness(neighbor);

            double deltaE = current.fitness - neighbor.fitness; // Energy = -fitness, nên deltaE > 0 nghĩa là neighbor kém hơn

            boolean accept = false;
            if (deltaE <= 0) {
                accept = true; // neighbor tốt hơn hoặc bằng
            } else {
                double prob = Math.exp(-deltaE / T);
                accept = rnd.nextDouble() < prob;
            }

            if (accept) {
                current = neighbor;
                if (current.fitness > bestFitness) {
                    bestFitness = current.fitness;
                    best = current.copy();
                    iterationsWithoutImprove = 0;
                } else {
                    iterationsWithoutImprove++;
                }
            } else {
                iterationsWithoutImprove++;
            }

            // Stagnation: reheat để thoát local optimum
            if (iterationsWithoutImprove >= stagnationLimit) {
                if (current.conflicts > 0 && T < T0 * 0.3) {
                    T = Math.min(T0 * 0.5, T * 3.0); // Reheat khi còn conflict
                } else {
                    T *= 0.92;
                }
                iterationsWithoutImprove = 0;
            }

            T *= ALPHA;

            if (progressCallback != null && iter > 0 && iter % PROGRESS_INTERVAL == 0) {
                int progress = Math.min(iter, MAX_ITERATIONS);
                progressCallback.onProgress(progress, MAX_ITERATIONS, best.fitness, best.conflicts);
            }
        }

        // Post-processing: repair + local search nhiều vòng hơn
        if (best != null) {
            for (int rep = 0; rep < 15 && best.conflicts > 0; rep++) {
                repairConflicts(best, rnd);
                evaluateFitness(best);
            }
            localSearchImprove(best, rnd);
            evaluateFitness(best);
        }

        if (progressCallback != null && best != null) {
            progressCallback.onProgress(MAX_ITERATIONS, MAX_ITERATIONS, best.fitness, best.conflicts);
        }

        return new GeneticTimetableScheduler.GeneticResult(best, best != null ? best.fitness : Double.NEGATIVE_INFINITY);
    }

    /**
     * Lấy láng giềng: ưu tiên move/swap gene đang conflict khi còn xung đột.
     * 70% move, 30% swap.
     */
    private GeneticTimetableScheduler.Chromosome getNeighbor(
            GeneticTimetableScheduler.Chromosome solution, int conflictCount, Random rnd) {

        if (solution.genes.isEmpty())
            return solution;

        if (rnd.nextDouble() < 0.7)
            return moveGene(solution, conflictCount, rnd);
        else
            return swapDayShift(solution, conflictCount, rnd);
    }

    /** Trả về tập chỉ số gene đang xung đột (phòng trùng, GV trùng, hoặc slot blocked). */
    private Set<Integer> getConflictingGeneIndices(GeneticTimetableScheduler.Chromosome solution) {
        Set<Integer> conflictIdx = new HashSet<>();
        Map<String, List<Integer>> roomIdx = new HashMap<>();
        Map<String, List<Integer>> lecIdx = new HashMap<>();
        for (int i = 0; i < solution.genes.size(); i++) {
            GeneticTimetableScheduler.Gene g = solution.genes.get(i);
            roomIdx.computeIfAbsent(g.roomId + "-" + g.dayOfWeek + "-" + g.shiftId, k -> new ArrayList<>()).add(i);
            ClassSection sec = sectionMap.get(g.sectionId);
            if (sec != null && sec.getLecturer() != null)
                lecIdx.computeIfAbsent(sec.getLecturer().getId() + "-" + g.dayOfWeek + "-" + g.shiftId, k -> new ArrayList<>()).add(i);
        }
        for (List<Integer> L : roomIdx.values()) {
            for (int j = 1; j < L.size(); j++) conflictIdx.add(L.get(j));
        }
        for (List<Integer> L : lecIdx.values()) {
            for (int j = 1; j < L.size(); j++) conflictIdx.add(L.get(j));
        }
        for (int i = 0; i < solution.genes.size(); i++) {
            GeneticTimetableScheduler.Gene g = solution.genes.get(i);
            if (blockedRoomShifts.contains(g.roomId + "-" + g.dayOfWeek + "-" + g.shiftId)) conflictIdx.add(i);
            ClassSection sec = sectionMap.get(g.sectionId);
            if (sec != null && sec.getLecturer() != null
                    && blockedLecturerShifts.contains(sec.getLecturer().getId() + "-" + g.dayOfWeek + "-" + g.shiftId))
                conflictIdx.add(i);
        }
        return conflictIdx;
    }

    /**
     * Di chuyển một gene sang slot mới. Khi còn conflict, ưu tiên chọn gene đang xung đột (85%).
     */
    private GeneticTimetableScheduler.Chromosome moveGene(
            GeneticTimetableScheduler.Chromosome solution, int conflictCount, Random rnd) {

        int idx;
        if (conflictCount > 0 && rnd.nextDouble() < 0.85) {
            Set<Integer> conflictIdx = getConflictingGeneIndices(solution);
            if (!conflictIdx.isEmpty()) {
                List<Integer> list = new ArrayList<>(conflictIdx);
                idx = list.get(rnd.nextInt(list.size()));
            } else {
                idx = rnd.nextInt(solution.genes.size());
            }
        } else {
            idx = rnd.nextInt(solution.genes.size());
        }
        GeneticTimetableScheduler.Gene gene = solution.genes.get(idx);
        ClassSection section = sectionMap.get(gene.sectionId);
        if (section == null)
            return solution;

        Course course = section.getCourseOffering().getCourse();
        Lecturer lec = section.getLecturer();
        List<Room> suitableRooms = getSuitableRooms(determineRequiredRoomType(section, course));
        List<Shift> allowedShifts = getAllowedShifts(course);
        if (suitableRooms.isEmpty() || allowedShifts.isEmpty())
            return solution;

        Set<String> currRoom = new HashSet<>();
        Set<String> currLec = new HashSet<>();
        Set<String> currSecDS = new HashSet<>();
        for (int k = 0; k < solution.genes.size(); k++) {
            if (k == idx) continue;
            GeneticTimetableScheduler.Gene g = solution.genes.get(k);
            currRoom.add(g.roomId + "-" + g.dayOfWeek + "-" + g.shiftId);
            currSecDS.add(g.sectionId + "-" + g.dayOfWeek + "-" + g.shiftId);
            ClassSection s = sectionMap.get(g.sectionId);
            if (s != null && s.getLecturer() != null)
                currLec.add(s.getLecturer().getId() + "-" + g.dayOfWeek + "-" + g.shiftId);
        }

        List<Integer> days = new ArrayList<>(ALL_DAYS);
        Collections.shuffle(days, rnd);
        List<Shift> shufShifts = new ArrayList<>(allowedShifts);
        Collections.shuffle(shufShifts, rnd);
        List<Room> shufRooms = new ArrayList<>(suitableRooms);
        Collections.shuffle(shufRooms, rnd);

        for (Integer day : days) {
            for (Shift shift : shufShifts) {
                String dsKey = gene.sectionId + "-" + day + "-" + shift.getId();
                if (currSecDS.contains(dsKey)) continue;
                String lk = lec != null ? lec.getId() + "-" + day + "-" + shift.getId() : null;
                if (lk != null && (blockedLecturerShifts.contains(lk) || currLec.contains(lk)))
                    continue;
                for (Room room : shufRooms) {
                    String rk = room.getId() + "-" + day + "-" + shift.getId();
                    if (blockedRoomShifts.contains(rk) || currRoom.contains(rk)) continue;

                    gene.roomId = room.getId();
                    gene.shiftId = shift.getId();
                    gene.dayOfWeek = day;
                    return solution;
                }
            }
        }
        return solution;
    }

    /**
     * Hoán đổi (day, shift) giữa hai gene. Khi còn conflict, ưu tiên chọn ít nhất 1 gene conflict.
     */
    private GeneticTimetableScheduler.Chromosome swapDayShift(
            GeneticTimetableScheduler.Chromosome solution, int conflictCount, Random rnd) {

        if (solution.genes.size() < 2)
            return solution;

        Set<Integer> conflictIdx = conflictCount > 0 ? getConflictingGeneIndices(solution) : Collections.emptySet();
        for (int attempt = 0; attempt < 35; attempt++) {
            int i, j;
            if (conflictCount > 0 && !conflictIdx.isEmpty() && rnd.nextDouble() < 0.8) {
                List<Integer> list = new ArrayList<>(conflictIdx);
                i = list.get(rnd.nextInt(list.size()));
                j = rnd.nextInt(solution.genes.size());
                if (i == j) continue;
            } else {
                i = rnd.nextInt(solution.genes.size());
                j = rnd.nextInt(solution.genes.size());
            }
            if (i == j) continue;

            GeneticTimetableScheduler.Gene gi = solution.genes.get(i);
            GeneticTimetableScheduler.Gene gj = solution.genes.get(j);
            ClassSection secI = sectionMap.get(gi.sectionId);
            ClassSection secJ = sectionMap.get(gj.sectionId);
            if (secI == null || secJ == null) continue;

            Lecturer lecI = secI.getLecturer();
            Lecturer lecJ = secJ.getLecturer();

            Set<String> usedRoom = new HashSet<>();
            Set<String> usedLec = new HashSet<>();
            Set<String> usedSecDS = new HashSet<>();
            for (int k = 0; k < solution.genes.size(); k++) {
                if (k == i || k == j) continue;
                GeneticTimetableScheduler.Gene g = solution.genes.get(k);
                usedRoom.add(g.roomId + "-" + g.dayOfWeek + "-" + g.shiftId);
                usedSecDS.add(g.sectionId + "-" + g.dayOfWeek + "-" + g.shiftId);
                ClassSection s = sectionMap.get(g.sectionId);
                if (s != null && s.getLecturer() != null)
                    usedLec.add(s.getLecturer().getId() + "-" + g.dayOfWeek + "-" + g.shiftId);
            }

            String newRi = gi.roomId + "-" + gj.dayOfWeek + "-" + gj.shiftId;
            String newLi = lecI != null ? lecI.getId() + "-" + gj.dayOfWeek + "-" + gj.shiftId : null;
            String newSi = gi.sectionId + "-" + gj.dayOfWeek + "-" + gj.shiftId;
            String newRj = gj.roomId + "-" + gi.dayOfWeek + "-" + gi.shiftId;
            String newLj = lecJ != null ? lecJ.getId() + "-" + gi.dayOfWeek + "-" + gi.shiftId : null;
            String newSj = gj.sectionId + "-" + gi.dayOfWeek + "-" + gi.shiftId;

            if (blockedRoomShifts.contains(newRi) || usedRoom.contains(newRi)) continue;
            if (newLi != null && (blockedLecturerShifts.contains(newLi) || usedLec.contains(newLi))) continue;
            if (usedSecDS.contains(newSi)) continue;
            if (blockedRoomShifts.contains(newRj) || usedRoom.contains(newRj)) continue;
            if (newLj != null && (blockedLecturerShifts.contains(newLj) || usedLec.contains(newLj))) continue;
            if (usedSecDS.contains(newSj)) continue;

            int tmpDay = gi.dayOfWeek;
            long tmpShift = gi.shiftId;
            gi.dayOfWeek = gj.dayOfWeek;
            gi.shiftId = gj.shiftId;
            gj.dayOfWeek = tmpDay;
            gj.shiftId = tmpShift;
            return solution;
        }
        return solution;
    }

    private GeneticTimetableScheduler.Chromosome createGreedySolution() {
        GeneticTimetableScheduler.Chromosome chromosome = new GeneticTimetableScheduler.Chromosome();
        Set<String> usedRoomShifts = new HashSet<>(blockedRoomShifts);
        Set<String> usedLecturerShifts = new HashSet<>(blockedLecturerShifts);
        Map<Long, Integer> lecturerAssigned = new HashMap<>();
        Random rnd = new Random();

        Map<Long, Integer> lecturerRequired = new HashMap<>();
        for (ClassSection s : sections) {
            if (s.getLecturer() == null) continue;
            lecturerRequired.merge(s.getLecturer().getId(), GeneticTimetableScheduler.calcSessionsPerWeek(s), (a, b) -> a + b);
        }

        List<ClassSection> sorted = new ArrayList<>(sections);
        sorted.sort((s1, s2) -> {
            boolean th1 = s1.getSectionType() == ClassSection.SectionType.TH;
            boolean th2 = s2.getSectionType() == ClassSection.SectionType.TH;
            if (th1 && !th2) return -1;
            if (!th1 && th2) return 1;
            double fill1 = fillRatio(s1, lecturerRequired);
            double fill2 = fillRatio(s2, lecturerRequired);
            return Double.compare(fill2, fill1);
        });

        for (ClassSection section : sorted) {
            Course course = section.getCourseOffering().getCourse();
            Lecturer lecturer = section.getLecturer();
            int sessionsPerWeek = determineSessionsPerWeek(section, course);

            int cap = lecturer != null ? lecturerAvailableSlots.getOrDefault(lecturer.getId(), Integer.MAX_VALUE) : Integer.MAX_VALUE;
            int assigned = lecturer != null ? lecturerAssigned.getOrDefault(lecturer.getId(), 0) : 0;
            int canAssign = Math.min(sessionsPerWeek, cap - assigned);
            if (canAssign <= 0) continue;

            List<Room> suitableRooms = getSuitableRooms(determineRequiredRoomType(section, course));
            List<Shift> allowedShifts = getAllowedShifts(course);
            Set<String> usedDS = new HashSet<>();

            for (int s = 0; s < canAssign; s++) {
                List<Integer> shuffledPrimary = new ArrayList<>(WEEKDAYS);
                Collections.shuffle(shuffledPrimary, rnd);
                List<Shift> shuffledShifts = new ArrayList<>(allowedShifts);
                Collections.shuffle(shuffledShifts, rnd);
                List<Room> shuffledRooms = new ArrayList<>(suitableRooms);
                Collections.shuffle(shuffledRooms, rnd);
                List<Integer> searchOrder = new ArrayList<>(shuffledPrimary);
                searchOrder.add(7);

                boolean ok = tryAssign(chromosome, section, lecturer, searchOrder, shuffledShifts, shuffledRooms,
                        usedRoomShifts, usedLecturerShifts, usedDS, lecturerAssigned, false);
                if (!ok) {
                    ok = tryAssign(chromosome, section, lecturer, searchOrder, shuffledShifts, shuffledRooms,
                            usedRoomShifts, usedLecturerShifts, usedDS, lecturerAssigned, true);
                }
                if (!ok) {
                    int day = searchOrder.get(rnd.nextInt(searchOrder.size()));
                    Shift shift = shuffledShifts.get(rnd.nextInt(shuffledShifts.size()));
                    String dsKey = day + "-" + shift.getId();
                    if (!usedDS.contains(dsKey)) {
                        String lk = lecturer != null ? lecturer.getId() + "-" + day + "-" + shift.getId() : null;
                        if (lk == null || !usedLecturerShifts.contains(lk)) {
                            Room room = shuffledRooms.get(rnd.nextInt(shuffledRooms.size()));
                            chromosome.genes.add(new GeneticTimetableScheduler.Gene(section.getId(), room.getId(), shift.getId(), day));
                            usedDS.add(dsKey);
                            if (lk != null) usedLecturerShifts.add(lk);
                            if (lecturer != null) lecturerAssigned.merge(lecturer.getId(), 1, (a, b) -> a + b);
                        }
                    }
                }
            }
        }
        return chromosome;
    }

    private boolean tryAssign(
            GeneticTimetableScheduler.Chromosome chromosome, ClassSection section, Lecturer lecturer,
            List<Integer> days, List<Shift> shiftList, List<Room> roomList,
            Set<String> usedRoomShifts, Set<String> usedLecturerShifts, Set<String> usedDS,
            Map<Long, Integer> lecturerAssigned, boolean allowRoomConflict) {

        for (Integer day : days) {
            for (Shift shift : shiftList) {
                String dsKey = day + "-" + shift.getId();
                if (usedDS.contains(dsKey)) continue;
                String lk = lecturer != null ? lecturer.getId() + "-" + day + "-" + shift.getId() : null;
                if (lk != null && usedLecturerShifts.contains(lk)) continue;
                for (Room room : roomList) {
                    String rk = room.getId() + "-" + day + "-" + shift.getId();
                    if (!allowRoomConflict && usedRoomShifts.contains(rk)) continue;

                    chromosome.genes.add(new GeneticTimetableScheduler.Gene(section.getId(), room.getId(), shift.getId(), day));
                    usedRoomShifts.add(rk);
                    if (lk != null) usedLecturerShifts.add(lk);
                    usedDS.add(dsKey);
                    if (lecturer != null) lecturerAssigned.merge(lecturer.getId(), 1, (a, b) -> a + b);
                    return true;
                }
            }
        }
        return false;
    }

    private void evaluateFitness(GeneticTimetableScheduler.Chromosome chromosome) {
        int conflictCount = 0;
        int eveningViolations = 0;
        int saturdayCount = 0;
        int dailyClusterPenalty = 0;
        int overloadPenalty = 0;
        int missingPenalty = 0;
        int distributionScore = 0;

        Map<String, Integer> roomUsage = new HashMap<>();
        Map<String, Integer> lecturerUsage = new HashMap<>();
        Map<Long, Map<Integer, Integer>> lecturerDailyCount = new HashMap<>();
        Map<Long, Integer> lecturerTotal = new HashMap<>();
        Map<Long, Integer> sectionCount = new HashMap<>();

        for (GeneticTimetableScheduler.Gene gene : chromosome.genes) {
            ClassSection section = sectionMap.get(gene.sectionId);
            if (section == null) continue;

            Course course = section.getCourseOffering().getCourse();
            Lecturer lecturer = section.getLecturer();

            String rk = gene.roomId + "-" + gene.dayOfWeek + "-" + gene.shiftId;
            if (blockedRoomShifts.contains(rk)) conflictCount++;
            roomUsage.merge(rk, 1, (a, b) -> a + b);
            if (roomUsage.get(rk) > 1) conflictCount++;

            if (lecturer != null) {
                String lk = lecturer.getId() + "-" + gene.dayOfWeek + "-" + gene.shiftId;
                if (blockedLecturerShifts.contains(lk)) conflictCount++;
                lecturerUsage.merge(lk, 1, (a, b) -> a + b);
                if (lecturerUsage.get(lk) > 1) conflictCount++;
                lecturerTotal.merge(lecturer.getId(), 1, (a, b) -> a + b);
                lecturerDailyCount.computeIfAbsent(lecturer.getId(), k -> new HashMap<>()).merge(gene.dayOfWeek, 1, (a, b) -> a + b);
            }

            Shift shift = shiftMap.get(gene.shiftId);
            if (shift != null && isEveningShift(shift) && !isEveningAllowedForCourse(course)) eveningViolations++;
            if (gene.dayOfWeek == 7) saturdayCount++;
            sectionCount.merge(gene.sectionId, 1, (a, b) -> a + b);
        }

        for (Map<Integer, Integer> dayMap : lecturerDailyCount.values()) {
            for (int cnt : dayMap.values()) {
                if (cnt > 2) dailyClusterPenalty += (cnt - 2) * DAILY_CLUSTER_PENALTY;
            }
        }
        for (Map.Entry<Long, Integer> e : lecturerTotal.entrySet()) {
            int cap = lecturerAvailableSlots.getOrDefault(e.getKey(), Integer.MAX_VALUE);
            if (e.getValue() > cap) overloadPenalty += (e.getValue() - cap) * OVERLOAD_PENALTY;
        }
        for (ClassSection section : sections) {
            int required = determineSessionsPerWeek(section, section.getCourseOffering().getCourse());
            int actual = sectionCount.getOrDefault(section.getId(), 0);
            if (actual < required) missingPenalty += (required - actual) * MISSING_SESSION_PENALTY;
        }

        Map<Long, Set<Integer>> sectionDays = new HashMap<>();
        for (GeneticTimetableScheduler.Gene g : chromosome.genes) {
            sectionDays.computeIfAbsent(g.sectionId, k -> new HashSet<>()).add(g.dayOfWeek);
        }
        for (Set<Integer> days : sectionDays.values()) {
            if (days.size() >= 2) distributionScore += days.size();
        }

        Map<Long, Integer> roomUsageCount = new HashMap<>();
        for (GeneticTimetableScheduler.Gene g : chromosome.genes) {
            roomUsageCount.merge(g.roomId, 1, (a, b) -> a + b);
        }
        int roomImbalance = 0;
        if (!roomUsageCount.isEmpty()) {
            double avg = (double) chromosome.genes.size() / roomUsageCount.size();
            for (int cnt : roomUsageCount.values()) {
                if (cnt > avg + 1.5) roomImbalance += (int) (cnt - avg);
            }
        }

        chromosome.conflicts = conflictCount;
        chromosome.fitness = (double) distributionScore * DISTRIBUTION_BONUS
                - (double) conflictCount * CONFLICT_PENALTY
                - (double) eveningViolations * EVENING_OFFLINE_PENALTY
                - (double) saturdayCount * SATURDAY_SOFT_PENALTY
                - (double) dailyClusterPenalty
                - (double) overloadPenalty
                - (double) missingPenalty
                - (double) roomImbalance * ROOM_IMBALANCE_PENALTY;
    }

    private void repairConflicts(GeneticTimetableScheduler.Chromosome chromosome, Random rnd) {
        if (chromosome.genes.isEmpty()) return;
        evaluateFitness(chromosome);
        Map<String, List<Integer>> roomIdx = new HashMap<>();
        Map<String, List<Integer>> lecIdx = new HashMap<>();
        for (int i = 0; i < chromosome.genes.size(); i++) {
            GeneticTimetableScheduler.Gene g = chromosome.genes.get(i);
            roomIdx.computeIfAbsent(g.roomId + "-" + g.dayOfWeek + "-" + g.shiftId, k -> new ArrayList<>()).add(i);
            ClassSection sec = sectionMap.get(g.sectionId);
            if (sec != null && sec.getLecturer() != null)
                lecIdx.computeIfAbsent(sec.getLecturer().getId() + "-" + g.dayOfWeek + "-" + g.shiftId, k -> new ArrayList<>()).add(i);
        }
        Set<Integer> conflictIdx = new HashSet<>();
        for (List<Integer> L : roomIdx.values()) {
            for (int j = 1; j < L.size(); j++) conflictIdx.add(L.get(j));
        }
        for (List<Integer> L : lecIdx.values()) {
            for (int j = 1; j < L.size(); j++) conflictIdx.add(L.get(j));
        }
        // Thêm gene nằm trong slot bị blocked (phòng/GV)
        for (int i = 0; i < chromosome.genes.size(); i++) {
            GeneticTimetableScheduler.Gene g = chromosome.genes.get(i);
            String rk = g.roomId + "-" + g.dayOfWeek + "-" + g.shiftId;
            if (blockedRoomShifts.contains(rk)) conflictIdx.add(i);
            ClassSection sec = sectionMap.get(g.sectionId);
            if (sec != null && sec.getLecturer() != null) {
                String lk = sec.getLecturer().getId() + "-" + g.dayOfWeek + "-" + g.shiftId;
                if (blockedLecturerShifts.contains(lk)) conflictIdx.add(i);
            }
        }
        Set<String> currRoom = chromosome.genes.stream()
                .map(g -> g.roomId + "-" + g.dayOfWeek + "-" + g.shiftId)
                .collect(Collectors.toSet());
        Set<String> currLec = new HashSet<>();
        for (GeneticTimetableScheduler.Gene g : chromosome.genes) {
            ClassSection sec = sectionMap.get(g.sectionId);
            if (sec != null && sec.getLecturer() != null)
                currLec.add(sec.getLecturer().getId() + "-" + g.dayOfWeek + "-" + g.shiftId);
        }
        List<Integer> days = new ArrayList<>(WEEKDAYS);
        days.add(7);
        for (int idx : conflictIdx) {
            if (idx >= chromosome.genes.size()) continue;
            GeneticTimetableScheduler.Gene gene = chromosome.genes.get(idx);
            ClassSection section = sectionMap.get(gene.sectionId);
            if (section == null) continue;
            Course course = section.getCourseOffering().getCourse();
            Lecturer lec = section.getLecturer();
            List<Room> roomList = getSuitableRooms(determineRequiredRoomType(section, course));
            List<Shift> shiftList = getAllowedShifts(course);
            if (roomList.isEmpty() || shiftList.isEmpty()) continue;

            // Quan trọng: loại slot cũ khỏi tracking để slot đó có thể dùng lại cho gene khác
            String oldRk = gene.roomId + "-" + gene.dayOfWeek + "-" + gene.shiftId;
            currRoom.remove(oldRk);
            String oldLk = lec != null ? lec.getId() + "-" + gene.dayOfWeek + "-" + gene.shiftId : null;
            if (oldLk != null) currLec.remove(oldLk);

            Collections.shuffle(days, rnd);
            Collections.shuffle(shiftList, rnd);
            Collections.shuffle(roomList, rnd);
            boolean fixed = false;
            outer: for (Integer d : days) {
                for (Shift sh : shiftList) {
                    final int fd = d;
                    final long fsid = sh.getId();
                    if (chromosome.genes.stream().anyMatch(g -> g != gene && g.sectionId.equals(gene.sectionId) && g.dayOfWeek == fd && g.shiftId.equals(fsid)))
                        continue;
                    String lk = lec != null ? lec.getId() + "-" + d + "-" + sh.getId() : null;
                    if (lk != null && (blockedLecturerShifts.contains(lk) || currLec.contains(lk))) continue;
                    for (Room room : roomList) {
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
            if (!fixed) {
                currRoom.add(oldRk);
                if (oldLk != null) currLec.add(oldLk);
            }
        }
    }

    private void localSearchImprove(GeneticTimetableScheduler.Chromosome chromosome, Random rnd) {
        if (chromosome.genes.isEmpty()) return;
        List<Integer> days = new ArrayList<>(ALL_DAYS);
        int maxAttempts = Math.min(200, chromosome.genes.size() * 8);
        int improved = 0;
        Set<Integer> conflictIdx = getConflictingGeneIndices(chromosome);
        List<Integer> conflictList = new ArrayList<>(conflictIdx);

        for (int attempt = 0; attempt < maxAttempts && improved < 25; attempt++) {
            int idx;
            if (chromosome.conflicts > 0 && !conflictList.isEmpty() && rnd.nextDouble() < 0.75)
                idx = conflictList.get(rnd.nextInt(conflictList.size()));
            else
                idx = rnd.nextInt(chromosome.genes.size());
            GeneticTimetableScheduler.Gene gene = chromosome.genes.get(idx);
            ClassSection section = sectionMap.get(gene.sectionId);
            if (section == null) continue;

            Course course = section.getCourseOffering().getCourse();
            Lecturer lec = section.getLecturer();
            List<Room> roomList = getSuitableRooms(determineRequiredRoomType(section, course));
            List<Shift> shiftList = getAllowedShifts(course);
            if (roomList.isEmpty() || shiftList.isEmpty()) continue;

            Set<String> usedRoom = new HashSet<>();
            Set<String> usedLec = new HashSet<>();
            Set<String> usedDS = new HashSet<>();
            for (int k = 0; k < chromosome.genes.size(); k++) {
                if (k == idx) continue;
                GeneticTimetableScheduler.Gene g = chromosome.genes.get(k);
                usedRoom.add(g.roomId + "-" + g.dayOfWeek + "-" + g.shiftId);
                usedDS.add(g.sectionId + "-" + g.dayOfWeek + "-" + g.shiftId);
                ClassSection s = sectionMap.get(g.sectionId);
                if (s != null && s.getLecturer() != null)
                    usedLec.add(s.getLecturer().getId() + "-" + g.dayOfWeek + "-" + g.shiftId);
            }

            Collections.shuffle(days, rnd);
            List<Shift> shufShifts = new ArrayList<>(shiftList);
            Collections.shuffle(shufShifts, rnd);
            List<Room> shufRooms = new ArrayList<>(roomList);
            Collections.shuffle(shufRooms, rnd);

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

    private Map<Long, Integer> computeLecturerAvailableSlots() {
        int totalSlots = shifts.size() * 6;
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

    private double fillRatio(ClassSection section, Map<Long, Integer> required) {
        if (section.getLecturer() == null) return 0;
        Long lid = section.getLecturer().getId();
        int avail = lecturerAvailableSlots.getOrDefault(lid, 1);
        int req = required.getOrDefault(lid, 0);
        return avail > 0 ? (double) req / avail : 0;
    }

    /** Ca được phép: chỉ ONLINE_ELEARNING và ONLINE_COURSERA được xếp ca tối; OFFLINE/HYBRID chỉ ca ngày */
    private List<Shift> getAllowedShifts(Course course) {
        if (isEveningAllowedForCourse(course)) return new ArrayList<>(shifts);
        return shifts.stream().filter(s -> !isEveningShift(s)).collect(Collectors.toList());
    }

    /** Học phần ONLINE / E-learning / Coursera mới được xếp ca tối */
    private boolean isEveningAllowedForCourse(Course course) {
        return course.getLearningMethod() == Course.LearningMethod.ONLINE_ELEARNING
                || course.getLearningMethod() == Course.LearningMethod.ONLINE_COURSERA;
    }

    private List<Room> getSuitableRooms(String requiredType) {
        if (VALID_TH_ROOM_TYPES.contains(requiredType)) {
            return new ArrayList<>(rooms.stream().filter(r -> requiredType.equals(r.getType())).toList());
        }
        List<Room> filtered = rooms.stream().filter(r -> requiredType.equals(r.getType())).collect(Collectors.toList());
        return filtered.isEmpty() ? new ArrayList<>(rooms) : filtered;
    }

    private boolean isEveningShift(Shift shift) {
        return shift.getStartPeriod() != null && shift.getStartPeriod() >= eveningShiftStartPeriodFrom;
    }

    /** Dùng cho room type: OFFLINE/HYBRID/ONLINE_COURSERA cần phòng LT thật (không ảo) */
    private boolean isOfflineCourse(Course course) {
        return course.getLearningMethod() == Course.LearningMethod.OFFLINE
                || course.getLearningMethod() == Course.LearningMethod.HYBRID
                || course.getLearningMethod() == Course.LearningMethod.ONLINE_COURSERA;
    }

    private String determineRequiredRoomType(ClassSection section, Course course) {
        String rt = course.getRequiredRoomType();
        if (section.getSectionType() == ClassSection.SectionType.TH) {
            return (rt != null && VALID_TH_ROOM_TYPES.contains(rt)) ? rt : "PM";
        }
        boolean isOnlineOnly = course.getLearningMethod() == Course.LearningMethod.ONLINE_ELEARNING;
        return (isOnlineOnly && "ONLINE".equals(rt)) ? "ONLINE" : "LT";
    }

    private int determineSessionsPerWeek(ClassSection section, Course course) {
        return calcSessionsPerWeek(section, course);
    }

    private static int calcSessionsPerWeek(ClassSection section, Course course) {
        if (section.getSectionType() == ClassSection.SectionType.TH) {
            return (course.getPracticeCredits() != null && course.getPracticeCredits() >= 3.0) ? 2 : 1;
        } else {
            return (course.getTheoryCredits() != null && course.getTheoryCredits() >= 4.0) ? 2 : 1;
        }
    }

    private int getTotalRequiredSessions() {
        int total = 0;
        for (ClassSection section : sections) {
            total += determineSessionsPerWeek(section, section.getCourseOffering().getCourse());
        }
        return total;
    }
}
