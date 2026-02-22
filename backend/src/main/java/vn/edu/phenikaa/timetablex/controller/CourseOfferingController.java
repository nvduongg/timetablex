package vn.edu.phenikaa.timetablex.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.phenikaa.timetablex.dto.AutoGenerateRequest;
import vn.edu.phenikaa.timetablex.entity.CourseOffering;
import vn.edu.phenikaa.timetablex.service.CourseOfferingService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/offerings")
public class CourseOfferingController {

    @Autowired
    private CourseOfferingService offeringService;

    @GetMapping
    public List<CourseOffering> getBySemester(
            @RequestParam Long semesterId,
            @RequestParam(required = false) Long facultyId,
            @RequestParam(required = false) String status) {
        CourseOffering.Status statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = CourseOffering.Status.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        if (facultyId != null || statusEnum != null)
            return offeringService.getBySemester(semesterId, facultyId, statusEnum);
        return offeringService.getBySemester(semesterId);
    }

    @PutMapping("/{id}")
    public CourseOffering updatePlan(@PathVariable Long id, @RequestBody Map<String, Integer> payload) {
        return offeringService.updatePlan(id,
                payload.get("theoryClassCount"),
                payload.get("practiceClassCount"),
                payload.get("studentDemand"));
    }

    @PostMapping("/generate")
    public void generateAutomatedPlan(@RequestBody AutoGenerateRequest request) {
        offeringService.generateAutomatedPlan(request);
    }

    @PostMapping("/send-for-approval")
    public ResponseEntity<Map<String, String>> sendForApproval(@RequestBody Map<String, Object> payload) {
        try {
            Object semObj = payload.get("semesterId");
            Long semesterId = (semObj instanceof Number n) ? n.longValue() : null;
            if (semesterId == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("message", "semesterId không được để trống"));
            }
            List<Long> offeringIds = null;
            Object idsObj = payload.get("offeringIds");
            if (idsObj instanceof List<?> list && !list.isEmpty()) {
                List<Long> parsed = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof Number num) {
                        parsed.add(num.longValue());
                    } else if (o != null) {
                        parsed.add(Long.parseLong(o.toString()));
                    }
                }
                offeringIds = parsed.isEmpty() ? null : parsed;
            }
            offeringService.sendForApproval(semesterId, offeringIds);
            return ResponseEntity.ok(Map.of("message", "Đã gửi yêu cầu duyệt thành công"));
        } catch (Exception e) {
            String msg = e.getMessage() != null && !e.getMessage().isBlank()
                ? e.getMessage()
                : (e.getCause() != null ? e.getCause().getMessage() : "Lỗi khi gửi yêu cầu duyệt");
            return ResponseEntity.badRequest()
                .body(Map.of("message", msg != null ? msg : "Lỗi khi gửi yêu cầu duyệt"));
        }
    }

    @PutMapping("/{id}/status")
    public CourseOffering updateStatus(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        CourseOffering.Status status = CourseOffering.Status.valueOf(payload.get("status"));
        return offeringService.updateStatus(id, status, payload.get("rejectionComment"));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void importExcel(@RequestParam Long semesterId, @RequestParam("file") MultipartFile file) throws IOException {
        offeringService.importExcel(semesterId, file);
    }

    @GetMapping("/import/template")
    public ResponseEntity<byte[]> downloadImportTemplate() throws IOException {
        ByteArrayInputStream stream = offeringService.generateImportTemplate();
        byte[] bytes = stream.readAllBytes();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=BM.DT.01.01_Danh_sach_hoc_phan_du_kien.xlsx");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
