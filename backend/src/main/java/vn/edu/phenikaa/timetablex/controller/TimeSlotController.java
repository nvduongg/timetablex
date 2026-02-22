package vn.edu.phenikaa.timetablex.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.phenikaa.timetablex.entity.Shift;
import vn.edu.phenikaa.timetablex.entity.TimeSlot;
import vn.edu.phenikaa.timetablex.service.TimeSlotService;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/time-config")
public class TimeSlotController {

    @Autowired private TimeSlotService service;

    // --- Tiết học ---
    @GetMapping("/slots")
    public List<TimeSlot> getAllSlots() { return service.getAllSlots(); }

    @PostMapping("/slots")
    public TimeSlot createSlot(@RequestBody TimeSlot slot) { return service.saveSlot(slot); }

    @PutMapping("/slots/{id}") // Update logic
    public TimeSlot updateSlot(@PathVariable Long id, @RequestBody TimeSlot slot) {
        return service.updateSlot(id, slot);
    }

    @DeleteMapping("/slots/{id}")
    public void deleteSlot(@PathVariable Long id) { service.deleteSlot(id); }

    // --- Ca học ---
    @GetMapping("/shifts")
    public List<Shift> getAllShifts() { return service.getAllShifts(); }

    @PostMapping("/shifts")
    public Shift createShift(@RequestBody Shift shift) { return service.saveShift(shift); }

    @PutMapping("/shifts/{id}")
    public Shift updateShift(@PathVariable Long id, @RequestBody Shift shift) {
        return service.updateShift(id, shift);
    }

    @DeleteMapping("/shifts/{id}")
    public void deleteShift(@PathVariable Long id) { service.deleteShift(id); }

    // --- Excel ---
    @GetMapping("/template")
    public ResponseEntity<InputStreamResource> downloadTemplate() throws IOException {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=timeslot_template.xlsx")
                .body(new InputStreamResource(service.generateTemplate()));
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ResponseEntity<?> importExcel(@RequestParam("file") MultipartFile file) {
        try {
            service.importExcel(file);
            return ResponseEntity.ok("Import thành công");
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }
}