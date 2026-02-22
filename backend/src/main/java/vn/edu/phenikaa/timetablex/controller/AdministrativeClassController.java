package vn.edu.phenikaa.timetablex.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.phenikaa.timetablex.entity.AdministrativeClass;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.phenikaa.timetablex.service.AdministrativeClassService;
import vn.edu.phenikaa.timetablex.service.CurrentUserService;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/classes") // Endpoint ngắn gọn
public class AdministrativeClassController {

    @Autowired
    private AdministrativeClassService classService;
    @Autowired
    private CurrentUserService currentUserService;

    @GetMapping
    public List<AdministrativeClass> getAll() {
        Long facultyId = currentUserService.getCurrentFacultyId();
        return classService.getAll(facultyId);
    }

    @PostMapping
    public AdministrativeClass create(@RequestBody AdministrativeClass adminClass) {
        Long fid = currentUserService.getCurrentFacultyId();
        if (fid != null && (adminClass.getMajor() == null || adminClass.getMajor().getFaculty() == null
                || !adminClass.getMajor().getFaculty().getId().equals(fid)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ được tạo lớp thuộc ngành của khoa bạn");
        return classService.save(adminClass);
    }

    @PutMapping("/{id}")
    public AdministrativeClass update(@PathVariable Long id, @RequestBody AdministrativeClass adminClass) {
        Long fid = currentUserService.getCurrentFacultyId();
        if (fid != null && !classService.belongsToFaculty(id, fid))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không được sửa lớp thuộc khoa khác");
        adminClass.setId(id);
        return classService.save(adminClass);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        Long fid = currentUserService.getCurrentFacultyId();
        if (fid != null && !classService.belongsToFaculty(id, fid))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không được xóa lớp thuộc khoa khác");
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