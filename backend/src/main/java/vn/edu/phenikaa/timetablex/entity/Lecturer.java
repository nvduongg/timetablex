package vn.edu.phenikaa.timetablex.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "lecturers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Lecturer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // VD: Mai Thủy Nga

    @Column(nullable = false, unique = true)
    private String email; // VD: nga.maithuy@...

    // Giảng viên thuộc 1 Khoa
    @ManyToOne
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty;

    // MA TRẬN CHUYÊN MÔN: Giảng viên dạy được nhiều môn
    @ManyToMany(fetch = FetchType.EAGER) // Load luôn danh sách môn khi query giảng viên
    @JoinTable(
        name = "lecturer_courses",
        joinColumns = @JoinColumn(name = "lecturer_id"),
        inverseJoinColumns = @JoinColumn(name = "course_id")
    )
    private Set<Course> courses = new HashSet<>();
}