package vn.edu.phenikaa.timetablex.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "course_offerings")
@Data
public class CourseOffering {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester; // Kế hoạch cho học kỳ nào

    @ManyToOne
    @JoinColumn(name = "course_id", nullable = false)
    private Course course; // Môn gì

    @ManyToOne
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty; // Khoa nào phụ trách môn này (để Khoa đó vào xác nhận ở Bước 2)

    private Integer theoryClassCount; // Số lớp lý thuyết dự kiến
    private Integer practiceClassCount; // Số lớp thực hành dự kiến

    private Integer studentDemand; // Tổng nhu cầu sinh viên (VD: 180 sv)

    @Enumerated(EnumType.STRING)
    private Status status; // Trạng thái quy trình

    @Column(length = 500)
    private String rejectionComment; // Ghi chú khi Khoa từ chối / yêu cầu chỉnh sửa

    private LocalDateTime submittedAt; // Thời điểm P.ĐT gửi duyệt (để ràng buộc 03 ngày làm việc)

    public enum Status {
        DRAFT, // PĐT mới tạo (Bước 1)
        WAITING_APPROVAL, // Đã gửi cho Khoa (Bước 2)
        APPROVED, // Khoa đã chốt (Xong Bước 2)
        REJECTED // Khoa yêu cầu sửa
    }
}