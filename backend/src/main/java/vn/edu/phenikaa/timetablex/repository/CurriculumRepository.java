package vn.edu.phenikaa.timetablex.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;
import vn.edu.phenikaa.timetablex.entity.Curriculum;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurriculumRepository extends JpaRepository<Curriculum, Long> {
    Optional<Curriculum> findByMajorIdAndCohort(Long majorId, String cohort);

    @EntityGraph(attributePaths = {"details", "details.course", "major"})
    @Query("SELECT c FROM Curriculum c")
    List<Curriculum> findAllWithDetailsAndCourses();

    @Query("SELECT DISTINCT c.cohort FROM Curriculum c ORDER BY c.cohort DESC")
    List<String> findAllCohorts();

    @EntityGraph(attributePaths = {"details", "details.course", "major"})
    @Query("SELECT c FROM Curriculum c WHERE c.major.faculty.id = :facultyId")
    List<Curriculum> findByMajor_Faculty_Id(Long facultyId);
}
