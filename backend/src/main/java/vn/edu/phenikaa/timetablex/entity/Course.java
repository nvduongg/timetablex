package vn.edu.phenikaa.timetablex.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "courses")
@Data
public class Course {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code; // VD: CSE702036

    @Column(nullable = false)
    private String name; // VD: Mạng máy tính

    // --- CẤU TRÚC TÍN CHỈ MỞ RỘNG ---
    private Double credits; // Tổng tín chỉ (VD: 2.0)
    private Double theoryCredits; // Tín chỉ Lý thuyết
    private Double practiceCredits; // Tín chỉ Thực hành
    private Double selfStudyCredits; // Tín chỉ Tự học (Mới - Quan trọng cho E-learning)

    // --- PHƯƠNG THỨC HỌC TẬP (MỚI) ---
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private LearningMethod learningMethod;

    // Yêu cầu loại phòng (Vẫn giữ để xếp lịch thi hoặc lịch offline xen kẽ)
    private String requiredRoomType;

    /** Các Khoa khác có thể cung cấp GV cho môn này (VD: Tin học văn phòng - GV từ KHMT, CNTT có thể dạy dù môn thuộc HTTT) */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "course_shared_faculties",
            joinColumns = @JoinColumn(name = "course_id"),
            inverseJoinColumns = @JoinColumn(name = "faculty_id"))
    private java.util.Set<Faculty> sharedFaculties = new java.util.HashSet<>();

    @ManyToOne
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty;

    // Enum định nghĩa các hình thức học
    public enum LearningMethod {
        OFFLINE,           // Học truyền thống tại lớp
        ONLINE_ELEARNING,  // 100% online (Canvas + vài buổi MS Teams), không cần phòng vật lý
        ONLINE_COURSERA,   // Hybrid: học online Coursera ở nhà + xếp offline tại trường tùy số buổi từng môn
        HYBRID             // Kết hợp (các trường hợp hybrid khác)
    }
}