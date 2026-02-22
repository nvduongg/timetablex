package vn.edu.phenikaa.timetablex.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.phenikaa.timetablex.entity.*;
import vn.edu.phenikaa.timetablex.repository.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

@Service
public class LecturerService {
    @Autowired private LecturerRepository lecturerRepo;
    @Autowired private FacultyRepository facultyRepo;
    @Autowired private CourseRepository courseRepo;
    @Autowired private vn.edu.phenikaa.timetablex.repository.CourseOfferingRepository offeringRepo;

    public List<Lecturer> getAll() { return lecturerRepo.findAll(); }
    public List<Lecturer> getByFaculty(Long facultyId) {
        if (facultyId == null) return lecturerRepo.findAll();
        return lecturerRepo.findByFaculty_IdOrderByName(facultyId);
    }

    /**
     * Lấy danh sách GV cho dropdown phân công thủ công.
     * Trả về GV thuộc khoa + GV từ khoa khác nhưng có chuyên môn về các môn
     * mà khoa này đang mở trong học kỳ — không dùng sharedFaculties nữa,
     * chỉ dựa vào ma trận chuyên môn (lecturer.courses).
     */
    public List<Lecturer> getForAssignment(Long semesterId, Long facultyId) {
        if (semesterId == null || facultyId == null) return getByFaculty(facultyId);

        // Lấy tập ID các môn học mà khoa này mở trong học kỳ
        Set<Long> facultyCourseIds = offeringRepo.findBySemesterId(semesterId).stream()
                .filter(o -> o.getFaculty() != null && o.getFaculty().getId().equals(facultyId))
                .filter(o -> o.getCourse() != null)
                .map(o -> o.getCourse().getId())
                .collect(java.util.stream.Collectors.toSet());

        if (facultyCourseIds.isEmpty()) return getByFaculty(facultyId);

        // GV thuộc khoa này HOẶC có chuyên môn về ít nhất một môn của khoa trong kỳ
        return lecturerRepo.findAll().stream()
                .filter(l ->
                        (l.getFaculty() != null && l.getFaculty().getId().equals(facultyId))
                        || (l.getCourses() != null && l.getCourses().stream()
                                .anyMatch(c -> facultyCourseIds.contains(c.getId())))
                )
                .sorted(java.util.Comparator.comparing(Lecturer::getName))
                .collect(java.util.stream.Collectors.toList());
    }
    public Lecturer save(Lecturer lecturer) { return lecturerRepo.save(lecturer); }
    public void delete(Long id) { lecturerRepo.deleteById(id); }

    public boolean belongsToFaculty(Long lecturerId, Long facultyId) {
        if (facultyId == null) return true;
        return lecturerRepo.findById(lecturerId)
                .map(l -> l.getFaculty() != null && l.getFaculty().getId().equals(facultyId))
                .orElse(false);
    }

    // API cập nhật ma trận chuyên môn (Gán list môn cho GV)
    public Lecturer updateCompetency(Long lecturerId, List<Long> courseIds) {
        Lecturer lecturer = lecturerRepo.findById(lecturerId).orElseThrow();
        List<Course> courses = courseRepo.findAllById(courseIds);
        lecturer.setCourses(new HashSet<>(courses));
        return lecturerRepo.save(lecturer);
    }

    // --- EXCEL LOGIC ---
    public ByteArrayInputStream generateTemplate() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Sheet sheet = workbook.createSheet("Lecturers");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Họ tên");
            header.createCell(1).setCellValue("Email");
            header.createCell(2).setCellValue("Mã Khoa");
            header.createCell(3).setCellValue("Mã môn dạy được (Cách nhau dấu phẩy)");

            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue("Nguyễn Văn A");
            sample.createCell(1).setCellValue("a.nguyenvan@phenikaa-uni.edu.vn");
            sample.createCell(2).setCellValue("CNTT");
            sample.createCell(3).setCellValue("INT101, INT102"); // Ma trận nhập ở đây

            workbook.write(out);
        } finally { workbook.close(); }
        return new ByteArrayInputStream(out.toByteArray());
    }

    @Transactional
    public void importExcel(MultipartFile file) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            List<Faculty> faculties = facultyRepo.findAll();
            List<Course> allCourses = courseRepo.findAll(); // Cache để tìm cho nhanh

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;

                String name = getCellValue(row.getCell(0));
                String email = getCellValue(row.getCell(1));
                String facultyCode = getCellValue(row.getCell(2));
                String coursesStr = getCellValue(row.getCell(3)); // Chuỗi mã môn

                if (email != null && facultyCode != null) {
                    if (lecturerRepo.existsByEmail(email)) continue;

                    Optional<Faculty> fac = faculties.stream()
                            .filter(f -> f.getCode().equalsIgnoreCase(facultyCode)).findFirst();

                    if (fac.isPresent()) {
                        Lecturer lecturer = new Lecturer();
                        lecturer.setName(name);
                        lecturer.setEmail(email);
                        lecturer.setFaculty(fac.get());

                        // Xử lý Ma trận: Tách chuỗi "INT101, INT102" -> Set<Course>
                        if (coursesStr != null && !coursesStr.isEmpty()) {
                            String[] codes = coursesStr.split(",");
                            Set<Course> competency = new HashSet<>();
                            for (String code : codes) {
                                String cleanCode = code.trim();
                                allCourses.stream()
                                    .filter(c -> c.getCode().equalsIgnoreCase(cleanCode))
                                    .findFirst()
                                    .ifPresent(competency::add);
                            }
                            lecturer.setCourses(competency);
                        }
                        lecturerRepo.save(lecturer);
                    }
                }
            }
        }
    }
    
    private String getCellValue(Cell cell) {
        if (cell == null) return null;
        return cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : null;
    }
}