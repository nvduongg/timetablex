package vn.edu.phenikaa.timetablex.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.phenikaa.timetablex.entity.Course;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {
    boolean existsByCode(String code);
    Optional<Course> findByCode(String code);
}