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
        Semester semester = semesterRepo.findById(req.getSemesterId()).orElseThrow();

        // Lọc theo cohort nhưng tránh NPE nếu lớp chưa có khóa hoặc request thiếu cohort
        final String targetCohort = req.getCohort() != null ? req.getCohort().trim() : null;
        List<AdministrativeClass> classes = adminClassRepo.findAll().stream()
                .filter(c -> c.getCohort() != null && targetCohort != null
                        && c.getCohort().trim().equalsIgnoreCase(targetCohort))
                .toList();

        if (classes.isEmpty()) return;

        Map<Long, Integer> demandByMajor = new HashMap<>();
        for (AdministrativeClass cls : classes) {
            Long majorId = cls.getMajor().getId();
            demandByMajor.put(majorId, demandByMajor.getOrDefault(majorId, 0) + cls.getStudentCount());
        }

        Map<Course, Integer> courseDemandMap = new HashMap<>();

        for (Map.Entry<Long, Integer> entry : demandByMajor.entrySet()) {
            Long majorId = entry.getKey();
            Integer studentCount = entry.getValue();

            // Có thể tồn tại nhiều CTĐT cho cùng (majorId, cohort) → gộp tất cả
            List<Curriculum> currList = curriculumRepo.findByMajorIdAndCohort(majorId, targetCohort);

            for (Curriculum curr : currList) {
                List<CurriculumDetail> details = curr.getDetails().stream()
                        .filter(d -> {
                            if (d.getSemesterIndex() == null) return false;
                            String[] semesters = d.getSemesterIndex().split(",");
                            String target = String.valueOf(req.getSemesterIndex());
                            for (String s : semesters) {
                                if (s.trim().equals(target)) return true;
                            }
                            return false;
                        })
                        .toList();

                for (CurriculumDetail detail : details) {
                    Course course = detail.getCourse();
                    courseDemandMap.put(course, courseDemandMap.getOrDefault(course, 0) + studentCount);
                }
            }
        }

        // Sĩ số tối đa mỗi lớp
        int THEORY_SIZE = 60;
        int PRACTICE_SIZE = 30;

        List<CourseOffering> offerings = new ArrayList<>();

        for (Map.Entry<Course, Integer> entry : courseDemandMap.entrySet()) {
            Course course = entry.getKey();
            Integer totalStudents = entry.getValue();

            if (offeringRepo.existsBySemesterAndCourse(semester, course)) continue;

            CourseOffering offering = new CourseOffering();
            offering.setSemester(semester);
            offering.setCourse(course);
            offering.setFaculty(course.getFaculty());
            offering.setStudentDemand(totalStudents);

            if (course.getTheoryCredits() != null && course.getTheoryCredits() > 0) {
                offering.setTheoryClassCount((int) Math.ceil((double) totalStudents / THEORY_SIZE));
            } else {
                offering.setTheoryClassCount(0);
            }

            if (course.getPracticeCredits() != null && course.getPracticeCredits() > 0) {
                offering.setPracticeClassCount((int) Math.ceil((double) totalStudents / PRACTICE_SIZE));
            } else {
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
     * Nếu đang REJECTED hoặc WAITING_APPROVAL thì sau khi sửa sẽ đưa về DRAFT để gửi duyệt lại.
     */
    public CourseOffering updatePlan(Long id, Integer theoryClassCount, Integer practiceClassCount, Integer studentDemand) {
        CourseOffering offering = offeringRepo.findById(id).orElseThrow();
        if (offering.getStatus() == CourseOffering.Status.APPROVED) {
            throw new IllegalStateException("Kế hoạch đã được Khoa chốt, không thể chỉnh sửa trực tiếp. Vui lòng liên hệ Khoa/Viện.");
        }
        if (theoryClassCount != null) offering.setTheoryClassCount(theoryClassCount);
        if (practiceClassCount != null) offering.setPracticeClassCount(practiceClassCount);
        if (studentDemand != null) offering.setStudentDemand(studentDemand);

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

    /** P.ĐT gửi danh sách học phần dự kiến cho Khoa xác nhận (DRAFT → WAITING_APPROVAL). Tạo thông báo theo từng Khoa. */
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
                    : "")
            );
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
                        "P.ĐT đã gửi " + e.getValue() + " học phần dự kiến tổ chức cho " + semesterName + ". Vui lòng xác nhận hoặc phản hồi trong vòng 03 ngày làm việc.",
                        semesterId
                );
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
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;

                String courseCode = getCellString(row.getCell(0));
                String facultyCode = getCellString(row.getCell(1));
                Integer theoryCount = getCellInt(row.getCell(2));
                Integer practiceCount = getCellInt(row.getCell(3));
                Integer demand = getCellInt(row.getCell(4));

                if (courseCode == null || facultyCode == null) continue;

                Optional<Course> courseOpt = courses.stream().filter(c -> courseCode.equalsIgnoreCase(c.getCode())).findFirst();
                Optional<Faculty> facultyOpt = faculties.stream().filter(f -> facultyCode.equalsIgnoreCase(f.getCode())).findFirst();
                if (courseOpt.isEmpty() || facultyOpt.isEmpty()) continue;

                Course course = courseOpt.get();
                Faculty faculty = facultyOpt.get();

                CourseOffering offering = offeringRepo.findBySemesterId(semesterId).stream()
                        .filter(o -> o.getCourse().getId().equals(course.getId()))
                        .findFirst()
                        .orElse(null);

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
                offeringRepo.save(offering);
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
        if (cell == null) return null;
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue().trim();
        if (cell.getCellType() == CellType.NUMERIC) return String.valueOf((long) cell.getNumericCellValue());
        return null;
    }

    private Integer getCellInt(Cell cell) {
        if (cell == null) return 0;
        if (cell.getCellType() == CellType.NUMERIC) return (int) cell.getNumericCellValue();
        if (cell.getCellType() == CellType.STRING) {
            try { return Integer.parseInt(cell.getStringCellValue().trim()); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }
}
