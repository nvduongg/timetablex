package vn.edu.phenikaa.timetablex.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.phenikaa.timetablex.entity.Course;
import vn.edu.phenikaa.timetablex.entity.CourseOffering;
import vn.edu.phenikaa.timetablex.entity.Semester;

import java.util.List;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, Long> {
    List<CourseOffering> findBySemesterId(Long semesterId);
    List<CourseOffering> findBySemesterIdAndFacultyId(Long semesterId, Long facultyId);
    List<CourseOffering> findBySemesterIdAndStatus(Long semesterId, CourseOffering.Status status);
    List<CourseOffering> findBySemesterIdAndFacultyIdAndStatus(Long semesterId, Long facultyId, CourseOffering.Status status);
    boolean existsBySemesterAndCourse(Semester semester, Course course);
    boolean existsByCourse(Course course);
}
