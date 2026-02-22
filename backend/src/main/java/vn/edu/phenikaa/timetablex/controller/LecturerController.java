package vn.edu.phenikaa.timetablex.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.phenikaa.timetablex.entity.Lecturer;
import vn.edu.phenikaa.timetablex.service.LecturerService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lecturers")
public class LecturerController {

    @Autowired
    private LecturerService lecturerService;

    @GetMapping
    public List<Lecturer> getAll(
            @RequestParam(required = false) Long facultyId,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Boolean forAssignment) {
        if (Boolean.TRUE.equals(forAssignment) && semesterId != null && facultyId != null) {
            return lecturerService.getForAssignment(semesterId, facultyId);
        }
        if (facultyId != null) return lecturerService.getByFaculty(facultyId);
        return lecturerService.getAll();
    }

    @PostMapping
    public Lecturer create(@RequestBody Lecturer lecturer) {
        return lecturerService.save(lecturer);
    }

    @PutMapping("/{id}")
    public Lecturer update(@PathVariable Long id, @RequestBody Lecturer lecturer) {
        lecturer.setId(id);
        return lecturerService.save(lecturer);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        lecturerService.delete(id);
    }

    // Cập nhật ma trận chuyên môn
    @PutMapping("/{id}/competency")
    public Lecturer updateCompetency(@PathVariable Long id, @RequestBody Map<String, List<Long>> payload) {
        List<Long> courseIds = payload.get("courseIds");
        return lecturerService.updateCompetency(id, courseIds);
    }

    @GetMapping("/template")
    public ResponseEntity<InputStreamResource> downloadTemplate() throws IOException {
        ByteArrayInputStream in = lecturerService.generateTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=lecturers_template.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importExcel(@RequestParam("file") MultipartFile file) {
        try {
            lecturerService.importExcel(file);
            return ResponseEntity.ok("Import giảng viên thành công");
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }
}
