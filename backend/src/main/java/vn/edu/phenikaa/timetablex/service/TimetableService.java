package vn.edu.phenikaa.timetablex.service;

import jakarta.persistence.EntityManager;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.phenikaa.timetablex.algorithm.GeneticTimetableScheduler;
import vn.edu.phenikaa.timetablex.algorithm.SimulatedAnnealingTimetableScheduler;
import vn.edu.phenikaa.timetablex.entity.*;
import vn.edu.phenikaa.timetablex.repository.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TimetableService {

        private static final Set<String> VALID_TH_ROOM_TYPES =
                Set.of("PM", "TN", "SB", "XT", "BV", "DN", "ONLINE");
        private static final Map<Integer, String> DAY_NAMES = Map.of(
                2, "Thứ 2", 3, "Thứ 3", 4, "Thứ 4", 5, "Thứ 5", 6, "Thứ 6", 7, "Thứ 7");
        private static final Map<String, String> LEARNING_METHOD_NAMES = Map.of(
                "OFFLINE", "Trực tiếp", "ONLINE_ELEARNING", "E-Learning (100% online)",
                "ONLINE_COURSERA", "Coursera (Hybrid)", "HYBRID", "Kết hợp");

        @Autowired
        private TimetableEntryRepository timetableRepo;
        @Autowired
        private EntityManager entityManager;
        @Autowired
        private ClassSectionRepository sectionRepo;
        @Autowired
        private RoomRepository roomRepo;
        @Autowired
        private TimeSlotRepository timeSlotRepo;
        @Autowired
        private ShiftRepository shiftRepo;
        @Autowired
        private SemesterRepository semesterRepo;

        @Value("${timetable.evening-shift.start-period-from:13}")
        private int eveningShiftStartPeriodFrom;
        @Value("${timetable.ga.max-runtime-ms:240000}")
        private long gaMaxRuntimeMs;
        @Value("${timetable.ga.max-generations:500}")
        private int gaMaxGenerations;

        /**
         * Thuật toán xếp TKB tự động cho một học kỳ.
         * algorithm: "SA" = Simulated Annealing (mặc định), "GA" = Genetic Algorithm.
         * Ca học (Shift) là tiền đề bắt buộc. Không xếp OFFLINE/HYBRID vào ca tối.
         */
        @Transactional
        public Map<String, Object> generateTimetable(Long semesterId, String algorithm) {
                String algo = (algorithm != null && algorithm.toUpperCase().startsWith("GA")) ? "GA" : "SA";
                Semester semester = semesterRepo.findById(semesterId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Không tìm thấy học kỳ ID: " + semesterId));

                // Xóa toàn bộ TKB cũ (DRAFT + CONFIRMED) để xếp lại từ đầu, tránh slot bị chiếm
                timetableRepo.deleteAllBySemesterId(semesterId);
                entityManager.flush();

                List<ClassSection> sections = sectionRepo.findByCourseOffering_Semester_Id(semesterId).stream()
                                .filter(s -> s.getLecturer() != null && !Boolean.TRUE.equals(s.getSkipAssignment()))
                                .collect(Collectors.toList());

                List<Room> rooms = roomRepo.findAll();
                List<Shift> shifts = shiftRepo.findAll();
                List<TimeSlot> timeSlots = timeSlotRepo.findAll().stream()
                                .sorted(Comparator.comparing(TimeSlot::getPeriodIndex))
                                .collect(Collectors.toList());

                if (sections.isEmpty()) {
                        throw new IllegalStateException(
                                        "Không có lớp nào đã phân công giảng viên. Vui lòng phân công giảng viên cho các lớp học phần trước khi xếp TKB.");
                }
                if (rooms.isEmpty() || shifts.isEmpty()) {
                        throw new IllegalStateException("Chưa có đủ dữ liệu: phòng học hoặc ca học");
                }
                if (shifts.stream().anyMatch(s -> s.getStartPeriod() == null || s.getEndPeriod() == null)) {
                        throw new IllegalStateException(
                                        "Một số ca học chưa có tiết bắt đầu/kết thúc. Vui lòng cập nhật đầy đủ trước khi xếp TKB.");
                }

                // ─── Pre-flight: kiểm tra tải GV và phòng học ─────────────────────────────
                int totalShiftSlotsPerWeek = shifts.size() * 6; // N ca × 6 ngày (Thứ 2–7)

                // Tính available slots thực tế cho từng GV (không trừ blocked vì không có TKB
                // cũ)
                Map<Long, Integer> lecturerAvailableById = new HashMap<>();
                for (ClassSection s : sections) {
                        if (s.getLecturer() == null)
                                continue;
                        lecturerAvailableById.putIfAbsent(s.getLecturer().getId(), totalShiftSlotsPerWeek);
                }

                // Map: tên GV → [required, available]
                Map<String, int[]> lecturerLoad = new HashMap<>();
                int totalLtSessionsNeeded = 0;
                int totalThSessionsNeeded = 0;
                for (ClassSection s : sections) {
                        if (s.getLecturer() == null)
                                continue;
                        String name = s.getLecturer().getName();
                        Long lid = s.getLecturer().getId();
                        int sessionsNeeded = GeneticTimetableScheduler.calcSessionsPerWeek(s);
                        int available = lecturerAvailableById.getOrDefault(lid, totalShiftSlotsPerWeek);
                        lecturerLoad.computeIfAbsent(name, k -> new int[] { 0, available })[0] += sessionsNeeded;
                        if (s.getSectionType() == ClassSection.SectionType.TH)
                                totalThSessionsNeeded += sessionsNeeded;
                        else
                                totalLtSessionsNeeded += sessionsNeeded;
                }

                // Phòng LT + ONLINE tính chung cho khối lý thuyết
                long ltRoomCount = rooms.stream()
                                .filter(r -> "LT".equals(r.getType()) || "ONLINE".equals(r.getType()))
                                .count();
                // Phòng thực hành: PM, TN, SB, XT, BV, DN
                Set<String> thRoomTypes = Set.of("PM", "TN", "SB", "XT", "BV", "DN");
                long thRoomCount = rooms.stream()
                                .filter(r -> thRoomTypes.contains(r.getType()))
                                .count();
                int ltRoomCapacity = (int) ltRoomCount * shifts.size() * 6;
                int thRoomCapacity = (int) thRoomCount * shifts.size() * 6;

                List<String> overloadWarnings = new ArrayList<>();
                // Cảnh báo phòng học
                if (totalLtSessionsNeeded > ltRoomCapacity) {
                        overloadWarnings.add(String.format(
                                        "⚠ PHÒNG LT/ONLINE: cần %d slot nhưng chỉ có %d phòng × %d ca × 6 ngày = %d slot. Thiếu %d slot.",
                                        totalLtSessionsNeeded, ltRoomCount, shifts.size(), ltRoomCapacity,
                                        totalLtSessionsNeeded - ltRoomCapacity));
                }
                if (totalThSessionsNeeded > thRoomCapacity && thRoomCapacity > 0) {
                        overloadWarnings.add(String.format(
                                        "⚠ PHÒNG THỰC HÀNH (PM/TN/SB/XT/BV/DN): cần %d slot nhưng chỉ có %d phòng × %d ca × 6 ngày = %d slot. Thiếu %d slot.",
                                        totalThSessionsNeeded, thRoomCount, shifts.size(), thRoomCapacity,
                                        totalThSessionsNeeded - thRoomCapacity));
                }
                // Cảnh báo giảng viên quá tải
                for (Map.Entry<String, int[]> entry : lecturerLoad.entrySet()) {
                        int required = entry.getValue()[0];
                        int available = entry.getValue()[1];
                        if (required > available) {
                                overloadWarnings.add(String.format(
                                                "⚠ GV '%s': cần %d slot/tuần nhưng chỉ có %d slot (thừa %d). Một số lớp sẽ không được xếp.",
                                                entry.getKey(), required, available, required - available));
                        }
                }

                // Đã xóa hết TKB ở trên → không có slot bị chiếm
                Set<String> blockedRoomShifts = new HashSet<>();
                Set<String> blockedLecturerShifts = new HashSet<>();

                GeneticTimetableScheduler.GeneticResult result;
                if ("GA".equals(algo)) {
                        GeneticTimetableScheduler gaScheduler = new GeneticTimetableScheduler(
                                        sections, rooms, shifts, timeSlots, blockedRoomShifts, blockedLecturerShifts,
                                        eveningShiftStartPeriodFrom, gaMaxRuntimeMs, gaMaxGenerations);
                        gaScheduler.setProgressCallback((generation, maxGenerations, bestFitness, conflicts) -> {});
                        result = gaScheduler.run();
                } else {
                        SimulatedAnnealingTimetableScheduler saScheduler = new SimulatedAnnealingTimetableScheduler(
                                        sections, rooms, shifts, timeSlots, blockedRoomShifts, blockedLecturerShifts,
                                        eveningShiftStartPeriodFrom);
                        saScheduler.setProgressCallback((generation, maxGenerations, bestFitness, conflicts) -> {});
                        result = saScheduler.run();
                }
                GeneticTimetableScheduler.Chromosome bestChromosome = result.bestChromosome;

                if (bestChromosome == null || bestChromosome.getGenes().isEmpty()) {
                        throw new IllegalStateException(
                                        "Không thể tạo giải pháp xếp TKB. Vui lòng kiểm tra: có đủ phòng học (LT/PM/TN), ca học có tiết bắt đầu/kết thúc, và TKB đã xác nhận có quá nhiều slot chiếm chưa.");
                }

                // Build lookup maps
                Map<Long, ClassSection> sectionMap = sections.stream()
                                .collect(Collectors.toMap(ClassSection::getId, s -> s));
                Map<Long, Room> roomMap = rooms.stream()
                                .collect(Collectors.toMap(Room::getId, r -> r));
                Map<Long, Shift> shiftMap = shifts.stream()
                                .collect(Collectors.toMap(Shift::getId, s -> s));
                Map<Integer, TimeSlot> timeSlotByPeriod = timeSlots.stream()
                                .collect(Collectors.toMap(TimeSlot::getPeriodIndex, ts -> ts, (a, b) -> a));

                Set<String> usedRoomShifts = new HashSet<>(blockedRoomShifts);
                Set<String> usedLecturerShifts = new HashSet<>(blockedLecturerShifts);
                // Track: sectionId → set of "day-shiftId" đã dùng (tránh 1 lớp xếp 2 ca cùng
                // ngày-ca)
                Map<Long, Set<String>> sectionUsedDayShifts = new HashMap<>();

                List<TimetableEntry> entries = new ArrayList<>();
                int conflictCount = 0;
                int gaConflicts = bestChromosome.getConflicts();
                Map<String, Integer> conflictCountBySection = new LinkedHashMap<>();
                Map<String, String> conflictReasonBySection = new LinkedHashMap<>();
                List<String> conflicts = new ArrayList<>();

                List<Room> allRooms = new ArrayList<>(rooms);
                List<Shift> allShifts = new ArrayList<>(shifts);

                Map<String, Integer> lecturerRequired = new HashMap<>();
                for (Map.Entry<String, int[]> e : lecturerLoad.entrySet()) {
                        lecturerRequired.put(e.getKey(), e.getValue()[0]);
                }

                // Thứ tự xử lý: TH trước (phòng PM/TN khan hiếm) → LT của GV nhiều lớp nhất
                // trước
                List<GeneticTimetableScheduler.Gene> genesToProcess = new ArrayList<>(bestChromosome.getGenes());
                genesToProcess.sort((g1, g2) -> {
                        ClassSection s1 = sectionMap.get(g1.getSectionId());
                        ClassSection s2 = sectionMap.get(g2.getSectionId());
                        if (s1 == null || s2 == null)
                                return 0;

                        boolean th1 = s1.getSectionType() == ClassSection.SectionType.TH;
                        boolean th2 = s2.getSectionType() == ClassSection.SectionType.TH;
                        if (th1 && !th2)
                                return -1;
                        if (!th1 && th2)
                                return 1;

                        String lv1 = s1.getLecturer() != null ? s1.getLecturer().getName() : "";
                        String lv2 = s2.getLecturer() != null ? s2.getLecturer().getName() : "";
                        int load1 = lecturerRequired.getOrDefault(lv1, 0);
                        int load2 = lecturerRequired.getOrDefault(lv2, 0);
                        return Integer.compare(load2, load1);
                });

                for (GeneticTimetableScheduler.Gene gene : genesToProcess) {
                        ClassSection section = sectionMap.get(gene.getSectionId());
                        if (section == null) {
                                conflicts.add(String.format("Lỗi dữ liệu: section=%d", gene.getSectionId()));
                                continue;
                        }

                        Room room = roomMap.get(gene.getRoomId());
                        Shift shift = shiftMap.get(gene.getShiftId());
                        int day = gene.getDayOfWeek();

                        // Section-level day-shift tracking: tránh 1 section bị xếp 2 ca cùng ngày-ca
                        Set<String> secUsed = sectionUsedDayShifts.computeIfAbsent(section.getId(),
                                        k -> new HashSet<>());

                        // Kiểm tra slot gốc hợp lệ
                        boolean valid = false;
                        if (room != null && shift != null) {
                                String roomKey = room.getId() + "-" + day + "-" + shift.getId();
                                String lecturerKey = section.getLecturer() != null
                                                ? section.getLecturer().getId() + "-" + day + "-" + shift.getId()
                                                : null;
                                String secDayShiftKey = day + "-" + shift.getId();
                                valid = !usedRoomShifts.contains(roomKey)
                                                && (lecturerKey == null || !usedLecturerShifts.contains(lecturerKey))
                                                && !secUsed.contains(secDayShiftKey);
                        }

                        // Nếu conflict hoặc section trùng, tìm slot thay thế
                        if (!valid) {
                                Object[] alt = findAlternativeSlot(section, allRooms, allShifts,
                                                usedRoomShifts, usedLecturerShifts, secUsed);
                                if (alt != null) {
                                        room = (Room) alt[0];
                                        shift = (Shift) alt[1];
                                        day = (Integer) alt[2];
                                        valid = true;
                                }
                        }

                        if (!valid || room == null || shift == null) {
                                String sCode = section.getCode();
                                String reason = section.getLecturer() != null ? section.getLecturer().getName() : "—";
                                conflictCountBySection.merge(sCode, 1, (a, b) -> a + b);
                                conflictReasonBySection.putIfAbsent(sCode, reason);
                                conflictCount++;
                                continue;
                        }

                        String roomKey = room.getId() + "-" + day + "-" + shift.getId();
                        String lecturerKey = section.getLecturer() != null
                                        ? section.getLecturer().getId() + "-" + day + "-" + shift.getId()
                                        : null;
                        String secDayShiftKey = day + "-" + shift.getId();
                        usedRoomShifts.add(roomKey);
                        if (lecturerKey != null)
                                usedLecturerShifts.add(lecturerKey);
                        secUsed.add(secDayShiftKey);

                        TimeSlot firstTimeSlot = timeSlotByPeriod.get(shift.getStartPeriod());
                        TimetableEntry entry = TimetableEntry.builder()
                                        .classSection(section)
                                        .room(room)
                                        .shift(shift)
                                        .startPeriod(shift.getStartPeriod())
                                        .endPeriod(shift.getEndPeriod())
                                        .timeSlot(firstTimeSlot)
                                        .dayOfWeek(day)
                                        .semester(semester)
                                        .status(TimetableEntry.Status.DRAFT)
                                        .build();
                        entries.add(entry);
                }

                try {
                        timetableRepo.saveAll(entries);
                } catch (Exception e) {
                        conflicts.add("Lỗi khi lưu TKB: " + e.getMessage());
                        conflictCount++;
                }

                // Build danh sách xung đột đã gộp theo lớp
                conflictCountBySection.forEach((code, failedSessions) -> {
                        String gv = conflictReasonBySection.getOrDefault(code, "—");
                        int[] load = lecturerLoad.get(gv);
                        String overloadHint = (load != null && load[0] > load[1])
                                        ? String.format(" [GV quá tải: cần %d slot, có %d]", load[0], load[1])
                                        : "";
                        conflicts.add(String.format("Lớp %s: %d buổi không xếp được (GV '%s'%s)",
                                        code, failedSessions, gv, overloadHint));
                });
                overloadWarnings.forEach(w -> conflicts.add(0, w));

                Map<String, Object> response = new HashMap<>();
                response.put("assignedCount", entries.size());
                response.put("conflictCount", conflictCount);
                response.put("unscheduledSections", conflictCountBySection.size());
                response.put("algorithmConflicts", gaConflicts);
                response.put("overloadWarnings", overloadWarnings);
                response.put("conflicts", conflicts);
                response.put("fitness", result.bestFitness);
                response.put("algorithm", algo);
                response.put("message", String.format(
                                "Đã xếp %d buổi học. Không thể xếp: %d buổi (%d lớp bị ảnh hưởng). %s fitness: %.0f",
                                entries.size(), conflictCount, conflictCountBySection.size(), algo, result.bestFitness));
                return response;
        }

        /**
         * Tìm slot thay thế (room, shift, day) khi gene gốc bị conflict.
         * Trả về [Room, Shift, Integer day] hoặc null nếu không tìm được.
         */
        private Object[] findAlternativeSlot(ClassSection section, List<Room> rooms, List<Shift> shifts,
                        Set<String> usedRoomShifts, Set<String> usedLecturerShifts, Set<String> sectionUsedDayShifts) {
                Course course = section.getCourseOffering().getCourse();
                List<Room> suitableRooms;
                if (section.getSectionType() == ClassSection.SectionType.TH) {
                        String preferred = (course.getRequiredRoomType() != null && VALID_TH_ROOM_TYPES.contains(course.getRequiredRoomType()))
                                        ? course.getRequiredRoomType() : "PM";
                        // CHỈ dùng đúng loại: PM=phòng máy (lập trình), TN=thí nghiệm Hóa/Lý — không trộn
                        suitableRooms = rooms.stream().filter(r -> preferred.equals(r.getType())).collect(Collectors.toList());
                } else {
                        boolean isOnlineOnly = course.getLearningMethod() == Course.LearningMethod.ONLINE_ELEARNING;
                        String requiredRoomType = (isOnlineOnly && "ONLINE".equals(course.getRequiredRoomType())) ? "ONLINE" : "LT";
                        suitableRooms = rooms.stream().filter(r -> requiredRoomType.equals(r.getType())).collect(Collectors.toList());
                        if (suitableRooms.isEmpty())
                                suitableRooms = new ArrayList<>(rooms);
                }

                // Ca tối chỉ cho ONLINE_ELEARNING và ONLINE_COURSERA
                boolean eveningAllowed = isEveningAllowedForCourse(course);
                List<Shift> allowedShifts = shifts.stream()
                                .filter(s -> !isEveningShift(s) || eveningAllowed)
                                .collect(Collectors.toList());
                if (allowedShifts.isEmpty())
                        allowedShifts = new ArrayList<>(shifts);

                // Ưu tiên T2-T6, T7 cuối; tìm có hệ thống để tăng cơ hội tìm được slot
                List<Integer> days = Arrays.asList(2, 3, 4, 5, 6, 7);

                Long lecturerId = section.getLecturer() != null ? section.getLecturer().getId() : null;

                for (Integer day : days) {
                        for (Shift shift : allowedShifts) {
                                // Không cho section trùng ngày-ca (áp dụng khi section có 2 buổi/tuần)
                                String secDayShiftKey = day + "-" + shift.getId();
                                if (sectionUsedDayShifts != null && sectionUsedDayShifts.contains(secDayShiftKey))
                                        continue;

                                String lk = lecturerId != null ? lecturerId + "-" + day + "-" + shift.getId() : null;
                                if (lk != null && usedLecturerShifts.contains(lk))
                                        continue;
                                for (Room room : suitableRooms) {
                                        String rk = room.getId() + "-" + day + "-" + shift.getId();
                                        if (usedRoomShifts.contains(rk))
                                                continue;
                                        return new Object[] { room, shift, day };
                                }
                        }
                }
                return null;
        }

        /** Lấy TKB của một học kỳ */
        public List<TimetableEntry> getTimetableBySemester(Long semesterId) {
                return timetableRepo.findBySemesterId(semesterId);
        }

        /** Lấy TKB của một lớp học phần */
        public List<TimetableEntry> getTimetableBySection(Long sectionId) {
                return timetableRepo.findByClassSectionId(sectionId);
        }

        /** Lấy TKB của một lớp biên chế trong học kỳ */
        public List<TimetableEntry> getTimetableByAdminClass(Long semesterId, Long adminClassId) {
                return timetableRepo.findBySemesterAndAdminClass(semesterId, adminClassId);
        }

        /**
         * Cập nhật một entry TKB (chỉnh sửa thủ công).
         * Nhận shiftId thay vì timeSlotId - ca học là đơn vị xếp lịch.
         */
        @Transactional
        public TimetableEntry updateEntry(Long entryId, Long roomId, Long shiftId, Integer dayOfWeek) {
                TimetableEntry entry = timetableRepo.findById(entryId)
                                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy entry ID: " + entryId));

                if (entry.getStatus() == TimetableEntry.Status.CONFIRMED) {
                        throw new IllegalStateException("Không thể chỉnh sửa TKB đã xác nhận");
                }

                Room room = roomRepo.findById(roomId)
                                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phòng ID: " + roomId));
                Shift shift = shiftRepo.findById(shiftId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Không tìm thấy ca học ID: " + shiftId));

                Long semesterId = entry.getSemester().getId();
                Long currentEntryId = entry.getId();

                if (timetableRepo.existsByRoomAndShiftAndDay(roomId, dayOfWeek, shiftId, semesterId, currentEntryId)) {
                        throw new IllegalStateException(
                                        String.format("Phòng '%s' đã được sử dụng trong ca '%s' thứ %d",
                                                        room.getName(), shift.getName(), dayOfWeek));
                }

                Long lecturerId = entry.getClassSection().getLecturer() != null
                                ? entry.getClassSection().getLecturer().getId()
                                : null;
                if (lecturerId != null
                                && timetableRepo.existsByLecturerAndShiftAndDay(lecturerId, dayOfWeek, shiftId,
                                                semesterId, currentEntryId)) {
                        throw new IllegalStateException(
                                        String.format("Giảng viên đã có lớp trong ca '%s' thứ %d",
                                                        shift.getName(), dayOfWeek));
                }

                // Tìm TimeSlot đầu tiên của ca mới
                final int startPeriodVal = shift.getStartPeriod() != null ? shift.getStartPeriod() : 1;
                TimeSlot firstTimeSlot = timeSlotRepo.findAll().stream()
                                .filter(ts -> ts.getPeriodIndex() == startPeriodVal)
                                .findFirst().orElse(null);

                entry.setRoom(room);
                entry.setShift(shift);
                entry.setStartPeriod(shift.getStartPeriod());
                entry.setEndPeriod(shift.getEndPeriod());
                entry.setTimeSlot(firstTimeSlot);
                entry.setDayOfWeek(dayOfWeek);
                return timetableRepo.save(entry);
        }

        /** Xác nhận TKB (chuyển từ DRAFT sang CONFIRMED) */
        @Transactional
        public void confirmTimetable(Long semesterId) {
                List<TimetableEntry> entries = timetableRepo.findBySemesterId(semesterId).stream()
                                .filter(e -> e.getStatus() == TimetableEntry.Status.DRAFT)
                                .collect(Collectors.toList());

                for (TimetableEntry entry : entries) {
                        entry.setStatus(TimetableEntry.Status.CONFIRMED);
                }
                timetableRepo.saveAll(entries);
        }

        /**
         * Gợi ý các slot trống (phòng + ca học + thứ) không conflict khi chỉnh sửa thủ
         * công.
         * Trả về tối đa 10 gợi ý với đầy đủ startPeriod/endPeriod của ca.
         */
        public List<Map<String, Object>> getSuggestions(Long entryId) {
                TimetableEntry entry = timetableRepo.findById(entryId)
                                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy entry ID: " + entryId));

                ClassSection section = entry.getClassSection();
                Course course = section.getCourseOffering().getCourse();
                String requiredRoomType = determineRequiredRoomType(section, course);

                List<Room> suitableRooms = roomRepo.findAll().stream()
                                .filter(r -> requiredRoomType.equals(r.getType()))
                                .collect(Collectors.toList());

                boolean eveningAllowed = isEveningAllowedForCourse(course);
                List<Shift> allowedShifts = shiftRepo.findAll().stream()
                                .filter(shift -> !isEveningShift(shift) || eveningAllowed)
                                .collect(Collectors.toList());

                List<Integer> days = Arrays.asList(2, 3, 4, 5, 6, 7);
                Long semesterId = entry.getSemester().getId();
                Long lecturerId = section.getLecturer() != null ? section.getLecturer().getId() : null;

                List<Map<String, Object>> suggestions = new ArrayList<>();
                for (Room room : suitableRooms) {
                        for (Shift shift : allowedShifts) {
                                for (Integer day : days) {
                                        if (timetableRepo.existsByRoomAndShiftAndDay(
                                                        room.getId(), day, shift.getId(), semesterId, entryId))
                                                continue;
                                        if (lecturerId != null && timetableRepo.existsByLecturerAndShiftAndDay(
                                                        lecturerId, day, shift.getId(), semesterId, entryId))
                                                continue;

                                        Map<String, Object> s = new HashMap<>();
                                        s.put("roomId", room.getId());
                                        s.put("roomName", room.getName());
                                        s.put("shiftId", shift.getId());
                                        s.put("shiftName", shift.getName());
                                        s.put("startPeriod", shift.getStartPeriod());
                                        s.put("endPeriod", shift.getEndPeriod());
                                        s.put("dayOfWeek", day);
                                        s.put("dayName", DAY_NAMES.getOrDefault(day, "Thứ " + day));
                                        suggestions.add(s);

                                        if (suggestions.size() >= 10)
                                                return suggestions;
                                }
                        }
                }
                return suggestions;
        }

        /** Xóa một entry TKB */
        @Transactional
        public void deleteEntry(Long entryId) {
                TimetableEntry entry = timetableRepo.findById(entryId)
                                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy entry ID: " + entryId));

                if (entry.getStatus() == TimetableEntry.Status.CONFIRMED) {
                        throw new IllegalStateException("Không thể xóa TKB đã xác nhận");
                }
                timetableRepo.delete(entry);
        }

        /**
         * Xuất TKB của một học kỳ ra file Excel (.xlsx).
         * Sắp xếp theo: Thứ → Ca (startPeriod) → Phòng → Mã lớp học phần.
         * Bao gồm đầy đủ thông tin: môn học, lớp, giảng viên, phòng, ca, trạng thái.
         */
        public ByteArrayInputStream exportTimetableToExcel(Long semesterId) throws IOException {
                Semester semester = semesterRepo.findById(semesterId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Không tìm thấy học kỳ ID: " + semesterId));

                List<TimetableEntry> entries = timetableRepo.findBySemesterId(semesterId);

                // Sắp xếp: Thứ → Ca (startPeriod) → Phòng tên → Mã lớp
                entries.sort(Comparator
                                .comparingInt(TimetableEntry::getDayOfWeek)
                                .thenComparingInt(e -> e.getShift() != null && e.getShift().getStartPeriod() != null
                                                ? e.getShift().getStartPeriod()
                                                : 0)
                                .thenComparing(e -> e.getRoom() != null ? e.getRoom().getName() : "")
                                .thenComparing(e -> e.getClassSection() != null ? e.getClassSection().getCode() : ""));

                try (Workbook workbook = new XSSFWorkbook();
                                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

                        // ── Styles ────────────────────────────────────────────────────────────────
                        CellStyle titleStyle = workbook.createCellStyle();
                        Font titleFont = workbook.createFont();
                        titleFont.setBold(true);
                        titleFont.setFontHeightInPoints((short) 14);
                        titleStyle.setFont(titleFont);
                        titleStyle.setAlignment(HorizontalAlignment.CENTER);

                        CellStyle headerStyle = workbook.createCellStyle();
                        Font headerFont = workbook.createFont();
                        headerFont.setBold(true);
                        headerFont.setColor(IndexedColors.WHITE.getIndex());
                        headerStyle.setFont(headerFont);
                        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
                        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                        headerStyle.setAlignment(HorizontalAlignment.CENTER);
                        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
                        headerStyle.setBorderTop(BorderStyle.THIN);
                        headerStyle.setBorderBottom(BorderStyle.THIN);
                        headerStyle.setBorderLeft(BorderStyle.THIN);
                        headerStyle.setBorderRight(BorderStyle.THIN);
                        headerStyle.setWrapText(true);

                        CellStyle dataStyle = workbook.createCellStyle();
                        dataStyle.setBorderTop(BorderStyle.THIN);
                        dataStyle.setBorderBottom(BorderStyle.THIN);
                        dataStyle.setBorderLeft(BorderStyle.THIN);
                        dataStyle.setBorderRight(BorderStyle.THIN);
                        dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);

                        CellStyle dataCenter = workbook.createCellStyle();
                        dataCenter.cloneStyleFrom(dataStyle);
                        dataCenter.setAlignment(HorizontalAlignment.CENTER);

                        CellStyle confirmedStyle = workbook.createCellStyle();
                        confirmedStyle.cloneStyleFrom(dataCenter);
                        confirmedStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
                        confirmedStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                        Font confirmedFont = workbook.createFont();
                        confirmedFont.setBold(true);
                        confirmedFont.setColor(IndexedColors.DARK_GREEN.getIndex());
                        confirmedStyle.setFont(confirmedFont);

                        CellStyle draftStyle = workbook.createCellStyle();
                        draftStyle.cloneStyleFrom(dataCenter);
                        draftStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
                        draftStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

                        CellStyle dayGroupStyle = workbook.createCellStyle();
                        Font dayFont = workbook.createFont();
                        dayFont.setBold(true);
                        dayGroupStyle.setFont(dayFont);
                        dayGroupStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
                        dayGroupStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                        dayGroupStyle.setBorderTop(BorderStyle.THIN);
                        dayGroupStyle.setBorderBottom(BorderStyle.THIN);
                        dayGroupStyle.setBorderLeft(BorderStyle.THIN);
                        dayGroupStyle.setBorderRight(BorderStyle.THIN);
                        dayGroupStyle.setAlignment(HorizontalAlignment.CENTER);
                        dayGroupStyle.setVerticalAlignment(VerticalAlignment.CENTER);

                        // ── Sheet 1: TKB đầy đủ ─────────────────────────────────────────────────
                        Sheet sheet = workbook.createSheet("Thời Khóa Biểu");

                        // Dòng tiêu đề chính
                        Row titleRow = sheet.createRow(0);
                        titleRow.setHeightInPoints(28);
                        Cell titleCell = titleRow.createCell(0);
                        titleCell.setCellValue("THỜI KHÓA BIỂU - " + semester.getName().toUpperCase());
                        titleCell.setCellStyle(titleStyle);
                        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 16));

                        // Dòng thông tin học kỳ
                        Row infoRow = sheet.createRow(1);
                        Cell infoCell = infoRow.createCell(0);
                        String semesterInfo = String.format("Học kỳ: %s | Từ: %s đến: %s | Tổng số buổi: %d",
                                        semester.getName(),
                                        semester.getStartDate(),
                                        semester.getEndDate(),
                                        entries.size());
                        infoCell.setCellValue(semesterInfo);
                        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 16));

                        // Header row (dòng 2, index 2)
                        String[] headers = {
                                        "STT", "Thứ", "Ca học", "Tiết BĐ", "Tiết KT",
                                        "Mã học phần", "Tên học phần", "Số TC",
                                        "Loại lớp", "Mã lớp HP", "Lớp biên chế", "Sĩ số DK", "Giảng viên",
                                        "Phòng học", "Loại phòng", "Hình thức", "Trạng thái"
                        };
                        Row headerRow = sheet.createRow(2);
                        headerRow.setHeightInPoints(36);
                        for (int i = 0; i < headers.length; i++) {
                                Cell cell = headerRow.createCell(i);
                                cell.setCellValue(headers[i]);
                                cell.setCellStyle(headerStyle);
                        }

                        // Độ rộng cột (đơn vị: 1/256 ký tự)
                        int[] colWidths = { 8, 10, 14, 10, 10, 18, 36, 8, 12, 20, 24, 10, 28, 14, 14, 20, 14 };
                        for (int i = 0; i < colWidths.length; i++) {
                                sheet.setColumnWidth(i, colWidths[i] * 256);
                        }

                        // ── Dữ liệu ──────────────────────────────────────────────────────────────
                        int rowNum = 3;
                        int stt = 1;
                        int prevDay = -1;

                        for (TimetableEntry entry : entries) {
                                ClassSection section = entry.getClassSection();
                                Course course = section != null && section.getCourseOffering() != null
                                                ? section.getCourseOffering().getCourse()
                                                : null;
                                Lecturer lecturer = section != null ? section.getLecturer() : null;
                                Room room = entry.getRoom();
                                Shift shift = entry.getShift();
                                int day = entry.getDayOfWeek();

                                // Dòng nhóm theo Thứ (khi sang thứ mới)
                                if (day != prevDay) {
                                        Row dayRow = sheet.createRow(rowNum++);
                                        dayRow.setHeightInPoints(20);
                                        Cell dayCell = dayRow.createCell(0);
                                        dayCell.setCellValue("▶ " + DAY_NAMES.getOrDefault(day, "Thứ " + day));
                                        dayCell.setCellStyle(dayGroupStyle);
                                        for (int c = 1; c < headers.length; c++) {
                                                dayRow.createCell(c).setCellStyle(dayGroupStyle);
                                        }
                                        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 16));
                                        prevDay = day;
                                }

                                Row row = sheet.createRow(rowNum++);
                                row.setHeightInPoints(18);

                                boolean confirmed = entry.getStatus() == TimetableEntry.Status.CONFIRMED;

                                // STT
                                createStyledCell(row, 0, String.valueOf(stt++), dataCenter);
                                // Thứ
                                createStyledCell(row, 1, DAY_NAMES.getOrDefault(day, "Thứ " + day), dataCenter);
                                // Ca học
                                createStyledCell(row, 2, shift != null ? shift.getName() : "—", dataCenter);
                                // Tiết bắt đầu
                                createStyledCell(row, 3,
                                                entry.getStartPeriod() != null ? String.valueOf(entry.getStartPeriod())
                                                                : "—",
                                                dataCenter);
                                // Tiết kết thúc
                                createStyledCell(row, 4,
                                                entry.getEndPeriod() != null ? String.valueOf(entry.getEndPeriod())
                                                                : "—",
                                                dataCenter);
                                // Mã học phần
                                createStyledCell(row, 5, course != null ? course.getCode() : "—", dataStyle);
                                // Tên học phần
                                createStyledCell(row, 6, course != null ? course.getName() : "—", dataStyle);
                                // Số tín chỉ
                                createStyledCell(row, 7,
                                                course != null && course.getCredits() != null
                                                                ? String.valueOf(course.getCredits().intValue())
                                                                : "—",
                                                dataCenter);
                                // Loại lớp (LT / TH)
                                createStyledCell(row, 8,
                                                section != null && section.getSectionType() != null
                                                                ? section.getSectionType().name()
                                                                : "—",
                                                dataCenter);
                                // Mã lớp học phần
                                createStyledCell(row, 9, section != null ? section.getCode() : "—", dataStyle);
                                // Lớp biên chế (nhiều lớp → nối bằng ", ")
                                String adminClassNames = (section != null && section.getAdministrativeClasses() != null
                                                && !section.getAdministrativeClasses().isEmpty())
                                                                ? section.getAdministrativeClasses().stream()
                                                                                .map(ac -> ac.getCode())
                                                                                .sorted()
                                                                                .collect(java.util.stream.Collectors
                                                                                                .joining(", "))
                                                                : "—";
                                createStyledCell(row, 10, adminClassNames, dataStyle);
                                // Sĩ số dự kiến
                                String expectedSv = section != null && section.getExpectedStudentCount() != null
                                                ? String.valueOf(section.getExpectedStudentCount())
                                                : "—";
                                createStyledCell(row, 11, expectedSv, dataCenter);
                                // Giảng viên
                                createStyledCell(row, 12, lecturer != null ? lecturer.getName() : "— (Chưa phân công)",
                                                dataStyle);
                                // Phòng học
                                createStyledCell(row, 13, room != null ? room.getName() : "—", dataCenter);
                                // Loại phòng
                                createStyledCell(row, 14, room != null ? room.getType() : "—", dataCenter);
                                // Hình thức học
                                String method = course != null && course.getLearningMethod() != null
                                                ? LEARNING_METHOD_NAMES.getOrDefault(course.getLearningMethod().name(),
                                                                course.getLearningMethod().name())
                                                : "—";
                                createStyledCell(row, 15, method, dataCenter);
                                // Trạng thái
                                Cell statusCell = row.createCell(16);
                                statusCell.setCellValue(confirmed ? "Đã xác nhận" : "Bản nháp");
                                statusCell.setCellStyle(confirmed ? confirmedStyle : draftStyle);
                        }

                        // Freeze pane: giữ cố định 3 dòng đầu (tiêu đề + header)
                        sheet.createFreezePane(0, 3);

                        // ── Sheet 2: Thống kê tổng hợp ───────────────────────────────────────────
                        Sheet summarySheet = workbook.createSheet("Thống Kê");

                        CellStyle summaryHeaderStyle = workbook.createCellStyle();
                        Font sumHeaderFont = workbook.createFont();
                        sumHeaderFont.setBold(true);
                        sumHeaderFont.setColor(IndexedColors.WHITE.getIndex());
                        summaryHeaderStyle.setFont(sumHeaderFont);
                        summaryHeaderStyle.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
                        summaryHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                        summaryHeaderStyle.setBorderTop(BorderStyle.THIN);
                        summaryHeaderStyle.setBorderBottom(BorderStyle.THIN);
                        summaryHeaderStyle.setBorderLeft(BorderStyle.THIN);
                        summaryHeaderStyle.setBorderRight(BorderStyle.THIN);
                        summaryHeaderStyle.setAlignment(HorizontalAlignment.CENTER);

                        CellStyle summaryDataStyle = workbook.createCellStyle();
                        summaryDataStyle.setBorderTop(BorderStyle.THIN);
                        summaryDataStyle.setBorderBottom(BorderStyle.THIN);
                        summaryDataStyle.setBorderLeft(BorderStyle.THIN);
                        summaryDataStyle.setBorderRight(BorderStyle.THIN);

                        CellStyle summaryNumStyle = workbook.createCellStyle();
                        summaryNumStyle.cloneStyleFrom(summaryDataStyle);
                        summaryNumStyle.setAlignment(HorizontalAlignment.CENTER);

                        // Thống kê theo giảng viên
                        Row sumTitle = summarySheet.createRow(0);
                        sumTitle.setHeightInPoints(22);
                        Cell sumTitleCell = sumTitle.createCell(0);
                        sumTitleCell.setCellValue("THỐNG KÊ TẢI GIẢNG VIÊN - " + semester.getName());
                        sumTitleCell.setCellStyle(titleStyle);
                        summarySheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));

                        Row sumHeader = summarySheet.createRow(2);
                        sumHeader.setHeightInPoints(24);
                        String[] sumHeaders = { "STT", "Giảng viên", "Số buổi LT", "Số buổi TH", "Tổng buổi" };
                        for (int i = 0; i < sumHeaders.length; i++) {
                                Cell c = sumHeader.createCell(i);
                                c.setCellValue(sumHeaders[i]);
                                c.setCellStyle(summaryHeaderStyle);
                        }
                        summarySheet.setColumnWidth(0, 8 * 256);
                        summarySheet.setColumnWidth(1, 32 * 256);
                        summarySheet.setColumnWidth(2, 14 * 256);
                        summarySheet.setColumnWidth(3, 14 * 256);
                        summarySheet.setColumnWidth(4, 14 * 256);

                        // Tập hợp dữ liệu: lecturerName → [ltCount, thCount]
                        Map<String, int[]> lecturerStats = new LinkedHashMap<>();
                        for (TimetableEntry entry : entries) {
                                ClassSection section = entry.getClassSection();
                                if (section == null)
                                        continue;
                                String lecName = section.getLecturer() != null ? section.getLecturer().getName()
                                                : "— Chưa phân công";
                                lecturerStats.computeIfAbsent(lecName, k -> new int[2]);
                                boolean isTH = section.getSectionType() == ClassSection.SectionType.TH;
                                if (isTH)
                                        lecturerStats.get(lecName)[1]++;
                                else
                                        lecturerStats.get(lecName)[0]++;
                        }

                        // Sắp xếp giảng viên theo tổng buổi giảm dần
                        List<Map.Entry<String, int[]>> sortedLecturers = new ArrayList<>(lecturerStats.entrySet());
                        sortedLecturers.sort((a, b) -> (b.getValue()[0] + b.getValue()[1])
                                        - (a.getValue()[0] + a.getValue()[1]));

                        int sumRow = 3;
                        int sumStt = 1;
                        for (Map.Entry<String, int[]> lec : sortedLecturers) {
                                Row r = summarySheet.createRow(sumRow++);
                                r.setHeightInPoints(18);
                                createStyledCell(r, 0, String.valueOf(sumStt++), summaryNumStyle);
                                createStyledCell(r, 1, lec.getKey(), summaryDataStyle);
                                createStyledCell(r, 2, String.valueOf(lec.getValue()[0]), summaryNumStyle);
                                createStyledCell(r, 3, String.valueOf(lec.getValue()[1]), summaryNumStyle);
                                createStyledCell(r, 4, String.valueOf(lec.getValue()[0] + lec.getValue()[1]),
                                                summaryNumStyle);
                        }

                        // Dòng tổng cộng
                        int totalLt = entries.stream()
                                        .filter(e -> e.getClassSection() != null
                                                        && e.getClassSection()
                                                                        .getSectionType() == ClassSection.SectionType.LT)
                                        .mapToInt(e -> 1).sum();
                        int totalTh = entries.size() - totalLt;
                        Row totalRow = summarySheet.createRow(sumRow + 1);
                        totalRow.setHeightInPoints(20);
                        CellStyle totalStyle = workbook.createCellStyle();
                        Font totalFont = workbook.createFont();
                        totalFont.setBold(true);
                        totalStyle.setFont(totalFont);
                        totalStyle.setBorderTop(BorderStyle.MEDIUM);
                        totalStyle.setBorderBottom(BorderStyle.MEDIUM);
                        totalStyle.setBorderLeft(BorderStyle.THIN);
                        totalStyle.setBorderRight(BorderStyle.THIN);
                        totalStyle.setAlignment(HorizontalAlignment.CENTER);
                        createStyledCell(totalRow, 0, "Tổng", totalStyle);
                        createStyledCell(totalRow, 1, entries.size() + " buổi học", totalStyle);
                        createStyledCell(totalRow, 2, String.valueOf(totalLt), totalStyle);
                        createStyledCell(totalRow, 3, String.valueOf(totalTh), totalStyle);
                        createStyledCell(totalRow, 4, String.valueOf(entries.size()), totalStyle);
                        summarySheet.addMergedRegion(new CellRangeAddress(sumRow + 1, sumRow + 1, 0, 1));

                        workbook.write(out);
                        return new ByteArrayInputStream(out.toByteArray());
                }
        }

        /** Helper: tạo cell với nội dung text và style cho sẵn */
        private void createStyledCell(Row row, int col, String value, CellStyle style) {
                Cell cell = row.createCell(col);
                cell.setCellValue(value != null ? value : "");
                cell.setCellStyle(style);
        }

        private static boolean isOfflineCourse(Course course) {
                return course.getLearningMethod() == Course.LearningMethod.OFFLINE
                                || course.getLearningMethod() == Course.LearningMethod.HYBRID
                                || course.getLearningMethod() == Course.LearningMethod.ONLINE_COURSERA;
        }

        /** Ca tối chỉ cho học phần ONLINE / E-learning / Coursera */
        private static boolean isEveningAllowedForCourse(Course course) {
                return course.getLearningMethod() == Course.LearningMethod.ONLINE_ELEARNING
                                || course.getLearningMethod() == Course.LearningMethod.ONLINE_COURSERA;
        }

        /** Ca tối: startPeriod >= cấu hình (mặc định 13). Chỉ ONLINE_ELEARNING, ONLINE_COURSERA được xếp ca tối. */
        private boolean isEveningShift(Shift shift) {
                return shift.getStartPeriod() != null && shift.getStartPeriod() >= eveningShiftStartPeriodFrom;
        }

        private static String determineRequiredRoomType(ClassSection section, Course course) {
                String rt = course.getRequiredRoomType();
                if (section.getSectionType() == ClassSection.SectionType.TH) {
                        return (rt != null && VALID_TH_ROOM_TYPES.contains(rt)) ? rt : "PM";
                }
                boolean isOnlineOnly = course.getLearningMethod() == Course.LearningMethod.ONLINE_ELEARNING;
                return (isOnlineOnly && "ONLINE".equals(rt)) ? "ONLINE" : "LT";
        }
}
