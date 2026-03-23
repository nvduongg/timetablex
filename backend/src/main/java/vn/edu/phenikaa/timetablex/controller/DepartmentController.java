package vn.edu.phenikaa.timetablex.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.phenikaa.timetablex.entity.Department;
import vn.edu.phenikaa.timetablex.service.CurrentUserService;
import vn.edu.phenikaa.timetablex.service.DepartmentService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private CurrentUserService currentUserService;

    @GetMapping
    public List<Department> getAll(@RequestParam(required = false) Long facultyId) {
        Long currentFacultyId = currentUserService.getCurrentFacultyId();
        Long filterFacultyId = facultyId != null ? facultyId : currentFacultyId;
        return departmentService.getByFaculty(filterFacultyId);
    }

    @PostMapping
    public Department create(@RequestBody Department department) {
        Long fid = currentUserService.getCurrentFacultyId();
        if (fid != null && (department.getFaculty() == null || !department.getFaculty().getId().equals(fid)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ được tạo bộ môn thuộc khoa của bạn");
        return departmentService.save(department);
    }

    @PutMapping("/{id}")
    public Department update(@PathVariable Long id, @RequestBody Department department) {
        Long fid = currentUserService.getCurrentFacultyId();
        if (fid != null && !departmentService.belongsToFaculty(id, fid))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không được sửa bộ môn thuộc khoa khác");
        return departmentService.update(id, department);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        Long fid = currentUserService.getCurrentFacultyId();
        if (fid != null && !departmentService.belongsToFaculty(id, fid))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không được xóa bộ môn thuộc khoa khác");
        departmentService.delete(id);
    }

    @GetMapping("/template")
    public ResponseEntity<InputStreamResource> downloadTemplate() throws IOException {
        ByteArrayInputStream in = departmentService.generateTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=department_template.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importExcel(@RequestParam("file") MultipartFile file) {
        try {
            departmentService.importExcel(file);
            return ResponseEntity.ok("Import bộ môn thành công");
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }
}
