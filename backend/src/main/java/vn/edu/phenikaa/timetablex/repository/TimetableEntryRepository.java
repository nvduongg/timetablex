package vn.edu.phenikaa.timetablex.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.phenikaa.timetablex.entity.TimetableEntry;

import java.util.List;

public interface TimetableEntryRepository extends JpaRepository<TimetableEntry, Long> {

  /**
   * Lấy tất cả TKB của một học kỳ (kèm đầy đủ thông tin shift, startPeriod,
   * endPeriod).
   * Dùng LEFT JOIN FETCH cho lecturer và timeSlot vì có thể null.
   */
  @Query("""
      SELECT t FROM TimetableEntry t
      JOIN FETCH t.classSection cs
      JOIN FETCH cs.courseOffering o
      JOIN FETCH o.course
      LEFT JOIN FETCH cs.lecturer
      LEFT JOIN FETCH cs.administrativeClasses
      JOIN FETCH t.room
      JOIN FETCH t.shift
      LEFT JOIN FETCH t.timeSlot
      WHERE t.semester.id = :semesterId
      ORDER BY t.dayOfWeek, t.shift.startPeriod
      """)
  List<TimetableEntry> findBySemesterId(Long semesterId);

  /**
   * Lấy TKB của một lớp học phần.
   */
  @Query("""
      SELECT t FROM TimetableEntry t
      JOIN FETCH t.room
      JOIN FETCH t.shift
      LEFT JOIN FETCH t.timeSlot
      WHERE t.classSection.id = :sectionId
      ORDER BY t.dayOfWeek, t.shift.startPeriod
      """)
  List<TimetableEntry> findByClassSectionId(Long sectionId);

  /**
   * Kiểm tra conflict theo CA HỌC: Phòng đã được dùng trong ca này chưa?
   * (loại trừ entryId nếu đang chỉnh sửa)
   */
  @Query("""
      SELECT COUNT(t) > 0 FROM TimetableEntry t
      WHERE t.room.id = :roomId
        AND t.dayOfWeek = :dayOfWeek
        AND t.shift.id = :shiftId
        AND t.semester.id = :semesterId
        AND (:excludeEntryId IS NULL OR t.id != :excludeEntryId)
      """)
  boolean existsByRoomAndShiftAndDay(Long roomId, Integer dayOfWeek, Long shiftId,
      Long semesterId, Long excludeEntryId);

  /**
   * Kiểm tra conflict theo CA HỌC: Giảng viên đã có lớp trong ca này chưa?
   * (loại trừ entryId nếu đang chỉnh sửa)
   */
  @Query("""
      SELECT COUNT(t) > 0 FROM TimetableEntry t
      JOIN t.classSection cs
      WHERE cs.lecturer.id = :lecturerId
        AND t.dayOfWeek = :dayOfWeek
        AND t.shift.id = :shiftId
        AND t.semester.id = :semesterId
        AND (:excludeEntryId IS NULL OR t.id != :excludeEntryId)
      """)
  boolean existsByLecturerAndShiftAndDay(Long lecturerId, Integer dayOfWeek, Long shiftId,
      Long semesterId, Long excludeEntryId);

  /** Xóa tất cả TKB dự kiến của một học kỳ (để tạo lại) */
  @Modifying
  @Query("DELETE FROM TimetableEntry t WHERE t.semester.id = :semesterId AND t.status = :status")
  void clearDraftBySemester(@Param("semesterId") Long semesterId, @Param("status") TimetableEntry.Status status);

  /** Xóa toàn bộ TKB của học kỳ (DRAFT + CONFIRMED) để xếp lại từ đầu */
  @Modifying
  @Query("DELETE FROM TimetableEntry t WHERE t.semester.id = :semesterId")
  void deleteAllBySemesterId(@Param("semesterId") Long semesterId);

  /**
   * Lấy TKB cho một lớp biên chế (adminClassId) trong một học kỳ.
   * Join qua quan hệ nhiều-nhiều giữa ClassSection và AdministrativeClass.
   */
  @Query("""
      SELECT t FROM TimetableEntry t
      JOIN FETCH t.classSection cs
      JOIN FETCH cs.courseOffering o
      JOIN FETCH o.course
      LEFT JOIN FETCH cs.lecturer
      JOIN FETCH t.room
      JOIN FETCH t.shift
      LEFT JOIN FETCH t.timeSlot
      JOIN cs.administrativeClasses ac
      WHERE t.semester.id = :semesterId
        AND ac.id = :adminClassId
      ORDER BY t.dayOfWeek, t.shift.startPeriod
      """)
  List<TimetableEntry> findBySemesterAndAdminClass(
      @Param("semesterId") Long semesterId,
      @Param("adminClassId") Long adminClassId);
}
