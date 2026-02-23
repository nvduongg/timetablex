package vn.edu.phenikaa.timetablex.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Lớp học phần - được sinh tự động từ CourseOffering đã APPROVED.
 * Mỗi học phần có thể có nhiều lớp LT (CSE702036-LT01, LT02...) và nhiều lớp
 * TH.
 */
@Entity
@Table(name = "class_sections")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassSection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mã lớp học phần, VD: CSE702036-LT01, CSE702036-TH01 */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /** Loại: LT (Lý thuyết) hoặc TH (Thực hành) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SectionType sectionType;

    /** Số thứ tự trong cùng loại (1, 2, 3...) */
    private Integer sectionIndex;

    @ManyToOne
    @JoinColumn(name = "course_offering_id", nullable = false)
    private CourseOffering courseOffering;

    /** Giảng viên được phân công (null nếu chưa phân công hoặc bỏ qua) */
    @ManyToOne
    @JoinColumn(name = "lecturer_id")
    private Lecturer lecturer;

    /**
     * Danh sách lớp biên chế được xếp vào lớp học phần này.
     * Một lớp HP có thể gộp nhiều lớp biên chế (merged class).
     * Một lớp biên chế có thể được xếp vào nhiều lớp HP khác nhau (các môn khác
     * nhau).
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "class_section_admin_classes", joinColumns = @JoinColumn(name = "class_section_id"), inverseJoinColumns = @JoinColumn(name = "admin_class_id"))
    @Builder.Default
    private Set<AdministrativeClass> administrativeClasses = new HashSet<>();

    /**
     * Bỏ qua phân công - thực hiện sau khi có TKB dự kiến (Post-scheduling
     * assignment)
     */
    @Builder.Default
    private Boolean skipAssignment = false;

    /** Yêu cầu hỗ trợ GV từ khoa khác — Khoa A thiếu GV, chuyển Khoa B (quản lý chuyên môn) phân công */
    @Builder.Default
    private Boolean needsSupport = false;

    /** Ghi chú khi gửi yêu cầu hỗ trợ (lý do, bối cảnh) */
    @Column(length = 500)
    private String supportRequestComment;

    /** Sĩ số dự kiến — tính tự động từ lớp biên chế hoặc nhập thủ công */
    private Integer expectedStudentCount;

    public enum SectionType {
        LT, // Lý thuyết
        TH // Thực hành
    }
}
