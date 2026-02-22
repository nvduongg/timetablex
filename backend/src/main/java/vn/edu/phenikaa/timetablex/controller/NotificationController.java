package vn.edu.phenikaa.timetablex.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import vn.edu.phenikaa.timetablex.entity.Notification;
import vn.edu.phenikaa.timetablex.service.NotificationService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @GetMapping
    public List<Notification> getByFaculty(@RequestParam Long facultyId) {
        return notificationService.getByFaculty(facultyId);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount(@RequestParam Long facultyId) {
        long count = notificationService.countUnreadByFaculty(facultyId);
        return Map.of("count", count);
    }

    @PutMapping("/{id}/read")
    public void markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
    }
}
