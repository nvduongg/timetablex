package vn.edu.phenikaa.timetablex.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.phenikaa.timetablex.entity.Course;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.phenikaa.timetablex.service.CourseService;
import vn.edu.phenikaa.timetablex.service.CurrentUserService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    @Autowired
    private CourseService courseService;
    @Autowired
    private CurrentUserService currentUserService;

    @GetMapping
    public List<Course> getAll() {
        Long facultyId = currentUserService.getCurrentFacultyId();
        return courseService.getAll(facultyId);
    }

    @PostMapping
    public Course create(@RequestBody java.util.Map<String, Object> payload) {
        return courseService.createFromPayload(payload);
    }

    @PutMapping("/{id}")
    public Course update(@PathVariable Long id, @RequestBody java.util.Map<String, Object> payload) {
        return courseService.updateWithPayload(id, payload);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        Long fid = currentUserService.getCurrentFacultyId();
        if (fid != null && !courseService.belongsToFaculty(id, fid))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không được xóa môn học thuộc khoa khác");
        try {
            courseService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/template")
    public ResponseEntity<InputStreamResource> downloadTemplate() throws IOException {
        ByteArrayInputStream in = courseService.generateTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=course_template.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importExcel(@RequestParam("file") MultipartFile file) {
        try {
            courseService.importExcel(file);
            return ResponseEntity.ok("Import thành công");
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }
}
