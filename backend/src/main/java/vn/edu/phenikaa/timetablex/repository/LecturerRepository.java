package vn.edu.phenikaa.timetablex.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.edu.phenikaa.timetablex.entity.Lecturer;

import java.util.List;

public interface LecturerRepository extends JpaRepository<Lecturer, Long> {
    boolean existsByEmail(String email);
    List<Lecturer> findByFaculty_IdOrderByName(Long facultyId);
    
    @Query("SELECT l FROM Lecturer l JOIN FETCH l.faculty WHERE l.faculty.id IN :facultyIds ORDER BY l.faculty.code, l.name")
    List<Lecturer> findByFaculty_IdInOrderByName(java.util.List<Long> facultyIds);
}
