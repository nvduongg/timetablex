package vn.edu.phenikaa.timetablex.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.phenikaa.timetablex.entity.AdministrativeClass;

import java.util.Collection;
import java.util.List;

public interface AdministrativeClassRepository extends JpaRepository<AdministrativeClass, Long> {
    boolean existsByCode(String code);

    /** Lấy tất cả lớp biên chế thuộc một khoa (qua Major → Faculty) */
    List<AdministrativeClass> findByMajor_Faculty_Id(Long facultyId);

    /** BC thuộc một trong các ngành (dùng khi gán lớp HP theo CTĐT liên khoa) */
    List<AdministrativeClass> findByMajor_IdIn(Collection<Long> majorIds);
}