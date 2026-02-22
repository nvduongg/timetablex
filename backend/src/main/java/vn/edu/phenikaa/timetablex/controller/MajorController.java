package vn.edu.phenikaa.timetablex.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.phenikaa.timetablex.entity.Major;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.phenikaa.timetablex.service.CurrentUserService;
import vn.edu.phenikaa.timetablex.service.MajorService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/majors")
public class MajorController {

    @Autowired
    private MajorService majorService;
    @Autowired
    private CurrentUserService currentUserService;

    @GetMapping
    public List<Major> getAll() {
        Long facultyId = currentUserService.getCurrentFacultyId();
        return majorService.getAll(facultyId);
    }

    @PostMapping
    public Major create(@RequestBody Major major) {
        Long fid = currentUserService.getCurrentFacultyId();
        if (fid != null && (major.getFaculty() == null || !major.getFaculty().getId().equals(fid)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ được tạo ngành thuộc khoa của bạn");
        return majorService.save(major);
    }

    @PutMapping("/{id}")
    public Major update(@PathVariable Long id, @RequestBody Major major) {
        Long fid = currentUserService.getCurrentFacultyId();
        if (fid != null && !majorService.belongsToFaculty(id, fid))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không được sửa ngành thuộc khoa khác");
        major.setId(id);
        return majorService.save(major);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        Long fid = currentUserService.getCurrentFacultyId();
        if (fid != null && !majorService.belongsToFaculty(id, fid))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không được xóa ngành thuộc khoa khác");
        try {
            majorService.delete(id);
            return ResponseEntity.ok().build();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return ResponseEntity.badRequest()
                    .body("Không thể xóa Ngành này vì đang được sử dụng ở bảng khác (như Lớp biên chế).");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi xóa ngành: " + e.getMessage());
        }
    }

    @GetMapping("/template")
    public ResponseEntity<InputStreamResource> downloadTemplate() throws IOException {
        ByteArrayInputStream in = majorService.generateTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=major_template.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importExcel(@RequestParam("file") MultipartFile file) {
        try {
            majorService.importExcel(file);
            return ResponseEntity.ok("Import thành công");
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }
}