package vn.edu.phenikaa.timetablex.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.phenikaa.timetablex.dto.AutoGenerateRequest;
import vn.edu.phenikaa.timetablex.entity.*;
import vn.edu.phenikaa.timetablex.repository.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@Service
public class CourseOfferingService {
    @Autowired
    private CourseOfferingRepository offeringRepo;
    @Autowired
    private SemesterRepository semesterRepo;
    @Autowired
    private CurriculumRepository curriculumRepo;
    @Autowired
    private AdministrativeClassRepository adminClassRepo;
    @Autowired
    private CourseRepository courseRepo;
    @Autowired
    private FacultyRepository facultyRepo;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private CohortService cohortService;

    public List<CourseOffering> getBySemester(Long semesterId) {
        return offeringRepo.findBySemesterId(semesterId);
    }

    public List<CourseOffering> getBySemester(Long semesterId, Long facultyId, CourseOffering.Status status) {
        if (facultyId != null && status != null)
            return offeringRepo.findBySemesterIdAndFacultyIdAndStatus(semesterId, facultyId, status);
        if (facultyId != null)
            return offeringRepo.findBySemesterIdAndFacultyId(semesterId, facultyId);
        if (status != null)
            return offeringRepo.findBySemesterIdAndStatus(semesterId, status);
        return offeringRepo.findBySemesterId(semesterId);
    }

    @Transactional
    public void generateAutomatedPlan(AutoGenerateRequest req) {
        if (req == null || req.getSemesterId() == null) {
            throw new IllegalArgumentException("Học kỳ (semesterId) không được để trống");
        }
        Semester semester = semesterRepo.findById(req.getSemesterId()).orElseThrow();

        // Lấy tất cả các lớp biên chế trong toàn trường
        List<AdministrativeClass> classes = adminClassRepo.findAll();
        if (classes.isEmpty()) return;

        // Key: (cohort, courseId) → đảm bảo K17 và K18 học cùng môn vẫn tạo offering riêng
        record DemandKey(String cohort, Long courseId) {}
        Map<DemandKey, Integer>  demandMap  = new LinkedHashMap<>();
        Map<DemandKey, Course>   courseMap  = new LinkedHashMap<>();

        for (AdministrativeClass cls : classes) {
            // Ưu tiên dùng Cohort entity nếu có, fallback về chuỗi cohort cũ
            String cohortCode = null;
            if (cls.getCohortRef() != null && cls.getCohortRef().getCode() != null) {
                cohortCode = cls.getCohortRef().getCode();
            } else if (cls.getCohort() != null && !cls.getCohort().isBlank()) {
                cohortCode = cls.getCohort().trim();
            }
            if (cohortCode == null || cohortCode.isBlank()) continue;

            Long majorId = cls.getMajor().getId();
            // Lấy CTĐT khớp với chuyên ngành và khóa của lớp này (theo mã khóa)
            List<Curriculum> currList = curriculumRepo.findByMajorIdAndCohort(majorId, cohortCode);
            if (currList.isEmpty()) continue;

            // Lấy admissionYear trực tiếp từ CTĐT (ưu tiên giá trị đã lưu trong CTĐT),
            // fallback sang Cohort của CTĐT hoặc Cohort của Lớp nếu cần
            Integer admYear = currList.stream()
                    .map(Curriculum::getAdmissionYear)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (admYear == null) {
                admYear = currList.stream()
                        .map(Curriculum::getCohortRef)
                        .filter(Objects::nonNull)
                        .map(Cohort::getAdmissionYear)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
            }
            if (admYear == null && cls.getCohortRef() != null) {
                admYear = cls.getCohortRef().getAdmissionYear();
            }

            // Nếu vẫn chưa xác định được admissionYear → bỏ qua lớp này, in cảnh báo
            if (admYear == null) {
                System.err.printf("[AutoGenerate] Bỏ qua lớp %s: CTĐT chưa có Năm nhập học (admissionYear). " +
                        "Vui lòng cập nhật lại CTĐT của khóa %s.%n", cls.getCode(), cohortCode);
                continue;
            }

            int semesterIndex = (req.getPlanningYear() - admYear) * req.getTermsPerYear() + req.getPlanningTerm();

            // Nếu học kỳ âm (chưa nhập học) hoặc > 20 (đã ra trường) → bỏ qua
            if (semesterIndex < 1 || semesterIndex > 20) continue;

            for (Curriculum curr : currList) {
                List<CurriculumDetail> details = curr.getDetails().stream()
                        .filter(d -> {
                            if (d.getSemesterIndex() == null) return false;
                            String[] semesters = d.getSemesterIndex().split(",");
                            String target = String.valueOf(semesterIndex);
                            for (String s : semesters) {
                                if (s.trim().equals(target)) return true;
                            }
                            return false;
                        })
                        .toList();

                for (CurriculumDetail detail : details) {
                    Course course = detail.getCourse();
                    // Bỏ qua các môn Thực tập / Đồ án / Khóa luận (loại phòng DN)
                    // vì những môn này sinh viên tự đăng ký với GV, không cần xếp lịch phòng
                    if ("DN".equalsIgnoreCase(course.getRequiredRoomType())) continue;

                    // Nhóm nhu cầu theo (cohort, course) — K17 và K18 cùng học 1 môn → 2 offering riêng
                    DemandKey key = new DemandKey(cohortCode, course.getId());
                    demandMap.merge(key, cls.getStudentCount(), Integer::sum);
                    courseMap.putIfAbsent(key, course);
                }
            }
        }

        // Sĩ số tối đa mỗi lớp
        final int THEORY_SIZE = 60;
        final int PRACTICE_SIZE = 30;
        final int SB_SIZE = 140; // Sân bãi / GDTC: có thể gom lớp lớn hơn
        // Tỷ lệ tách TH từ mỗi lớp LT (mỗi 1 lớp LT tách thành bao nhiêu lớp TH)
        final int PRACTICE_PER_THEORY = (int) Math.ceil((double) THEORY_SIZE / PRACTICE_SIZE); // = 2

        List<CourseOffering> offerings = new ArrayList<>();

        for (Map.Entry<DemandKey, Integer> entry : demandMap.entrySet()) {
            DemandKey key           = entry.getKey();
            Course    course        = courseMap.get(key);
            Integer   totalStudents = entry.getValue();
            String    cohort        = key.cohort();

            // Bỏ qua nếu offering (học kỳ, môn, khóa) đã tồn tại
            if (offeringRepo.existsBySemesterAndCourseAndCohort(semester, course, cohort))
                continue;

            CourseOffering offering = new CourseOffering();
            offering.setSemester(semester);
            offering.setCourse(course);
            offering.setFaculty(course.getFaculty());
            offering.setCohort(cohort);
            // Gắn thêm tham chiếu Cohort nếu có trong danh mục
            Cohort cohortEntity = cohortService.getByCodeOrNull(cohort);
            offering.setCohortRef(cohortEntity);
            offering.setStudentDemand(totalStudents);

            boolean hasTheory   = course.getTheoryCredits()   != null && course.getTheoryCredits()   > 0;
            boolean hasPractice = course.getPracticeCredits() != null && course.getPracticeCredits() > 0;
            boolean isFieldCourse = "SB".equalsIgnoreCase(course.getRequiredRoomType());

            if (isFieldCourse) {
                // Môn sân bãi / GDTC: coi như LT mở rộng, không tách TH, cho phép sĩ số lớn
                int theoryCount = (int) Math.ceil((double) totalStudents / SB_SIZE);
                if (theoryCount < 1) theoryCount = 1;
                offering.setTheoryClassCount(theoryCount);
                offering.setPracticeClassCount(0);
            } else if (hasTheory && hasPractice) {
                // Trường hợp 1: Môn kết hợp LT + TH (VD: Vật lý, Hóa học)
                // → Mở gộp lớp LT lớn, sau đó mỗi lớp LT tách nhỏ ra thành K lớp TH
                int theoryCount = (int) Math.ceil((double) totalStudents / THEORY_SIZE);
                offering.setTheoryClassCount(theoryCount);
                offering.setPracticeClassCount(theoryCount * PRACTICE_PER_THEORY);
            } else if (hasTheory) {
                // Trường hợp 2: Môn thuần lý thuyết (VD: Triết học, Lịch sử Đảng)
                // → Chỉ mở lớp LT, không có lớp TH
                offering.setTheoryClassCount((int) Math.ceil((double) totalStudents / THEORY_SIZE));
                offering.setPracticeClassCount(0);
            } else if (hasPractice) {
                // Trường hợp 3: Môn thuần thực hành / phòng máy (VD: Lập trình, Thí nghiệm)
                // → Không có lớp LT gộp, chỉ có lớp TH nhỏ
                offering.setTheoryClassCount(0);
                offering.setPracticeClassCount((int) Math.ceil((double) totalStudents / PRACTICE_SIZE));
            } else {
                // Không xác định được cấu trúc tín chỉ → mặc định 1 lớp LT
                offering.setTheoryClassCount(1);
                offering.setPracticeClassCount(0);
            }

            offering.setStatus(CourseOffering.Status.DRAFT);
            offerings.add(offering);
        }

        offeringRepo.saveAll(offerings);
    }


    /**
     * P.ĐT chỉnh sửa nhanh kế hoạch (số lớp LT/TH, nhu cầu SV).
     * Chỉ cho phép sửa khi trạng thái chưa APPROVED.
     * Nếu đang REJECTED hoặc WAITING_APPROVAL thì sau khi sửa sẽ đưa về DRAFT để
     * gửi duyệt lại.
     */
    public CourseOffering updatePlan(Long id, Integer theoryClassCount, Integer practiceClassCount,
            Integer studentDemand) {
        CourseOffering offering = offeringRepo.findById(id).orElseThrow();
        if (offering.getStatus() == CourseOffering.Status.APPROVED) {
            throw new IllegalStateException(
                    "Kế hoạch đã được Khoa chốt, không thể chỉnh sửa trực tiếp. Vui lòng liên hệ Khoa/Viện.");
        }
        if (theoryClassCount != null)
            offering.setTheoryClassCount(theoryClassCount);
        if (practiceClassCount != null)
            offering.setPracticeClassCount(practiceClassCount);
        if (studentDemand != null)
            offering.setStudentDemand(studentDemand);

        if (offering.getStatus() == CourseOffering.Status.REJECTED
                || offering.getStatus() == CourseOffering.Status.WAITING_APPROVAL) {
            offering.setStatus(CourseOffering.Status.DRAFT);
            offering.setSubmittedAt(null);
        }
        return offeringRepo.save(offering);
    }

    public CourseOffering updateStatus(Long id, CourseOffering.Status status, String rejectionComment) {
        CourseOffering offering = offeringRepo.findById(id).orElseThrow();
        offering.setStatus(status);
        offering.setRejectionComment(rejectionComment);
        return offeringRepo.save(offering);
    }

    /**
     * P.ĐT gửi danh sách học phần dự kiến cho Khoa xác nhận (DRAFT →
     * WAITING_APPROVAL). Tạo thông báo theo từng Khoa.
     */
    @Transactional
    public void sendForApproval(Long semesterId, List<Long> offeringIds) {
        Semester semester = semesterRepo.findById(semesterId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy học kỳ có ID: " + semesterId));
        List<CourseOffering> toSend;
        if (offeringIds == null || offeringIds.isEmpty()) {
            toSend = offeringRepo.findBySemesterId(semesterId).stream()
                    .filter(o -> o.getStatus() == CourseOffering.Status.DRAFT
                            || o.getStatus() == CourseOffering.Status.REJECTED)
                    .toList();
        } else {
            toSend = offeringRepo.findAllById(offeringIds).stream()
                    .filter(o -> o.getSemester().getId().equals(semesterId)
                            && (o.getStatus() == CourseOffering.Status.DRAFT
                                    || o.getStatus() == CourseOffering.Status.REJECTED))
                    .toList();
        }
        if (toSend.isEmpty()) {
            throw new IllegalStateException(
                    "Không có học phần nào ở trạng thái Nháp hoặc Khoa yêu cầu chỉnh sửa để gửi duyệt. " +
                            (offeringIds != null && !offeringIds.isEmpty()
                                    ? "Các học phần đã chọn có thể đã được duyệt hoặc đang chờ xử lý."
                                    : ""));
        }

        LocalDateTime now = LocalDateTime.now();
        for (CourseOffering o : toSend) {
            o.setStatus(CourseOffering.Status.WAITING_APPROVAL);
            o.setSubmittedAt(now);
        }
        offeringRepo.saveAll(toSend);

        Map<Long, Long> countByFaculty = toSend.stream()
                .filter(o -> o.getFaculty() != null && o.getFaculty().getId() != null)
                .collect(Collectors.groupingBy(o -> o.getFaculty().getId(), Collectors.counting()));
        String semesterName = semester.getName() != null ? semester.getName() : "Học kỳ " + semesterId;
        for (Map.Entry<Long, Long> e : countByFaculty.entrySet()) {
            try {
                notificationService.create(
                        e.getKey(),
                        "Yêu cầu xác nhận danh sách học phần dự kiến",
                        "P.ĐT đã gửi " + e.getValue() + " học phần dự kiến tổ chức cho " + semesterName
                                + ". Vui lòng xác nhận hoặc phản hồi trong vòng 03 ngày làm việc.",
                        semesterId);
            } catch (Exception ex) {
                // Không fail toàn bộ quá trình nếu một notification lỗi
                System.err.println("Lỗi khi tạo notification cho facultyId " + e.getKey() + ": " + ex.getMessage());
            }
        }
    }

    @Transactional
    public void importExcel(Long semesterId, MultipartFile file) throws IOException {
        Semester semester = semesterRepo.findById(semesterId).orElseThrow();
        List<Course> courses = courseRepo.findAll();
        List<Faculty> faculties = facultyRepo.findAll();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            
            // In-memory lookup map of existing offerings for this semester
            List<CourseOffering> existingOfferings = offeringRepo.findBySemesterId(semesterId);
            java.util.Map<Long, CourseOffering> existingOfferingsMap = existingOfferings.stream()
                    .collect(java.util.stream.Collectors.toMap(o -> o.getCourse().getId(), o -> o));
            List<CourseOffering> offeringsToSave = new ArrayList<>();

            for (Row row : sheet) {
                if (row.getRowNum() == 0)
                    continue;

                String courseCode = getCellString(row.getCell(0));
                String facultyCode = getCellString(row.getCell(1));
                Integer theoryCount = getCellInt(row.getCell(2));
                Integer practiceCount = getCellInt(row.getCell(3));
                Integer demand = getCellInt(row.getCell(4));

                if (courseCode == null || facultyCode == null)
                    continue;

                Optional<Course> courseOpt = courses.stream().filter(c -> courseCode.equalsIgnoreCase(c.getCode()))
                        .findFirst();
                Optional<Faculty> facultyOpt = faculties.stream().filter(f -> facultyCode.equalsIgnoreCase(f.getCode()))
                        .findFirst();
                if (courseOpt.isEmpty() || facultyOpt.isEmpty())
                    continue;

                Course course = courseOpt.get();
                Faculty faculty = facultyOpt.get();

                CourseOffering offering = existingOfferingsMap.get(course.getId());

                if (offering == null) {
                    offering = new CourseOffering();
                    offering.setSemester(semester);
                    offering.setCourse(course);
                    offering.setFaculty(faculty);
                    offering.setStatus(CourseOffering.Status.DRAFT);
                }
                offering.setTheoryClassCount(theoryCount != null ? theoryCount : 0);
                offering.setPracticeClassCount(practiceCount != null ? practiceCount : 0);
                offering.setStudentDemand(demand != null ? demand : 0);
                offeringsToSave.add(offering);
                existingOfferingsMap.put(course.getId(), offering); // update map in case there are duplicates in file
            }
            if (!offeringsToSave.isEmpty()) {
                offeringRepo.saveAll(offeringsToSave);
            }
        }
    }

    public ByteArrayInputStream generateImportTemplate() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Sheet sheet = workbook.createSheet("BM.ĐT.01.01");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Mã HP");
            header.createCell(1).setCellValue("Mã Khoa");
            header.createCell(2).setCellValue("Số lớp LT");
            header.createCell(3).setCellValue("Số lớp TH");
            header.createCell(4).setCellValue("Nhu cầu SV");
            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue("CSE702036");
            sample.createCell(1).setCellValue("CNTT");
            sample.createCell(2).setCellValue(2);
            sample.createCell(3).setCellValue(1);
            sample.createCell(4).setCellValue(180);
            workbook.write(out);
        } finally {
            workbook.close();
        }
        return new ByteArrayInputStream(out.toByteArray());
    }

    private String getCellString(Cell cell) {
        if (cell == null)
            return null;
        if (cell.getCellType() == CellType.STRING)
            return cell.getStringCellValue().trim();
        if (cell.getCellType() == CellType.NUMERIC)
            return String.valueOf((long) cell.getNumericCellValue());
        return null;
    }

    private Integer getCellInt(Cell cell) {
        if (cell == null)
            return 0;
        if (cell.getCellType() == CellType.NUMERIC)
            return (int) cell.getNumericCellValue();
        if (cell.getCellType() == CellType.STRING) {
            try {
                return Integer.parseInt(cell.getStringCellValue().trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}
