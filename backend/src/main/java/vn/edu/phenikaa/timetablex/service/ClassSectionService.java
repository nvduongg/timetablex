package vn.edu.phenikaa.timetablex.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.phenikaa.timetablex.entity.AdministrativeClass;
import vn.edu.phenikaa.timetablex.entity.ClassSection;
import vn.edu.phenikaa.timetablex.entity.CourseOffering;
import vn.edu.phenikaa.timetablex.entity.Lecturer;
import vn.edu.phenikaa.timetablex.repository.AdministrativeClassRepository;
import vn.edu.phenikaa.timetablex.repository.ClassSectionRepository;
import vn.edu.phenikaa.timetablex.repository.TimetableEntryRepository;
import vn.edu.phenikaa.timetablex.repository.CourseOfferingRepository;
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

    /**
     * Sinh tự động các Lớp học phần từ danh sách CourseOffering đã APPROVED.
     *
     * Quy tắc gán lớp biên chế (LT-led splitting):
     * - LT: mỗi lớp nhận nhóm BC ≤ {@value MAX_LT_STUDENTS} SV (phòng học lớn)
     * - TH: bám theo LT — mỗi nhóm BC của LT được chia tiếp thành các lớp TH
     *       (mỗi lớp ≤ {@value MAX_TH_STUDENTS} SV). 1 lớp LT có thể tương ứng 1–2 lớp TH.
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

            String courseCode = o.getCourse() != null && o.getCourse().getCode() != null
                    ? o.getCourse().getCode()
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

            int lt = o.getTheoryClassCount() != null ? o.getTheoryClassCount() : 0;
            int th = o.getPracticeClassCount() != null ? o.getPracticeClassCount() : 0;

            // ── LT: chia BC theo MAX_LT_STUDENTS (mỗi lớp ≤ 80 SV) ──────────────────
            List<SliceWithCount> ltSlices = lt > 0 && !facultyAdminClasses.isEmpty()
                    ? splitAdminClassesWithMax(facultyAdminClasses, MAX_LT_STUDENTS)
                    : List.of();
            int ltCount = Math.max(lt, ltSlices.size());
            // Khi không có BC: vẫn tạo đủ số lớp theo P.ĐT (để gán thủ công sau)
            if (lt > 0 && ltSlices.isEmpty() && ltCount > 0) {
                List<SliceWithCount> extended = new ArrayList<>(ltSlices);
                while (extended.size() < ltCount)
                    extended.add(new SliceWithCount(new HashSet<>(), 0));
                ltSlices = extended;
            }

            // ── TH: bám theo LT — mỗi nhóm LT chia tiếp thành các lớp TH (≤45 SV) ───
            List<SliceWithCount> thSlices = new ArrayList<>();
            if (th > 0) {
                if (!facultyAdminClasses.isEmpty()) {
                    if (!ltSlices.isEmpty()) {
                        for (SliceWithCount ltSwc : ltSlices) {
                            List<AdministrativeClass> ltList = new ArrayList<>(ltSwc.adminClasses());
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
                } else {
                    for (int i = 0; i < th; i++)
                        thSlices.add(new SliceWithCount(new HashSet<>(), 0));
                }
            }
            int thCount = Math.max(th, thSlices.size());

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
            // Giữ nguyên needsSupport khi skip
        } else {
            section.setSkipAssignment(false);
            if (lecturerId != null) {
                Lecturer lecturer = lecturerRepo.findById(lecturerId)
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giảng viên ID: " + lecturerId));
                section.setLecturer(lecturer);
                // Khi đã phân công GV, tự động reset needsSupport và xóa comment
                section.setNeedsSupport(false);
                section.setSupportRequestComment(null);
            } else {
                section.setLecturer(null);
                // Khi bỏ phân công, giữ nguyên needsSupport
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

        int assigned = 0;
        for (ClassSection section : toAssign) {
            Course course = section.getCourseOffering().getCourse();
            Long offeringFacId = section.getCourseOffering().getFaculty().getId();
            Set<Long> allowedFacIds = new HashSet<>();
            allowedFacIds.add(offeringFacId);
            if (course.getSharedFaculties() != null) {
                course.getSharedFaculties().stream().map(Faculty::getId).forEach(allowedFacIds::add);
            }

            List<Lecturer> qualified = lecturers.stream()
                    .filter(l -> l.getFaculty() != null && allowedFacIds.contains(l.getFaculty().getId()))
                    .filter(l -> l.getCourses() != null && l.getCourses().stream()
                            .anyMatch(c -> c.getId().equals(course.getId())))
                    .toList();

            if (qualified.isEmpty())
                continue;

            Lecturer best = qualified.stream()
                    .min(Comparator.comparingLong(l -> load.getOrDefault(l.getId(), 0L)))
                    .orElse(null);
            if (best == null)
                continue;

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

    /** Khoa yêu cầu hỗ trợ GV từ khoa khác */
    @Transactional
    public ClassSection requestSupport(Long sectionId, String comment) {
        ClassSection section = sectionRepo.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lớp học phần ID: " + sectionId));
        section.setNeedsSupport(true);
        section.setSupportRequestComment(comment);
        section.setSkipAssignment(false); // Reset skip nếu đã set
        ClassSection saved = sectionRepo.save(section);

        // Gửi thông báo tới Khoa quản lý chuyên môn của học phần
        if (saved.getCourseOffering() != null && saved.getCourseOffering().getCourse() != null
                && saved.getCourseOffering().getCourse().getFaculty() != null
                && saved.getCourseOffering().getSemester() != null) {
            Long managerFacultyId = saved.getCourseOffering().getCourse().getFaculty().getId();
            Long semesterId = saved.getCourseOffering().getSemester().getId();
            String courseCode = saved.getCourseOffering().getCourse().getCode();
            String courseName = saved.getCourseOffering().getCourse().getName();
            String title = "Yêu cầu hỗ trợ giảng viên cho học phần " + courseCode;
            String msg = "Có lớp học phần cần hỗ trợ giảng viên: "
                    + saved.getCode() + " - " + courseCode + " - " + courseName
                    + (comment != null && !comment.isBlank() ? (". Ghi chú: " + comment) : "");
            notificationService.create(managerFacultyId, title, msg, semesterId);
        }

        return saved;
    }

    /** P.ĐT xem danh sách các lớp cần hỗ trợ GV */
    public List<ClassSection> getSupportRequests(Long semesterId) {
        List<ClassSection> all = sectionRepo.findByCourseOffering_Semester_Id(semesterId);
        return all.stream()
                .filter(s -> Boolean.TRUE.equals(s.getNeedsSupport()))
                .filter(s -> s.getLecturer() == null) // Chưa được phân công
                .collect(Collectors.toList());
    }

    /** P.ĐT giải quyết yêu cầu hỗ trợ: phân công GV từ khoa khác */
    @Transactional
    public ClassSection resolveSupportRequest(Long sectionId, Long lecturerId) {
        ClassSection section = sectionRepo.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lớp học phần ID: " + sectionId));
        if (lecturerId != null) {
            Lecturer lecturer = lecturerRepo.findById(lecturerId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giảng viên ID: " + lecturerId));
            section.setLecturer(lecturer);
            section.setNeedsSupport(false); // Đã giải quyết
            section.setSupportRequestComment(null);
        }
        return sectionRepo.save(section);
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
     * Chia lớp biên chế thành các nhóm sao cho mỗi nhóm ≤ maxPerBin SV.
     * Nếu 1 BC có sĩ số > maxPerBin thì tách vào nhiều lớp (cùng BC xuất hiện ở nhiều lớp).
     */
    private List<SliceWithCount> splitAdminClassesWithMax(List<AdministrativeClass> classes, int maxPerBin) {
        List<Set<AdministrativeClass>> bins = new ArrayList<>();
        List<Integer> binLoad = new ArrayList<>();
        if (maxPerBin <= 0 || classes == null || classes.isEmpty())
            return List.of();

        List<AdministrativeClass> sorted = new ArrayList<>(classes);
        sorted.sort((a, b) -> {
            int sa = a.getStudentCount() != null ? a.getStudentCount() : 0;
            int sb = b.getStudentCount() != null ? b.getStudentCount() : 0;
            return Integer.compare(sb, sa); // giảm dần
        });

        for (AdministrativeClass ac : sorted) {
            int students = ac.getStudentCount() != null ? ac.getStudentCount() : 0;
            int remaining = students;
            while (remaining > 0) {
                int add = Math.min(maxPerBin, remaining);
                int chosen = -1;
                int minLoad = Integer.MAX_VALUE; // Worst-fit: chọn bin ít tải nhất còn chỗ → cân bằng sĩ số
                for (int i = 0; i < bins.size(); i++) {
                    if (binLoad.get(i) + add <= maxPerBin && binLoad.get(i) < minLoad) {
                        minLoad = binLoad.get(i);
                        chosen = i;
                    }
                }
                if (chosen < 0) {
                    bins.add(new HashSet<>());
                    binLoad.add(0);
                    chosen = bins.size() - 1;
                }
                bins.get(chosen).add(ac);
                binLoad.set(chosen, binLoad.get(chosen) + add);
                remaining -= add;
            }
        }
        List<SliceWithCount> result = new ArrayList<>();
        for (int i = 0; i < bins.size(); i++)
            result.add(new SliceWithCount(bins.get(i), binLoad.get(i)));
        return result;
    }
}
