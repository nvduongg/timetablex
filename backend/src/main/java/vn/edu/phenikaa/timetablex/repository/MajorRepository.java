package vn.edu.phenikaa.timetablex.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.phenikaa.timetablex.entity.Major;

import java.util.List;
import vn.edu.phenikaa.timetablex.entity.Major;

public interface MajorRepository extends JpaRepository<Major, Long> {
    boolean existsByCode(String code);
    List<Major> findByFaculty_IdOrderByCode(Long facultyId);
}