package vn.edu.phenikaa.timetablex.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.phenikaa.timetablex.entity.Department;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    List<Department> findByFaculty_IdOrderByName(Long facultyId);
    Optional<Department> findByCode(String code);
    boolean existsByCode(String code);
}
