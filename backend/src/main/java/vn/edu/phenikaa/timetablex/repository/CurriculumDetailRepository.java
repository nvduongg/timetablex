package vn.edu.phenikaa.timetablex.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import vn.edu.phenikaa.timetablex.entity.Course;
import vn.edu.phenikaa.timetablex.entity.Curriculum;
import vn.edu.phenikaa.timetablex.entity.CurriculumDetail;

import java.util.List;

@Repository
public interface CurriculumDetailRepository extends JpaRepository<CurriculumDetail, Long> {
    List<CurriculumDetail> findByCurriculumId(Long curriculumId);
    boolean existsByCurriculumAndCourse(Curriculum c, Course co);
    boolean existsByCourse(Course course);
}
