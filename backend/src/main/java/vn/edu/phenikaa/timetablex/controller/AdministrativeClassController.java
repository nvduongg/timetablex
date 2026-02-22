package vn.edu.phenikaa.timetablex.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.phenikaa.timetablex.entity.AdministrativeClass;
import vn.edu.phenikaa.timetablex.service.AdministrativeClassService;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/classes") // Endpoint ngắn gọn
public class AdministrativeClassController {

    @Autowired
    private AdministrativeClassService classService;

    @GetMapping
    public List<AdministrativeClass> getAll() {
        return classService.getAll();
    }

    @PostMapping
    public AdministrativeClass create(@RequestBody AdministrativeClass adminClass) {
        return classService.save(adminClass);
    }

    @PutMapping("/{id}")
    public AdministrativeClass update(@PathVariable Long id, @RequestBody AdministrativeClass adminClass) {
        adminClass.setId(id);
        return classService.save(adminClass);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        classService.delete(id);
    }

    @GetMapping("/template")
    public ResponseEntity<InputStreamResource> downloadTemplate() throws IOException {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=class_template.xlsx")
                .body(new InputStreamResource(classService.generateTemplate()));
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ResponseEntity<?> importExcel(@RequestParam("file") MultipartFile file) {
        try {
            classService.importExcel(file);
            return ResponseEntity.ok("Import thành công");
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }
}