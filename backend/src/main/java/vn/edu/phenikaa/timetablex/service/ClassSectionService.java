package vn.edu.phenikaa.timetablex.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.phenikaa.timetablex.algorithm.GeneticTimetableScheduler;
import vn.edu.phenikaa.timetablex.entity.AdministrativeClass;
import vn.edu.phenikaa.timetablex.entity.ClassSection;
import vn.edu.phenikaa.timetablex.entity.Course;
import vn.edu.phenikaa.timetablex.entity.CourseOffering;
import vn.edu.phenikaa.timetablex.entity.Curriculum;
import vn.edu.phenikaa.timetablex.entity.CurriculumDetail;
import vn.edu.phenikaa.timetablex.entity.Faculty;
import vn.edu.phenikaa.timetablex.entity.Lecturer;
import vn.edu.phenikaa.timetablex.repository.AdministrativeClassRepository;
import vn.edu.phenikaa.timetablex.repository.ClassSectionRepository;
import vn.edu.phenikaa.timetablex.repository.TimetableEntryRepository;
import vn.edu.phenikaa.timetablex.repository.CourseOfferingRepository;
import vn.edu.phenikaa.timetablex.repository.CurriculumRepository;
import vn.edu.phenikaa.timetablex.repository.FacultyRepository;
import vn.edu.phenikaa.timetablex.repository.LecturerRepository;
import vn.edu.phenikaa.timetablex.repository.SemesterRepository;
import vn.edu.phenikaa.timetablex.dto.TeachingLoadDto;
import vn.edu.phenikaa.timetablex.entity.Course;
import vn.edu.phenikaa.timetablex.entity.Faculty;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ClassSectionService {

    @Autowired
    private ClassSectionRepository sectionRepo;
    @Autowired
    private CourseOfferingRepository offeringRepo;
    @Autowired
    private SemesterRepository semesterRepo;
    @Autowired
    private LecturerRepository lecturerRepo;
    @Autowired
    private FacultyRepository facultyRepo;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private AdministrativeClassRepository adminClassRepo;
    @Autowired
    private TimetableEntryRepository timetableEntryRepo;
    @Autowired
    private CurriculumRepository curriculumRepo;

    public List<ClassSection> getBySemester(Long semesterId) {
        return sectionRepo.findByCourseOffering_Semester_Id(semesterId);
    }

    public List<ClassSection> getBySemesterAndFaculty(Long semesterId, Long facultyId) {
        if (facultyId == null)
            return getBySemester(semesterId);
        return sectionRepo.findBySemesterAndFaculty(semesterId, facultyId);
    }

    public List<ClassSection> getByCourseOffering(Long offeringId) {
        return sectionRepo.findByCourseOffering_Id(offeringId);
    }

    /** Sĩ số tối đa mỗi lớp LT (phòng học lớn: 40–80 SV) */
    private static final int MAX_LT_STUDENTS = 80;

    /** Sĩ số tối đa mỗi lớp TH (phòng thực hành nhỏ: 20–45 SV) */
    private static final int MAX_TH_STUDENTS = 45;

    /** Sĩ số tối thiểu mỗi lớp TH — dưới ngưỡng này lớp không đủ quy mô (gộp với lớp khác) */
    private static final int MIN_TH_STUDENTS = 20;

    /** Sĩ số tối thiểu mỗi lớp LT — tương tự, tránh lớp quá nhỏ */
    private static final int MIN_LT_STUDENTS = 40;

    /**
     * Sinh tự động các Lớp học phần từ danh sách CourseOffering đã APPROVED.
     *
     * Quy tắc gán lớp biên chế (LT-led splitting):
     * - LT: mỗi lớp nhận nhóm BC ≤ {@value MAX_LT_STUDENTS} SV, ≥ {@value MIN_LT_STUDENTS} SV (phòng học lớn)
     * - TH: bám theo LT — mỗi nhóm BC của LT được chia tiếp thành các lớp TH
     *       (mỗi lớp {@value MIN_TH_STUDENTS}–{@value MAX_TH_STUDENTS} SV). Lớp TH < {@value MIN_TH_STUDENTS} SV
     *       sẽ được gộp với lớp khác để đủ quy mô.
     * - Dùng thuật toán Greedy Load-Balancing để cân bằng sĩ số.
     */
    /**
     * @param forceRegenerate nếu true: xóa hết lớp HP + TKB của học kỳ rồi sinh lại từ đầu
     */
    @Transactional
    public int generateFromApprovedOfferings(Long semesterId, boolean forceRegenerate) {
        semesterRepo.findById(semesterId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy học kỳ ID: " + semesterId));

        List<CourseOffering> approved = offeringRepo.findBySemesterIdAndStatus(semesterId,
                CourseOffering.Status.APPROVED);
        List<ClassSection> existing = sectionRepo.findByCourseOffering_Semester_Id(semesterId);

        if (forceRegenerate && !existing.isEmpty()) {
            timetableEntryRepo.deleteAllBySemesterId(semesterId);
            sectionRepo.deleteAll(existing);
            existing = List.of();
        }

        Set<Long> offeringIdsWithSections = existing.stream()
                .map(s -> s.getCourseOffering().getId())
                .collect(Collectors.toSet());

        // Cache: facultyId → danh sách lớp biên chế của khoa đó (tránh query nhiều lần)
        Map<Long, List<AdministrativeClass>> adminClassesByFaculty = new HashMap<>();

        List<ClassSection> toCreate = new ArrayList<>();
        for (CourseOffering o : approved) {
            if (offeringIdsWithSections.contains(o.getId()))
                continue;

            Course course = o.getCourse();
            String courseCode = course != null && course.getCode() != null
                    ? course.getCode()
                    : "HP" + o.getId();

            // Lấy lớp biên chế: khoa phụ trách + các khoa dùng chung môn (nếu có)
            Set<Long> facultyIds = new HashSet<>();
            if (o.getFaculty() != null) facultyIds.add(o.getFaculty().getId());
            if (o.getCourse() != null && o.getCourse().getSharedFaculties() != null) {
                o.getCourse().getSharedFaculties().stream()
                        .map(Faculty::getId)
                        .forEach(facultyIds::add);
            }
            List<AdministrativeClass> facultyAdminClasses = new ArrayList<>();
            for (Long fid : facultyIds) {
                List<AdministrativeClass> acs = adminClassesByFaculty.computeIfAbsent(fid,
                        id -> adminClassRepo.findByMajor_Faculty_Id(id));
                for (AdministrativeClass ac : acs) {
                    if (!facultyAdminClasses.contains(ac))
                        facultyAdminClasses.add(ac);
                }
            }

            // Giới hạn lớp biên chế theo CTĐT: chỉ những ngành có CTĐT (theo Khóa) chứa học phần này
            Set<Long> allowedMajorIds = new HashSet<>();
            if (course != null) {
                String cohortCodeForCurr = null;
                if (o.getCohortRef() != null && o.getCohortRef().getCode() != null) {
                    cohortCodeForCurr = o.getCohortRef().getCode();
                } else if (o.getCohort() != null && !o.getCohort().isBlank()) {
                    cohortCodeForCurr = o.getCohort().trim();
                }
                if (cohortCodeForCurr != null && !cohortCodeForCurr.isBlank()) {
                    List<Curriculum> curriculums = curriculumRepo.findByCohort(cohortCodeForCurr);
                    for (Curriculum c : curriculums) {
                        if (c.getDetails() == null || c.getMajor() == null || c.getMajor().getId() == null) continue;
                        boolean containsCourse = c.getDetails().stream()
                                .map(CurriculumDetail::getCourse)
                                .filter(Objects::nonNull)
                                .anyMatch(dc -> dc.getId().equals(course.getId()));
                        if (containsCourse) {
                            allowedMajorIds.add(c.getMajor().getId());
                        }
                    }
                }
            }
            if (!allowedMajorIds.isEmpty()) {
                facultyAdminClasses = facultyAdminClasses.stream()
                        .filter(ac -> ac.getMajor() != null && allowedMajorIds.contains(ac.getMajor().getId()))
                        .toList();
            }

            // Lọc lớp biên chế theo Khóa của CourseOffering (nếu có) để sinh lớp theo từng khóa riêng
            String cohortCode = null;
            if (o.getCohortRef() != null && o.getCohortRef().getCode() != null) {
                cohortCode = o.getCohortRef().getCode();
            } else if (o.getCohort() != null && !o.getCohort().isBlank()) {
                cohortCode = o.getCohort().trim();
            }
            if (cohortCode != null && !cohortCode.isBlank()) {
                String finalCohortCode = cohortCode;
                facultyAdminClasses = facultyAdminClasses.stream()
                        .filter(ac -> {
                            String acCode = null;
                            if (ac.getCohortRef() != null && ac.getCohortRef().getCode() != null) {
                                acCode = ac.getCohortRef().getCode();
                            } else if (ac.getCohort() != null && !ac.getCohort().isBlank()) {
                                acCode = ac.getCohort().trim();
                            }
                            return finalCohortCode.equalsIgnoreCase(acCode);
                        })
                        .toList();
            }

            int lt = o.getTheoryClassCount() != null ? o.getTheoryClassCount() : 0;
            int th = o.getPracticeClassCount() != null ? o.getPracticeClassCount() : 0;

            // ── LT: chia BC theo MAX_LT_STUDENTS (mỗi lớp ≤ 80 SV) ──────────────────
            List<SliceWithCount> ltSlices = lt > 0 && !facultyAdminClasses.isEmpty()
                    ? splitAdminClassesWithMax(facultyAdminClasses, MAX_LT_STUDENTS)
                    : List.of();
            ltSlices = mergeSmallSlices(ltSlices, MIN_LT_STUDENTS, MAX_LT_STUDENTS);

            // ltCount = số lớp thực tế sẽ tạo:
            // ưu tiên số slice ĐÃ gán BC (tránh tạo lớp trống);
            // nếu không có BC nào thì dùng số lớp theo P.ĐT
            int ltCount = !ltSlices.isEmpty() ? ltSlices.size() : lt;
            // Nếu P.ĐT đặt nhiều hơn số slice, tạo thêm lớp rỗng (hiếm gặp, đư phòng)
            if (lt > ltSlices.size() && !ltSlices.isEmpty()) {
                List<SliceWithCount> extended = new ArrayList<>(ltSlices);
                while (extended.size() < lt)
                    extended.add(new SliceWithCount(new HashSet<>(), 0));
                ltSlices = extended;
                ltCount = lt;
            }
            // Khi không có BC: vẫn tạo đủ số lớp theo P.ĐT (để gán thủ công sau)
            if (lt > 0 && ltSlices.isEmpty()) {
                List<SliceWithCount> extended = new ArrayList<>();
                while (extended.size() < lt)
                    extended.add(new SliceWithCount(new HashSet<>(), 0));
                ltSlices = extended;
                ltCount = lt;
            }

            // ── TH: bám theo LT — mỗi nhóm LT chia tiếp thành các lớp TH (≤45 SV) ───
            List<SliceWithCount> thSlices = new ArrayList<>();
            if (th > 0) {
                if (!facultyAdminClasses.isEmpty()) {
                    if (!ltSlices.isEmpty()) {
                        for (SliceWithCount ltSwc : ltSlices) {
                            List<AdministrativeClass> ltList = ltSwc.adminClasses().stream()
                                    .filter(ac -> ac.getStudentCount() != null && ac.getStudentCount() > 0)
                                    .collect(Collectors.toCollection(ArrayList::new));
                            if (!ltList.isEmpty())
                                thSlices.addAll(splitAdminClassesWithMax(ltList, MAX_TH_STUDENTS));
                        }
                        if (thSlices.size() < th) {
                            int total = facultyAdminClasses.stream()
                                    .mapToInt(ac -> ac.getStudentCount() != null ? ac.getStudentCount() : 0)
                                    .sum();
                            int maxPerBin = total > 0 ? Math.min(MAX_TH_STUDENTS, Math.max(1, (int) Math.ceil((double) total / th))) : MAX_TH_STUDENTS;
                            thSlices = splitAdminClassesWithMax(facultyAdminClasses, maxPerBin);
                        }
                    } else {
                        thSlices = splitAdminClassesWithMax(facultyAdminClasses, MAX_TH_STUDENTS);
                    }
                    thSlices = mergeSmallSlices(thSlices, MIN_TH_STUDENTS, MAX_TH_STUDENTS);
                } else {
                    for (int i = 0; i < th; i++)
                        thSlices.add(new SliceWithCount(new HashSet<>(), 0));
                }
            }
            // thCount: ưu tiên số slice thực tế đã gán BC, không đồng thời tạo lớp trống dư thừa
            int thCount = !thSlices.isEmpty() ? thSlices.size() : th;
            if (th > thSlices.size() && !thSlices.isEmpty()) {
                List<SliceWithCount> extended = new ArrayList<>(thSlices);
                while (extended.size() < th)
                    extended.add(new SliceWithCount(new HashSet<>(), 0));
                thSlices = extended;
                thCount = th;
            }

            // ── Lớp LT (40–80 SV/lớp) ────────────────────────────────────────────────
            for (int i = 1; i <= ltCount; i++) {
                String code = String.format("%s-LT%02d", courseCode, i);
                if (!sectionRepo.existsByCode(code)) {
                    SliceWithCount swc = (i - 1) < ltSlices.size() ? ltSlices.get(i - 1) : new SliceWithCount(new HashSet<>(), 0);
                    toCreate.add(ClassSection.builder()
                            .code(code)
                            .sectionType(ClassSection.SectionType.LT)
                            .sectionIndex(i)
                            .courseOffering(o)
                            .administrativeClasses(swc.adminClasses())
                            .expectedStudentCount(swc.expectedCount() > 0 ? swc.expectedCount() : null)
                            .build());
                }
            }

            // ── Lớp TH (20–45 SV/lớp) ────────────────────────────────────────────────
            for (int i = 1; i <= thCount; i++) {
                String code = String.format("%s-TH%02d", courseCode, i);
                if (!sectionRepo.existsByCode(code)) {
                    SliceWithCount swc = (i - 1) < thSlices.size() ? thSlices.get(i - 1) : new SliceWithCount(new HashSet<>(), 0);
                    toCreate.add(ClassSection.builder()
                            .code(code)
                            .sectionType(ClassSection.SectionType.TH)
                            .sectionIndex(i)
                            .courseOffering(o)
                            .administrativeClasses(swc.adminClasses())
                            .expectedStudentCount(swc.expectedCount() > 0 ? swc.expectedCount() : null)
                            .build());
                }
            }
        }

        sectionRepo.saveAll(toCreate);
        return toCreate.size();
    }

    @Transactional
    public ClassSection assignLecturer(Long sectionId, Long lecturerId, Boolean skipAssignment) {
        ClassSection section = sectionRepo.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lớp học phần ID: " + sectionId));
        if (Boolean.TRUE.equals(skipAssignment)) {
            section.setLecturer(null);
            section.setSkipAssignment(true);
        } else {
            section.setSkipAssignment(false);
            if (lecturerId != null) {
                Lecturer lecturer = lecturerRepo.findById(lecturerId)
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giảng viên ID: " + lecturerId));
                section.setLecturer(lecturer);
                section.setNeedsSupport(false);
                section.setSupportRequestComment(null);
            } else {
                section.setLecturer(null);
            }
        }
        return sectionRepo.save(section);
    }

    public boolean isFacultyAllowedSkipAssignment(Long facultyId) {
        if (facultyId == null)
            return false;
        return Boolean.TRUE.equals(
                facultyRepo.findById(facultyId).map(f -> f.getAllowSkipAssignment()).orElse(false));
    }

    /**
     * Tự động phân công giảng viên dựa trên: Khoa + Chuyên môn (ma trận môn) + Cân
     * bằng tải.
     * Chỉ gán các lớp chưa có giảng viên và chưa bỏ qua.
     * Sau khi chạy vẫn có thể chỉnh sửa thủ công.
     */
    @Transactional
    public int autoAssign(Long semesterId, Long facultyId) {
        List<ClassSection> sections = facultyId != null
                ? sectionRepo.findBySemesterAndFaculty(semesterId, facultyId)
                : sectionRepo.findByCourseOffering_Semester_Id(semesterId);

        List<ClassSection> toAssign = sections.stream()
                .filter(s -> s.getLecturer() == null && !Boolean.TRUE.equals(s.getSkipAssignment()))
                .toList();

        // Thu thập tất cả faculty IDs: Khoa chủ quản + Khoa dùng chung
        // (sharedFaculties)
        Set<Long> allFacultyIds = new HashSet<>();
        for (ClassSection s : toAssign) {
            allFacultyIds.add(s.getCourseOffering().getFaculty().getId());
            if (s.getCourseOffering().getCourse().getSharedFaculties() != null) {
                s.getCourseOffering().getCourse().getSharedFaculties().stream()
                        .map(f -> f.getId())
                        .forEach(allFacultyIds::add);
            }
        }
        if (facultyId != null)
            allFacultyIds.add(facultyId);
        List<Lecturer> lecturers = allFacultyIds.isEmpty() ? List.of()
                : lecturerRepo.findByFaculty_IdInOrderByName(new ArrayList<>(allFacultyIds));

        Map<Long, Long> load = sections.stream()
                .filter(s -> s.getLecturer() != null)
                .collect(Collectors.groupingBy(s -> s.getLecturer().getId(), Collectors.counting()));

        // Pre-index: courseId -> lecturers có chuyên môn tương ứng (giảm chi phí filter lặp)
        Map<Long, List<Lecturer>> lecturersByCourseId = new HashMap<>();
        for (Lecturer l : lecturers) {
            if (l.getCourses() == null) continue;
            for (Course c : l.getCourses()) {
                if (c == null || c.getId() == null) continue;
                lecturersByCourseId.computeIfAbsent(c.getId(), k -> new ArrayList<>()).add(l);
            }
        }

        // Seed để kết quả ổn định theo cùng (semesterId, facultyId).
        Random rnd = new Random(Objects.hash(semesterId, facultyId));

        // Sắp xếp theo độ khó: TH trước -> số buổi/tuần lớn trước -> ít GV phù hợp trước
        // (để giảm trường hợp greedy gặp “nút thắt” rồi mới xử lý).
        record Candidate(ClassSection section,
                List<Lecturer> qualified,
                int qualifiedCount,
                int sessionsPerWeek,
                long minLoad) {}

        List<Candidate> candidates = new ArrayList<>(toAssign.size());
        for (ClassSection section : toAssign) {
            Course course = section.getCourseOffering().getCourse();
            if (course == null || course.getId() == null) continue;

            Long offeringFacId = section.getCourseOffering().getFaculty().getId();
            Set<Long> allowedFacIds = new HashSet<>();
            allowedFacIds.add(offeringFacId);
            if (course.getSharedFaculties() != null) {
                course.getSharedFaculties().stream().map(Faculty::getId).forEach(allowedFacIds::add);
            }

            // Match nhanh theo courseId, sau đó lọc theo allowedFacIds.
            List<Lecturer> qualified = lecturersByCourseId.getOrDefault(course.getId(), List.of())
                    .stream()
                    .filter(l -> l.getFaculty() != null && allowedFacIds.contains(l.getFaculty().getId()))
                    .toList();

            if (qualified.isEmpty()) {
                // Không có GV phù hợp thì bỏ qua (giữ lớp unassigned).
                continue;
            }

            long minLoad = qualified.stream()
                    .mapToLong(l -> load.getOrDefault(l.getId(), 0L))
                    .min()
                    .orElse(0L);

            int sessionsPerWeek = GeneticTimetableScheduler.calcSessionsPerWeek(section);
            candidates.add(new Candidate(section, qualified, qualified.size(), sessionsPerWeek, minLoad));
        }

        candidates.sort((a, b) -> {
            boolean aTh = a.section().getSectionType() == ClassSection.SectionType.TH;
            boolean bTh = b.section().getSectionType() == ClassSection.SectionType.TH;
            if (aTh && !bTh) return -1;
            if (!aTh && bTh) return 1;

            // Sessions/tuần: ưu tiên gán cái “nặng” trước (giảm khả năng tạo lệch về mặt lịch).
            int cmpSessions = Integer.compare(b.sessionsPerWeek(), a.sessionsPerWeek());
            if (cmpSessions != 0) return cmpSessions;

            // QualifiedCount: ít lựa chọn hơn thì xử lý trước
            int cmpQual = Integer.compare(a.qualifiedCount(), b.qualifiedCount());
            if (cmpQual != 0) return cmpQual;

            // Nếu tương đương, xử lý lớp có minLoad cao trước (tức mọi GV đều đang nặng).
            return Long.compare(b.minLoad(), a.minLoad());
        });

        int assigned = 0;
        for (Candidate cand : candidates) {
            ClassSection section = cand.section();
            if (section.getLecturer() != null) continue; // an toàn (dù đang lọc unassigned)

            // Chọn GV có tải hiện tại nhỏ nhất.
            long bestLoad = cand.qualified().stream()
                    .mapToLong(l -> load.getOrDefault(l.getId(), 0L))
                    .min()
                    .orElse(0L);

            // Tránh lệch do tie-breaking theo thứ tự tên: chọn ngẫu nhiên trong nhóm hòa.
            List<Lecturer> tied = cand.qualified().stream()
                    .filter(l -> load.getOrDefault(l.getId(), 0L) == bestLoad)
                    .toList();

            Lecturer best = tied.isEmpty() ? null : tied.get(rnd.nextInt(tied.size()));
            if (best == null) continue;

            section.setLecturer(best);
            section.setSkipAssignment(false);
            load.merge(best.getId(), 1L, (a, b) -> a + b);
            assigned++;
        }
        sectionRepo.saveAll(toAssign);
        return assigned;
    }

    /**
     * Thống kê tải giảng: số lớp mỗi giảng viên được phân công trong học kỳ (lọc
     * theo Khoa nếu có)
     */
    public List<TeachingLoadDto> getTeachingLoad(Long semesterId, Long facultyId) {
        List<ClassSection> sections = facultyId != null
                ? sectionRepo.findBySemesterAndFaculty(semesterId, facultyId)
                : sectionRepo.findByCourseOffering_Semester_Id(semesterId);

        Map<Long, Integer> countByLecturer = new HashMap<>();
        Map<Long, Lecturer> lecturerMap = new HashMap<>();
        for (ClassSection s : sections) {
            Lecturer l = s.getLecturer();
            if (l != null) {
                countByLecturer.merge(l.getId(), 1, (a, b) -> a + b);
                lecturerMap.putIfAbsent(l.getId(), l);
            }
        }

        return lecturerMap.entrySet().stream()
                .map(e -> new TeachingLoadDto(
                        e.getKey(),
                        e.getValue().getName(),
                        e.getValue().getEmail(),
                        countByLecturer.getOrDefault(e.getKey(), 0)))
                .sorted((a, b) -> Integer.compare(b.getSectionCount(), a.getSectionCount()))
                .collect(Collectors.toList());
    }

    /**
     * Khoa A gửi yêu cầu hỗ trợ GV — lớp thuộc kế hoạch của Khoa A nhưng thiếu GV.
     * Thông báo tự động gửi tới Khoa quản lý chuyên môn (course.faculty) để phân công.
     */
    @Transactional
    public ClassSection requestSupport(Long sectionId, String comment) {
        ClassSection section = sectionRepo.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lớp học phần ID: " + sectionId));
        section.setNeedsSupport(true);
        section.setSupportRequestComment(comment);
        section.setSkipAssignment(false);
        ClassSection saved = sectionRepo.save(section);

        if (saved.getCourseOffering() != null && saved.getCourseOffering().getCourse() != null
                && saved.getCourseOffering().getCourse().getFaculty() != null
                && saved.getCourseOffering().getSemester() != null) {
            Long managerFacultyId = saved.getCourseOffering().getCourse().getFaculty().getId();
            Long semesterId = saved.getCourseOffering().getSemester().getId();
            String courseCode = saved.getCourseOffering().getCourse().getCode();
            String courseName = saved.getCourseOffering().getCourse().getName();
            String reqFaculty = saved.getCourseOffering().getFaculty() != null ? saved.getCourseOffering().getFaculty().getName() : "Khoa yêu cầu";
            String title = "Yêu cầu hỗ trợ GV cho học phần " + courseCode;
            String msg = reqFaculty + " cần hỗ trợ: " + saved.getCode() + " - " + courseCode + " - " + courseName
                    + (comment != null && !comment.isBlank() ? (". Ghi chú: " + comment) : "");
            notificationService.create(managerFacultyId, title, msg, semesterId);
        }
        return saved;
    }

    /** P.ĐT xem tất cả yêu cầu hỗ trợ (chưa phân công) */
    public List<ClassSection> getSupportRequests(Long semesterId) {
        List<ClassSection> all = sectionRepo.findByCourseOffering_Semester_Id(semesterId);
        return all.stream()
                .filter(s -> Boolean.TRUE.equals(s.getNeedsSupport()) && s.getLecturer() == null)
                .collect(Collectors.toList());
    }

    /**
     * Gán danh sách lớp biên chế vào một lớp học phần.
     * Thay thế toàn bộ danh sách cũ bằng danh sách mới (set-replace).
     * Tự động cập nhật sĩ số dự kiến theo tổng sĩ số lớp BC được gán.
     */
    @Transactional
    public ClassSection assignAdminClasses(Long sectionId, List<Long> adminClassIds) {
        ClassSection section = sectionRepo.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lớp học phần ID: " + sectionId));
        Set<AdministrativeClass> adminClasses = new HashSet<>(adminClassRepo.findAllById(adminClassIds));
        section.setAdministrativeClasses(adminClasses);
        // Tự động cập nhật sĩ số dự kiến
        int total = adminClasses.stream()
                .mapToInt(ac -> ac.getStudentCount() != null ? ac.getStudentCount() : 0)
                .sum();
        section.setExpectedStudentCount(total > 0 ? total : null);
        return sectionRepo.save(section);
    }

    /** Kết quả chia: tập BC + sĩ số dự kiến (khi BC tách nhiều lớp thì sum(BC) sai, dùng expectedCount) */
    private record SliceWithCount(Set<AdministrativeClass> adminClasses, int expectedCount) {}

    /**
     * Gộp các slice có sĩ số < minThreshold vào slice khác để đủ quy mô lớp.
     * Chiến lược:
     *  1. Ư tiên gộp slice nhỏ vào slice sẽ cho tổng ≤ maxPerBin (không vượt ngưỡng).
     *  2. Nếu không có nơi gộp vừa, gộp vào slice nhỏ nhất hiện tại
     *     (chấp nhận vượt giới hạn nhẹ thay vì để lớp quá ít SV).
     */
    private List<SliceWithCount> mergeSmallSlices(List<SliceWithCount> slices, int minThreshold, int maxPerBin) {
        if (slices == null || slices.isEmpty() || minThreshold <= 0)
            return slices;
        List<SliceWithCount> result = new ArrayList<>(slices);
        boolean changed = true;
        while (changed) {
            changed = false;
            // Tìm slice nhỏ nhất dưới ngưỡng (bỏ qua slice rỗng)
            SliceWithCount smallest = null;
            int smallestIdx = -1;
            for (int i = 0; i < result.size(); i++) {
                SliceWithCount s = result.get(i);
                if (s.expectedCount() > 0 && s.expectedCount() < minThreshold) {
                    if (smallest == null || s.expectedCount() < smallest.expectedCount()) {
                        smallest = s;
                        smallestIdx = i;
                    }
                }
            }
            if (smallest == null) break;

            // Bước 1: Tìm slice vừa khớit để gộp (tổng ≤ maxPerBin)
            int bestIdx = -1;
            int bestCombined = -1;
            for (int i = 0; i < result.size(); i++) {
                if (i == smallestIdx) continue;
                SliceWithCount other = result.get(i);
                int combined = smallest.expectedCount() + other.expectedCount();
                if (combined <= maxPerBin && combined > bestCombined) {
                    bestCombined = combined;
                    bestIdx = i;
                }
            }

            // Bước 2: Fallback — gộp vào slice nhỏ nhất hiện có
            // (thay vì để lớp dưới ngưỡng mãi mãi tồn tại)
            if (bestIdx < 0 && result.size() > 1) {
                int minOtherCount = Integer.MAX_VALUE;
                for (int i = 0; i < result.size(); i++) {
                    if (i == smallestIdx) continue;
                    if (result.get(i).expectedCount() < minOtherCount) {
                        minOtherCount = result.get(i).expectedCount();
                        bestIdx = i;
                    }
                }
            }

            if (bestIdx < 0) break; // cả fallback cũng không tìm được

            // Gộp smallest vào result[bestIdx]
            SliceWithCount other = result.get(bestIdx);
            Set<AdministrativeClass> merged = new HashSet<>(smallest.adminClasses());
            merged.addAll(other.adminClasses());
            int mergedCount = smallest.expectedCount() + other.expectedCount();
            SliceWithCount mergedSlice = new SliceWithCount(merged, mergedCount);
            result.remove(Math.max(smallestIdx, bestIdx));
            result.remove(Math.min(smallestIdx, bestIdx));
            result.add(mergedSlice);
            changed = true;
        }
        return result;
    }

    /**
     * Chia lớp biên chế thành các nhóm sao cho mỗi nhóm ≤ maxPerBin SV.
     * Thuật toán Best-fit Decreasing:
     *  - Sắp xếp BC giảm dần theo sĩ số.
     *  - Với mỗi BC, chọn bin có sắn còn đủ chỗ (load + students ≤ maxPerBin)
     *    và đã chứa nhiều nhất (để gom chặt, cân bằng).
     *  - Nếu không có bin vừa, mở bin mới.
     *  - Mỗi BC chỉ xuất hiện trong đúng 1 bin (không phân mảnh BC).
     *    Nếu BC đơn lẻ vượt maxPerBin, nó sẽ đi vào bin riêng (vượt ngưỡng nhẹ).
     */
    private List<SliceWithCount> splitAdminClassesWithMax(List<AdministrativeClass> classes, int maxPerBin) {
        if (maxPerBin <= 0 || classes == null || classes.isEmpty())
            return List.of();

        // Sắp xếp giảm dần để ưu tiên những BC lớn được xếp trước
        List<AdministrativeClass> sorted = new ArrayList<>(classes);
        sorted.sort((a, b) -> {
            int sa = a.getStudentCount() != null ? a.getStudentCount() : 0;
            int sb2 = b.getStudentCount() != null ? b.getStudentCount() : 0;
            return Integer.compare(sb2, sa);
        });

        List<Set<AdministrativeClass>> bins = new ArrayList<>();
        List<Integer> binLoad = new ArrayList<>();

        for (AdministrativeClass ac : sorted) {
            int students = ac.getStudentCount() != null ? ac.getStudentCount() : 0;

            // Tìm bin vừa khớit, chọn bin đã chứa nhiều nhất (Best-fit)
            int chosen = -1;
            int maxExisting = -1;
            for (int i = 0; i < bins.size(); i++) {
                int current = binLoad.get(i);
                if (current + students <= maxPerBin && current > maxExisting) {
                    maxExisting = current;
                    chosen = i;
                }
            }

            // Không có bin nào vừa → mở bin mới
            // (nếu BC đơn lẻ vượt maxPerBin, nó vẫn đi riêng 1 bin — không tách BC)
            if (chosen < 0) {
                bins.add(new HashSet<>());
                binLoad.add(0);
                chosen = bins.size() - 1;
            }

            bins.get(chosen).add(ac);
            binLoad.set(chosen, binLoad.get(chosen) + students);
        }

        List<SliceWithCount> result = new ArrayList<>();
        for (int i = 0; i < bins.size(); i++)
            result.add(new SliceWithCount(bins.get(i), binLoad.get(i)));
        return result;
    }
}
