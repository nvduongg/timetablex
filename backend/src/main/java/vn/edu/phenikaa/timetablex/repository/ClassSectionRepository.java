package vn.edu.phenikaa.timetablex.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.edu.phenikaa.timetablex.entity.ClassSection;

import java.util.List;

public interface ClassSectionRepository extends JpaRepository<ClassSection, Long> {
    @Query("SELECT s FROM ClassSection s JOIN FETCH s.courseOffering o JOIN FETCH o.course c JOIN FETCH c.faculty JOIN FETCH o.faculty LEFT JOIN FETCH s.lecturer LEFT JOIN FETCH s.administrativeClasses WHERE o.semester.id = :semesterId")
    List<ClassSection> findByCourseOffering_Semester_Id(Long semesterId);

    // Lấy các lớp học phần theo học kỳ + Khoa:
    // - Các lớp do Khoa đó phụ trách kế hoạch (o.faculty.id = facultyId)
    // - Cộng thêm các lớp cần hỗ trợ (needsSupport = true) của các Khoa khác
    // nhưng học phần thuộc Khoa này quản lý chuyên môn (c.faculty.id = facultyId)
    @Query("""
            SELECT DISTINCT s
            FROM ClassSection s
            JOIN FETCH s.courseOffering o
            JOIN FETCH o.course c
            JOIN FETCH c.faculty
            JOIN FETCH o.faculty
            LEFT JOIN FETCH s.lecturer
            LEFT JOIN FETCH s.administrativeClasses
            WHERE o.semester.id = :semesterId
              AND (
                  o.faculty.id = :facultyId
                  OR (c.faculty.id = :facultyId AND s.needsSupport = true)
              )
            """)
    List<ClassSection> findBySemesterAndFaculty(Long semesterId, Long facultyId);

    @Query("SELECT s FROM ClassSection s JOIN FETCH s.courseOffering o JOIN FETCH o.course JOIN FETCH o.faculty LEFT JOIN FETCH s.lecturer LEFT JOIN FETCH s.administrativeClasses WHERE o.id = :offeringId")
    List<ClassSection> findByCourseOffering_Id(Long offeringId);

    boolean existsByCode(String code);
}
