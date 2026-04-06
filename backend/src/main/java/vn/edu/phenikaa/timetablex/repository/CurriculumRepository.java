package vn.edu.phenikaa.timetablex.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;
import vn.edu.phenikaa.timetablex.entity.Curriculum;

import java.util.List;

@Repository
public interface CurriculumRepository extends JpaRepository<Curriculum, Long> {
    // Có thể tồn tại nhiều CTĐT cho cùng (major, cohort) → trả List để service tự
    // xử lý
    List<Curriculum> findByMajorIdAndCohort(Long majorId, String cohort);

    // Lấy tất cả CTĐT theo Khóa (niên khóa) bất kể ngành — dùng cho bước sinh lớp học phần,
    // để giới hạn lớp biên chế theo đúng ngành có CTĐT chứa học phần đó.
    List<Curriculum> findByCohort(String cohort);

    List<Curriculum> findByCohortIgnoreCase(String cohort);

    @EntityGraph(type = EntityGraph.EntityGraphType.LOAD, attributePaths = {
            "details",
            "details.course",
            "details.course.faculty",
            "major",
            "major.faculty"
    })
    @Query("SELECT DISTINCT c FROM Curriculum c")
    List<Curriculum> findAllWithDetailsAndCourses();

    @Query("SELECT DISTINCT c.cohort FROM Curriculum c ORDER BY c.cohort DESC")
    List<String> findAllCohorts();

    @EntityGraph(type = EntityGraph.EntityGraphType.LOAD, attributePaths = {
            "details",
            "details.course",
            "details.course.faculty",
            "major",
            "major.faculty"
    })
    @Query("SELECT DISTINCT c FROM Curriculum c WHERE c.major.faculty.id = :facultyId")
    List<Curriculum> findByMajor_Faculty_Id(Long facultyId);
}
