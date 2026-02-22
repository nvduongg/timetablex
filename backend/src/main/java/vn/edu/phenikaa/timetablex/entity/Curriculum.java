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

    @OneToMany(mappedBy = "curriculum", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference // Để tránh vòng lặp vô tận khi serialize JSON
    private List<CurriculumDetail> details;
}