package vn.edu.phenikaa.timetablex.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.phenikaa.timetablex.entity.TimetableEntry;
import vn.edu.phenikaa.timetablex.service.ClassSectionService;
import vn.edu.phenikaa.timetablex.service.TimetableService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/timetable")
public class TimetableController {

    @Autowired
    private TimetableService timetableService;
    @Autowired
    private ClassSectionService classSectionService;

    private static Long asLong(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof String s && !s.isBlank()) {
            try { return Long.parseLong(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static Integer asInt(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        return v instanceof Number ? ((Number) v).intValue() : null;
    }

    private static ResponseEntity<?> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    /**
     * Kích hoạt thuật toán xếp TKB tự động cho một học kỳ
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generateTimetable(@RequestBody Map<String, Object> payload) {
        Long semesterId = asLong(payload, "semesterId");
        if (semesterId == null) {
            return badRequest("semesterId không được để trống");
        }
        try {
            return ResponseEntity.ok(timetableService.generateTimetable(semesterId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.internalServerError().body(Map.of("message", "Lỗi xếp TKB: " + msg));
        }
    }

    /**
     * Lấy TKB của một học kỳ, có thể lọc theo lớp học phần hoặc lớp biên chế.
     */
    @GetMapping
    public List<TimetableEntry> getTimetable(
            @RequestParam Long semesterId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long adminClassId) {
        if (sectionId != null) {
            return timetableService.getTimetableBySection(sectionId);
        }
        if (adminClassId != null) {
            return timetableService.getTimetableByAdminClass(semesterId, adminClassId);
        }
        return timetableService.getTimetableBySemester(semesterId);
    }

    /**
     * Cập nhật một entry TKB (chỉnh sửa thủ công).
     * Nhận shiftId (ca học) thay vì timeSlotId - ca học là đơn vị xếp lịch.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateEntry(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {
        Long roomId = asLong(payload, "roomId");
        Long shiftId = asLong(payload, "shiftId");
        Integer dayOfWeek = asInt(payload, "dayOfWeek");
        if (roomId == null || shiftId == null || dayOfWeek == null) {
            return badRequest("roomId, shiftId, dayOfWeek không được để trống");
        }
        try {
            return ResponseEntity.ok(timetableService.updateEntry(id, roomId, shiftId, dayOfWeek));
        } catch (IllegalStateException e) {
            return badRequest(e.getMessage());
        }
    }

    /**
     * Xác nhận TKB (chuyển từ DRAFT sang CONFIRMED)
     */
    @PostMapping("/confirm")
    public ResponseEntity<?> confirmTimetable(@RequestBody Map<String, Object> payload) {
        Long semesterId = asLong(payload, "semesterId");
        if (semesterId == null) {
            return badRequest("semesterId không được để trống");
        }
        try {
            timetableService.confirmTimetable(semesterId);
            return ResponseEntity.ok(Map.of("message", "Đã xác nhận TKB thành công"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.internalServerError().body(Map.of("message", "Lỗi xác nhận TKB: " + msg));
        }
    }

    /**
     * Gợi ý slot trống khi chỉnh sửa thủ công
     */
    @GetMapping("/{id}/suggestions")
    public ResponseEntity<?> getSuggestions(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(timetableService.getSuggestions(id));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    /**
     * Xóa một entry TKB
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteEntry(@PathVariable Long id) {
        timetableService.deleteEntry(id);
        return ResponseEntity.ok(Map.of("message", "Đã xóa entry TKB"));
    }

    /**
     * Xuất TKB của một học kỳ ra file Excel (.xlsx).
     * Thứ tự: Thứ → Ca học → Phòng → Mã lớp HP.
     * Bao gồm 2 sheet: chi tiết TKB và thống kê tải giảng viên.
     */
    @GetMapping("/export")
    public ResponseEntity<InputStreamResource> exportTimetable(@RequestParam Long semesterId) {
        try {
            ByteArrayInputStream excelStream = timetableService.exportTimetableToExcel(semesterId);
            String filename = "TKB_HocKy_" + semesterId + ".xlsx";

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
            headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(new InputStreamResource(excelStream));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Gán lớp biên chế vào lớp học phần.
     * Body: { "adminClassIds": [1, 2, 3] }
     */
    @PostMapping("/sections/{sectionId}/admin-classes")
    public ResponseEntity<?> assignAdminClasses(
            @PathVariable Long sectionId,
            @RequestBody Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        List<Integer> rawIds = (List<Integer>) payload.get("adminClassIds");
        if (rawIds == null) {
            return badRequest("adminClassIds không được để trống");
        }
        try {
            List<Long> adminClassIds = rawIds.stream().mapToLong(Integer::longValue).boxed().toList();
            classSectionService.assignAdminClasses(sectionId, adminClassIds);
            return ResponseEntity.ok(Map.of("message", "Gán lớp biên chế thành công"));
        } catch (Exception e) {
            return badRequest(e.getMessage());
        }
    }
}
