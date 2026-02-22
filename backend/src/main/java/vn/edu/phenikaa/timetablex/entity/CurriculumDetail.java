package vn.edu.phenikaa.timetablex.entity;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonBackReference;

@Entity
@Table(name = "curriculum_details")
@Data
public class CurriculumDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "curriculum_id", nullable = false)
    @JsonBackReference // Ngắt vòng lặp JSON
    private Curriculum curriculum;

    @ManyToOne
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false)
    private String semesterIndex; // Học kỳ: "1", "1,2", "3"... (Hỗ trợ mở cho nhiều kỳ)
}