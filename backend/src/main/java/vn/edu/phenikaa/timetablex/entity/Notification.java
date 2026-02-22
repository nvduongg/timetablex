package vn.edu.phenikaa.timetablex.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Thông báo gửi đến Khoa/Viện (ví dụ: P.ĐT gửi danh sách học phần dự kiến yêu cầu xác nhận).
 * Có thể mở rộng gửi Email sau.
 */
@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String message;

    @ManyToOne
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty; // Khoa/Viện nhận thông báo

    private Long semesterId; // Học kỳ liên quan (để Khoa vào xem kế hoạch)

    private boolean read; // Đã đọc chưa

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
