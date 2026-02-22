package vn.edu.phenikaa.timetablex.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.phenikaa.timetablex.entity.Curriculum;
import vn.edu.phenikaa.timetablex.entity.CurriculumDetail;
import vn.edu.phenikaa.timetablex.service.CurrentUserService;
import vn.edu.phenikaa.timetablex.service.CurriculumService;
import vn.edu.phenikaa.timetablex.repository.CurriculumRepository;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/curriculums")
public class CurriculumController {
    @Autowired private CurriculumService service;
    @Autowired private CurrentUserService currentUserService;
    @Autowired private CurriculumRepository curriculumRepository;

    @GetMapping
    public List<Curriculum> getAll() {
        Long facultyId = currentUserService.getCurrentFacultyId();
        return service.getAll(facultyId);
    }

    @PostMapping
    public Curriculum create(@RequestBody Curriculum curr) {
        Long fid = currentUserService.getCurrentFacultyId();
        if (fid != null && (curr.getMajor() == null || curr.getMajor().getFaculty() == null
                || !curr.getMajor().getFaculty().getId().equals(fid)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ được tạo CTĐT thuộc ngành của khoa bạn");
        return service.create(curr);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        Long fid = currentUserService.getCurrentFacultyId();
        if (fid != null && !service.belongsToFaculty(id, fid))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không được xóa CTĐT thuộc khoa khác");
        service.delete(id);
    }

    @DeleteMapping("/details/{detailId}")
    public void removeDetail(@PathVariable Long detailId) {
        Long fid = currentUserService.getCurrentFacultyId();
        if (fid != null && !service.detailBelongsToFaculty(detailId, fid))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không được sửa CTĐT thuộc khoa khác");
        service.removeDetail(detailId);
    }

    @PostMapping("/{id}/details")
    public CurriculumDetail addDetail(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Long fid = currentUserService.getCurrentFacultyId();
        if (fid != null && !service.belongsToFaculty(id, fid))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không được sửa CTĐT thuộc khoa khác");
        Long courseId = payload.get("courseId") != null ? ((Number) payload.get("courseId")).longValue() : null;
        String semesterIndex = payload.get("semesterIndex") != null ? payload.get("semesterIndex").toString() : "1";
        if (courseId == null) throw new IllegalArgumentException("courseId bắt buộc");
        return service.addDetail(id, courseId, semesterIndex);
    }

    @GetMapping("/cohorts")
    public List<String> getAllCohorts() {
        return curriculumRepository.findAllCohorts();
    }
    // Download Template Import Lộ trình
    @GetMapping("/roadmap-template")
    public ResponseEntity<InputStreamResource> downloadTemplate() throws IOException {
        ByteArrayInputStream in = service.generateRoadmapTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=roadmap_template.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    // Import Lộ trình cho 1 CTĐT cụ thể
    @PostMapping(value = "/{id}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importRoadmap(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        Long fid = currentUserService.getCurrentFacultyId();
        if (fid != null && !service.belongsToFaculty(id, fid))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không được sửa CTĐT thuộc khoa khác");
        try {
            service.importRoadmap(id, file);
            return ResponseEntity.ok("Import lộ trình thành công");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }
}