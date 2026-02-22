package vn.edu.phenikaa.timetablex.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.phenikaa.timetablex.entity.Room;
import vn.edu.phenikaa.timetablex.service.RoomService;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    @Autowired
    private RoomService roomService;

    @GetMapping
    public List<Room> getAll() {
        return roomService.getAll();
    }

    @PostMapping
    public Room create(@RequestBody Room room) {
        return roomService.save(room);
    }

    @PutMapping("/{id}")
    public Room update(@PathVariable Long id, @RequestBody Room room) {
        return roomService.update(id, room);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        roomService.delete(id);
    }

    @GetMapping("/template")
    public ResponseEntity<InputStreamResource> downloadTemplate() throws IOException {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=room_template.xlsx")
                .body(new InputStreamResource(roomService.generateTemplate()));
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ResponseEntity<?> importExcel(@RequestParam("file") MultipartFile file) {
        try {
            roomService.importExcel(file);
            return ResponseEntity.ok("Import thành công");
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }
}