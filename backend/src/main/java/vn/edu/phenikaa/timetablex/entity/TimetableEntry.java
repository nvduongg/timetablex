package vn.edu.phenikaa.timetablex.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Bản ghi trong Thời khóa biểu - Kết quả xếp TKB tự động hoặc thủ công.
 * Mỗi ClassSection có thể có nhiều TimetableEntry (VD: LT học 2 buổi/tuần).
 * Đơn vị xếp lịch là Ca học (Shift): mỗi entry chiếm trọn một ca trong ngày.
 */
@Entity
@Table(name = "timetable_entries", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"class_section_id", "day_of_week", "shift_id"}),
    @UniqueConstraint(columnNames = {"room_id", "day_of_week", "shift_id", "semester_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimetableEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Lớp học phần được xếp */
    @ManyToOne
    @JoinColumn(name = "class_section_id", nullable = false)
    private ClassSection classSection;

    /** Phòng học được phân */
    @ManyToOne
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    /**
     * Ca học được xếp - đây là tiền đề xếp lịch (VD: Ca sáng tiết 1-5, Ca chiều tiết 6-10).
     * Một lớp học chiếm trọn ca học, không xếp chung ca với lớp khác cùng phòng/giảng viên.
     */
    @ManyToOne
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    /** Tiết bắt đầu của ca học (để hiển thị và tra cứu nhanh) */
    @Column(nullable = false)
    private Integer startPeriod;

    /** Tiết kết thúc của ca học (để hiển thị và tra cứu nhanh) */
    @Column(nullable = false)
    private Integer endPeriod;

    /**
     * TimeSlot đại diện (tiết đầu tiên của ca) - dùng cho sắp xếp hiển thị.
     * Lấy từ TimeSlot có periodIndex = shift.startPeriod.
     */
    @ManyToOne
    @JoinColumn(name = "time_slot_id")
    private TimeSlot timeSlot;

    /** Thứ trong tuần: 2=Thứ 2, 3=Thứ 3, ..., 7=Chủ nhật */
    @Column(nullable = false)
    private Integer dayOfWeek;

    /** Học kỳ (để query và validate conflict) */
    @ManyToOne
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    /** Trạng thái: DRAFT (dự kiến), CONFIRMED (đã xác nhận) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.DRAFT;

    /** Ghi chú (VD: "Tạm thời", "Đã điều chỉnh") */
    @Column(length = 500)
    private String note;

    public enum Status {
        DRAFT,      // TKB dự kiến (có thể chỉnh sửa)
        CONFIRMED   // TKB đã xác nhận (không thể chỉnh sửa)
    }
}
