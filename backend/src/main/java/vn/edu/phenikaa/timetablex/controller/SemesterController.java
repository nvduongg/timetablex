package vn.edu.phenikaa.timetablex.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.phenikaa.timetablex.entity.Semester;
import vn.edu.phenikaa.timetablex.service.SemesterService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/semesters")
public class SemesterController {

    @Autowired
    private SemesterService semesterService;

    // Lấy tất cả kỳ học
    @GetMapping
    public List<Semester> getAll() {
        return semesterService.getAll();
    }

    // Lấy kỳ học đang hoạt động
    @GetMapping("/active")
    public Semester getActive() {
        return semesterService.getActiveSemester();
    }

    // Tạo mới kỳ học
    @PostMapping
    public Semester create(@RequestBody Semester semester) {
        return semesterService.save(semester);
    }

    // Cập nhật kỳ học
    @PutMapping("/{id}")
    public Semester update(@PathVariable Long id, @RequestBody Semester semester) {
        semester.setId(id);
        return semesterService.save(semester);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        semesterService.delete(id);
    }

    @GetMapping("/template")
    public ResponseEntity<InputStreamResource> downloadTemplate() throws IOException {
        ByteArrayInputStream in = semesterService.generateTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=semester_template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importExcel(@RequestParam("file") MultipartFile file) {
        try {
            semesterService.importExcel(file);
            return ResponseEntity.ok("Import thành công");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }
}
