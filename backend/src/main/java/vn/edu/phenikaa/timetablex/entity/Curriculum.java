package vn.edu.phenikaa.timetablex.entity;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import java.util.List;

@Entity
@Table(name = "curriculums")
@Data
public class Curriculum {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // VD: K17 - Kỹ thuật phần mềm

    @ManyToOne
    @JoinColumn(name = "major_id", nullable = false)
    private Major major;

    private String cohort; // Khóa: K17

    /** Năm nhập học của khóa này (VD: 2024 với K18).
     *  Dùng để tự động nội suy học kỳ hiện tại khi lập kế hoạch mở lớp toàn trường. */
    private Integer admissionYear;


    @OneToMany(mappedBy = "curriculum", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference // Để tránh vòng lặp vô tận khi serialize JSON
    private List<CurriculumDetail> details;
}