package vn.edu.phenikaa.timetablex.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.phenikaa.timetablex.dto.TeachingLoadDto;
import vn.edu.phenikaa.timetablex.entity.ClassSection;
import vn.edu.phenikaa.timetablex.service.ClassSectionService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/class-sections")
public class ClassSectionController {

    @Autowired
    private ClassSectionService sectionService;

    @GetMapping
    public List<ClassSection> getBySemester(
            @RequestParam Long semesterId,
            @RequestParam(required = false) Long facultyId) {
        if (facultyId != null) return sectionService.getBySemesterAndFaculty(semesterId, facultyId);
        return sectionService.getBySemester(semesterId);
    }

    @PutMapping("/{id}/assign")
    public ClassSection assignLecturer(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Long lecturerId = payload.get("lecturerId") != null
                ? ((Number) payload.get("lecturerId")).longValue() : null;
        Boolean skip = payload.get("skipAssignment") != null
                ? Boolean.valueOf(payload.get("skipAssignment").toString()) : false;
        return sectionService.assignLecturer(id, lecturerId, skip);
    }

    @GetMapping("/faculty-skip-allowed")
    public ResponseEntity<Map<String, Boolean>> isFacultySkipAllowed(@RequestParam Long facultyId) {
        return ResponseEntity.ok(Map.of("allowed", sectionService.isFacultyAllowedSkipAssignment(facultyId)));
    }

    @GetMapping("/by-offering/{offeringId}")
    public List<ClassSection> getByOffering(@PathVariable Long offeringId) {
        return sectionService.getByCourseOffering(offeringId);
    }

    @PostMapping("/auto-assign")
    public ResponseEntity<Map<String, Object>> autoAssign(@RequestBody Map<String, Object> payload) {
        Long semesterId = payload.get("semesterId") != null
                ? ((Number) payload.get("semesterId")).longValue() : null;
        Long facultyId = payload.get("facultyId") != null
                ? ((Number) payload.get("facultyId")).longValue() : null;
        int assigned = sectionService.autoAssign(semesterId, facultyId);
        return ResponseEntity.ok(Map.of(
                "message", "Đã tự động phân công " + assigned + " lớp học phần",
                "assignedCount", assigned
        ));
    }

    @GetMapping("/teaching-load")
    public List<TeachingLoadDto> getTeachingLoad(
            @RequestParam Long semesterId,
            @RequestParam(required = false) Long facultyId) {
        return sectionService.getTeachingLoad(semesterId, facultyId);
    }

    /** Khoa A gửi yêu cầu hỗ trợ GV — chuyển tới Khoa quản lý chuyên môn (course.faculty) */
    @PutMapping("/{id}/request-support")
    public ResponseEntity<Map<String, Object>> requestSupport(
            @PathVariable Long id, @RequestBody Map<String, Object> payload) {
        String comment = payload.get("comment") != null ? payload.get("comment").toString() : null;
        ClassSection section = sectionService.requestSupport(id, comment);
        return ResponseEntity.ok(Map.of("message", "Đã gửi yêu cầu hỗ trợ GV tới Khoa quản lý chuyên môn", "section", section));
    }

    /** P.ĐT xem danh sách yêu cầu hỗ trợ chưa xử lý */
    @GetMapping("/support-requests")
    public List<ClassSection> getSupportRequests(@RequestParam Long semesterId) {
        return sectionService.getSupportRequests(semesterId);
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateFromApproved(@RequestBody Map<String, Object> payload) {
        Object semObj = payload.get("semesterId");
        Long semesterId = semObj instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(semObj));
        boolean forceRegenerate = payload.get("forceRegenerate") != null
                && Boolean.parseBoolean(String.valueOf(payload.get("forceRegenerate")));
        int created = sectionService.generateFromApprovedOfferings(semesterId, forceRegenerate);
        String msg = forceRegenerate
                ? "Đã xóa hết lớp cũ và sinh lại " + created + " lớp học phần"
                : "Đã sinh " + created + " lớp học phần từ danh sách đã duyệt";
        return ResponseEntity.ok(Map.of(
                "message", msg,
                "createdCount", created
        ));
    }

}
