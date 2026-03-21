package vn.edu.phenikaa.timetablex.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.phenikaa.timetablex.entity.Course;
import vn.edu.phenikaa.timetablex.entity.Faculty;
import vn.edu.phenikaa.timetablex.repository.CourseRepository;
import vn.edu.phenikaa.timetablex.repository.FacultyRepository;
import vn.edu.phenikaa.timetablex.repository.CurriculumDetailRepository;
import vn.edu.phenikaa.timetablex.repository.CourseOfferingRepository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

@Service
public class CourseService {

    @Autowired
    private CourseRepository courseRepo;
    @Autowired
    private FacultyRepository facultyRepo;
    @Autowired
    private CurriculumDetailRepository curriculumDetailRepo;
    @Autowired
    private CourseOfferingRepository courseOfferingRepo;

    public List<Course> getAll() {
        return courseRepo.findAll();
    }

    public List<Course> getAll(Long facultyId) {
        if (facultyId == null) return courseRepo.findAll();
        return courseRepo.findByFaculty_IdOrderByCode(facultyId);
    }

    public Course save(Course course) {
        return courseRepo.save(course);
    }

    public boolean belongsToFaculty(Long courseId, Long facultyId) {
        if (facultyId == null) return true;
        return courseRepo.findById(courseId)
                .map(c -> c.getFaculty() != null && c.getFaculty().getId().equals(facultyId))
                .orElse(false);
    }

    public void delete(Long id) {
        Course course = courseRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy học phần với id: " + id));

        boolean usedInCurriculum = curriculumDetailRepo.existsByCourse(course);
        boolean usedInOffering = courseOfferingRepo.existsByCourse(course);

        if (usedInCurriculum || usedInOffering) {
            throw new IllegalStateException("Không thể xóa học phần vì đang được sử dụng trong CTĐT hoặc kế hoạch mở lớp.");
        }

        courseRepo.delete(course);
    }

    public Course update(Long id, Course course) {
        Course existingCourse = courseRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Course not found with id: " + id));
        existingCourse.setCode(course.getCode());
        existingCourse.setName(course.getName());
        existingCourse.setCredits(course.getCredits());
        existingCourse.setTheoryCredits(course.getTheoryCredits());
        existingCourse.setPracticeCredits(course.getPracticeCredits());
        existingCourse.setRequiredRoomType(course.getRequiredRoomType());
        existingCourse.setFaculty(course.getFaculty());
        return courseRepo.save(existingCourse);
    }

    public Course createFromPayload(Map<String, Object> payload) {
        Course c = new Course();
        if (payload.containsKey("code")) c.setCode((String) payload.get("code"));
        if (payload.containsKey("name")) c.setName((String) payload.get("name"));
        if (payload.containsKey("credits")) c.setCredits(((Number) payload.get("credits")).doubleValue());
        if (payload.containsKey("theoryCredits")) c.setTheoryCredits(((Number) payload.get("theoryCredits")).doubleValue());
        if (payload.containsKey("practiceCredits")) c.setPracticeCredits(((Number) payload.get("practiceCredits")).doubleValue());
        if (payload.containsKey("selfStudyCredits")) c.setSelfStudyCredits(payload.get("selfStudyCredits") != null ? ((Number) payload.get("selfStudyCredits")).doubleValue() : null);
        if (payload.containsKey("requiredRoomType")) c.setRequiredRoomType((String) payload.get("requiredRoomType"));
        if (payload.containsKey("learningMethod")) c.setLearningMethod(Course.LearningMethod.valueOf(payload.get("learningMethod").toString()));
        if (payload.containsKey("faculty") && payload.get("faculty") instanceof Map<?, ?> m && m.get("id") != null) {
            c.setFaculty(facultyRepo.findById(((Number) m.get("id")).longValue()).orElseThrow());
        }
        if (payload.containsKey("sharedFacultyIds")) {
            List<?> ids = (List<?>) payload.get("sharedFacultyIds");
            Set<Faculty> shared = new HashSet<>();
            if (ids != null) for (Object o : ids) {
                if (o instanceof Number) facultyRepo.findById(((Number) o).longValue()).ifPresent(shared::add);
            }
            c.setSharedFaculties(shared);
        }
        return courseRepo.save(c);
    }

    public Course updateWithPayload(Long id, Map<String, Object> payload) {
        Course existing = courseRepo.findById(id).orElseThrow();
        if (payload.containsKey("code")) existing.setCode((String) payload.get("code"));
        if (payload.containsKey("name")) existing.setName((String) payload.get("name"));
        if (payload.containsKey("credits")) existing.setCredits(((Number) payload.get("credits")).doubleValue());
        if (payload.containsKey("theoryCredits")) existing.setTheoryCredits(((Number) payload.get("theoryCredits")).doubleValue());
        if (payload.containsKey("practiceCredits")) existing.setPracticeCredits(((Number) payload.get("practiceCredits")).doubleValue());
        if (payload.containsKey("selfStudyCredits")) existing.setSelfStudyCredits(payload.get("selfStudyCredits") != null ? ((Number) payload.get("selfStudyCredits")).doubleValue() : null);
        if (payload.containsKey("requiredRoomType")) existing.setRequiredRoomType((String) payload.get("requiredRoomType"));
        if (payload.containsKey("learningMethod")) existing.setLearningMethod(Course.LearningMethod.valueOf(payload.get("learningMethod").toString()));
        if (payload.containsKey("faculty") && payload.get("faculty") instanceof Map m && m.get("id") != null) {
            existing.setFaculty(facultyRepo.findById(((Number) m.get("id")).longValue()).orElseThrow());
        }
        if (payload.containsKey("sharedFacultyIds")) {
            List<?> ids = (List<?>) payload.get("sharedFacultyIds");
            Set<Faculty> shared = new HashSet<>();
            if (ids != null) for (Object o : ids) {
                if (o instanceof Number) facultyRepo.findById(((Number) o).longValue()).ifPresent(shared::add);
            }
            existing.setSharedFaculties(shared);
        }
        return courseRepo.save(existing);
    }

    public ByteArrayInputStream generateTemplate() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Sheet sheet = workbook.createSheet("Courses");

            // Style cho header
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row header = sheet.createRow(0);
            String[] headers = {
                "Mã HP",
                "Tên Học Phần",
                "Tổng TC",
                "TC Lý thuyết",
                "TC Thực hành",
                "TC Tự học",
                "Hình thức (OFFLINE/ONLINE_ELEARNING/ONLINE_COURSERA/HYBRID)",
                // Các giá trị Loại phòng hợp lệ:
                // LT = Phòng lý thuyết (xếp lịch bình thường)
                // TH = Phòng thực hành/lab (xếp lịch bình thường)
                // SB = Sân bãi/Thể dục (xếp lịch, sĩ số lớn)
                // TT = Thực tập doanh nghiệp (KHÔNG xếp lịch)
                // TL = Tiểu luận (KHÔNG xếp lịch)
                // DA = Đồ án môn học / Đồ án tốt nghiệp (KHÔNG xếp lịch)
                // LA = Luận văn / Luận án (KHÔNG xếp lịch)
                "Loại phòng (LT / TH / SB / TT / TL / DA / LA)",
                "Mã Khoa",
                "Mã Khoa dùng chung (cách nhau dấu phẩy, VD: IT,CS,MATH)"
            };
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Dòng mẫu 1: Môn lý thuyết thông thường
            Row sample1 = sheet.createRow(1);
            sample1.createCell(0).setCellValue("CSE702036");
            sample1.createCell(1).setCellValue("Mạng máy tính");
            sample1.createCell(2).setCellValue(3);
            sample1.createCell(3).setCellValue(2);
            sample1.createCell(4).setCellValue(1);
            sample1.createCell(5).setCellValue(0);
            sample1.createCell(6).setCellValue("OFFLINE");
            sample1.createCell(7).setCellValue("LT");
            sample1.createCell(8).setCellValue("CNTT");
            sample1.createCell(9).setCellValue("");

            // Dòng mẫu 2: Đồ án tốt nghiệp (không xếp lịch)
            Row sample2 = sheet.createRow(2);
            sample2.createCell(0).setCellValue("CSE800001");
            sample2.createCell(1).setCellValue("Đồ án tốt nghiệp");
            sample2.createCell(2).setCellValue(10);
            sample2.createCell(3).setCellValue(0);
            sample2.createCell(4).setCellValue(0);
            sample2.createCell(5).setCellValue(10);
            sample2.createCell(6).setCellValue("OFFLINE");
            sample2.createCell(7).setCellValue("DA");
            sample2.createCell(8).setCellValue("CNTT");
            sample2.createCell(9).setCellValue("");

            // Dòng mẫu 3: Thực tập doanh nghiệp (không xếp lịch)
            Row sample3 = sheet.createRow(3);
            sample3.createCell(0).setCellValue("CSE700001");
            sample3.createCell(1).setCellValue("Thực tập doanh nghiệp");
            sample3.createCell(2).setCellValue(3);
            sample3.createCell(3).setCellValue(0);
            sample3.createCell(4).setCellValue(0);
            sample3.createCell(5).setCellValue(3);
            sample3.createCell(6).setCellValue("OFFLINE");
            sample3.createCell(7).setCellValue("TT");
            sample3.createCell(8).setCellValue("CNTT");
            sample3.createCell(9).setCellValue("");

            // Tự động điều chỉnh độ rộng cột
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
        } finally {
            workbook.close();
        }
        return new ByteArrayInputStream(out.toByteArray());
    }

    public void importExcel(MultipartFile file) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            List<Course> courses = new ArrayList<>();
            List<Faculty> faculties = facultyRepo.findAll();
            
            // Prefetch existing course codes
            Set<String> existingCourses = courseRepo.findAll().stream()
                    .map(Course::getCode)
                    .collect(java.util.stream.Collectors.toSet());
            Set<String> processedCodes = new HashSet<>();

            for (Row row : sheet) {
                if (row.getRowNum() == 0)
                    continue;

                // Lấy dữ liệu an toàn
                String code = getCellValue(row.getCell(0));
                String name = getCellValue(row.getCell(1));
                Double thCredits = getNumericValue(row.getCell(3));
                Double prCredits = getNumericValue(row.getCell(4));
                Double selfStudy = getNumericValue(row.getCell(5));
                String methodStr = getCellValue(row.getCell(6));
                String roomType = getCellValue(row.getCell(7));
                String facultyCode = getCellValue(row.getCell(8));
                String sharedCodesRaw = getCellValue(row.getCell(9));

                if (code != null && facultyCode != null) {
                    if (existingCourses.contains(code) || processedCodes.contains(code))
                        continue;

                    Optional<Faculty> fac = faculties.stream()
                            .filter(f -> f.getCode().equalsIgnoreCase(facultyCode))
                            .findFirst();

                    if (fac.isPresent()) {
                        Course c = new Course();
                        c.setCode(code);
                        c.setName(name);
                        // Tổng TC = LT + TH (không tính tín tự học)
                        c.setCredits((thCredits != null ? thCredits : 0.0) + (prCredits != null ? prCredits : 0.0));
                        c.setTheoryCredits(thCredits);
                        c.setPracticeCredits(prCredits);
                        c.setSelfStudyCredits(selfStudy);

                        try {
                            if (methodStr != null && !methodStr.trim().isEmpty()) {
                                c.setLearningMethod(Course.LearningMethod.valueOf(methodStr.trim().toUpperCase()));
                            } else {
                                c.setLearningMethod(Course.LearningMethod.OFFLINE);
                            }
                        } catch (IllegalArgumentException e) {
                            c.setLearningMethod(Course.LearningMethod.OFFLINE);
                        }

                        c.setRequiredRoomType(roomType != null ? roomType : "LT");
                        c.setFaculty(fac.get());

                        // Khoa dùng chung: parse "IT,CS,MATH" hoặc "IT; CS; MATH"
                        Set<Faculty> shared = new HashSet<>();
                        if (sharedCodesRaw != null && !sharedCodesRaw.isBlank()) {
                            for (String part : sharedCodesRaw.split("[,;]")) {
                                String fc = part.trim();
                                if (fc.isEmpty()) continue;
                                faculties.stream()
                                        .filter(f -> f.getCode().equalsIgnoreCase(fc) && !f.getId().equals(fac.get().getId()))
                                        .findFirst()
                                        .ifPresent(shared::add);
                            }
                        }
                        c.setSharedFaculties(shared);

                        courses.add(c);
                        processedCodes.add(code);
                    }
                }
            }
            if (!courses.isEmpty()) {
                courseRepo.saveAll(courses);
            }
        }
    }

    /** Giống importExcel nhưng NẾU mã HP đã tồn tại thì CẬP NHẬT thay vì bỏ qua.
     *  Dùng để re-import lại cấu trúc tín chỉ (TC LT / TC TH) mà không cần xóa dữ liệu. */
    public void upsertExcel(MultipartFile file) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            List<Faculty> faculties = facultyRepo.findAll();
            
            // Prefetch existing courses to map by code
            java.util.Map<String, Course> existingCoursesMap = courseRepo.findAll().stream()
                    .collect(java.util.stream.Collectors.toMap(Course::getCode, c -> c));
            List<Course> coursesToSave = new ArrayList<>();

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;

                String code       = getCellValue(row.getCell(0));
                String name       = getCellValue(row.getCell(1));
                Double thCredits  = getNumericValue(row.getCell(3));
                Double prCredits  = getNumericValue(row.getCell(4));
                Double selfStudy  = getNumericValue(row.getCell(5));
                String methodStr  = getCellValue(row.getCell(6));
                String roomType   = getCellValue(row.getCell(7));
                String facultyCode = getCellValue(row.getCell(8));
                String sharedCodesRaw = getCellValue(row.getCell(9));

                if (code == null || facultyCode == null) continue;

                Optional<Faculty> fac = faculties.stream()
                        .filter(f -> f.getCode().equalsIgnoreCase(facultyCode))
                        .findFirst();
                if (fac.isEmpty()) continue;

                // Lấy hoặc tạo mới từ in-memory map
                Course c = existingCoursesMap.get(code);
                if (c == null) {
                    c = new Course();
                    existingCoursesMap.put(code, c);
                }
                c.setCode(code);
                if (name != null && !name.isBlank()) c.setName(name);
                c.setCredits((thCredits != null ? thCredits : 0.0) + (prCredits != null ? prCredits : 0.0));
                c.setTheoryCredits(thCredits);
                c.setPracticeCredits(prCredits);
                c.setSelfStudyCredits(selfStudy);
                try {
                    c.setLearningMethod(methodStr != null && !methodStr.isBlank()
                            ? Course.LearningMethod.valueOf(methodStr.trim().toUpperCase())
                            : Course.LearningMethod.OFFLINE);
                } catch (IllegalArgumentException e) {
                    c.setLearningMethod(Course.LearningMethod.OFFLINE);
                }
                c.setRequiredRoomType(roomType != null && !roomType.isBlank() ? roomType : "LT");
                c.setFaculty(fac.get());

                Set<Faculty> shared = new HashSet<>();
                if (sharedCodesRaw != null && !sharedCodesRaw.isBlank()) {
                    for (String part : sharedCodesRaw.split("[,;]")) {
                        String fc = part.trim();
                        if (fc.isEmpty()) continue;
                        faculties.stream()
                                .filter(f -> f.getCode().equalsIgnoreCase(fc) && !f.getId().equals(fac.get().getId()))
                                .findFirst()
                                .ifPresent(shared::add);
                    }
                }
                c.setSharedFaculties(shared);
                coursesToSave.add(c);
            }
            if (!coursesToSave.isEmpty()) {
                courseRepo.saveAll(coursesToSave);
            }
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue().trim();
        if (cell.getCellType() == CellType.NUMERIC) return String.valueOf((long) cell.getNumericCellValue());
        return null;
    }

    private Double getNumericValue(Cell cell) {
        if (cell == null)
            return 0.0;
        return cell.getCellType() == CellType.NUMERIC ? cell.getNumericCellValue() : 0.0;
    }
}