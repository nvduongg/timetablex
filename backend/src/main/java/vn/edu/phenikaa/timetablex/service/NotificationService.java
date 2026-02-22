package vn.edu.phenikaa.timetablex.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import vn.edu.phenikaa.timetablex.entity.Faculty;
import vn.edu.phenikaa.timetablex.entity.Notification;
import vn.edu.phenikaa.timetablex.repository.FacultyRepository;
import vn.edu.phenikaa.timetablex.repository.NotificationRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepo;
    @Autowired
    private FacultyRepository facultyRepo;

    public Notification create(Long facultyId, String title, String message, Long semesterId) {
        Faculty faculty = facultyRepo.findById(facultyId).orElseThrow();
        Notification n = Notification.builder()
                .title(title)
                .message(message)
                .faculty(faculty)
                .semesterId(semesterId)
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();
        return notificationRepo.save(n);
    }

    public List<Notification> getByFaculty(Long facultyId) {
        return notificationRepo.findByFaculty_IdOrderByCreatedAtDesc(facultyId);
    }

    public long countUnreadByFaculty(Long facultyId) {
        return notificationRepo.countByFaculty_IdAndReadFalse(facultyId);
    }

    public void markAsRead(Long id) {
        notificationRepo.findById(id).ifPresent(n -> {
            n.setRead(true);
            notificationRepo.save(n);
        });
    }
}
