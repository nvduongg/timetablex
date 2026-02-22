package vn.edu.phenikaa.timetablex.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "majors")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Major {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code; // VD: 7480201 (Mã ngành CNTT)

    @Column(nullable = false)
    private String name; // VD: Công nghệ thông tin

    // Quan hệ: Nhiều ngành thuộc 1 Khoa
    @ManyToOne
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty;
}