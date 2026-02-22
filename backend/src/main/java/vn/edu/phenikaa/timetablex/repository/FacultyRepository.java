package vn.edu.phenikaa.timetablex.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.phenikaa.timetablex.entity.Faculty;

public interface FacultyRepository extends JpaRepository<Faculty, Long> {
    boolean existsByCode(String code);
}