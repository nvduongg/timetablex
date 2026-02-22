package vn.edu.phenikaa.timetablex.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.phenikaa.timetablex.entity.Notification;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByFaculty_IdOrderByCreatedAtDesc(Long facultyId);
    long countByFaculty_IdAndReadFalse(Long facultyId);
}
