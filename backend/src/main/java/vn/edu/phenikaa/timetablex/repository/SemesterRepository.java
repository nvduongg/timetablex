package vn.edu.phenikaa.timetablex.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.phenikaa.timetablex.entity.Semester;

public interface SemesterRepository extends JpaRepository<Semester, Long> {
    Optional<Semester> findByIsActiveTrue();
    List<Semester> findAllByIsActiveTrue();
    boolean existsByCode(String code);
}