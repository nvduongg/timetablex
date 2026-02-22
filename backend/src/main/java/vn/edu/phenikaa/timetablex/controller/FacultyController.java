package vn.edu.phenikaa.timetablex.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.phenikaa.timetablex.entity.Faculty;
import vn.edu.phenikaa.timetablex.service.FacultyService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/faculties")
public class FacultyController {

    @Autowired
    private FacultyService facultyService;

    @GetMapping
    public List<Faculty> getAll() {
        return facultyService.getAll();
    }

    @PostMapping
    public Faculty create(@RequestBody Faculty faculty) {
        return facultyService.save(faculty);
    }

    @PutMapping("/{id}")
    public Faculty update(@PathVariable Long id, @RequestBody Faculty faculty) {
        faculty.setId(id);
        return facultyService.save(faculty);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        facultyService.delete(id);
    }

    // API Download Template
    @GetMapping("/template")
    public ResponseEntity<InputStreamResource> downloadTemplate() throws IOException {
        ByteArrayInputStream in = facultyService.generateTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=faculty_template.xlsx");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    // API Import Excel
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importExcel(@RequestParam("file") MultipartFile file) {
        try {
            facultyService.importExcel(file);
            return ResponseEntity.ok().body("Import thành công");
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Lỗi đọc file: " + e.getMessage());
        }
    }
}