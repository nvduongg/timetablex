package vn.edu.phenikaa.timetablex.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "departments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code; // Mã bộ môn (VD: KHMT, HTTT, DTVT)

    @Column(nullable = false)
    private String name; // Tên bộ môn (VD: Bộ môn Khoa học máy tính)

    // Bộ môn thuộc 1 Khoa
    @ManyToOne
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty;
}
