package vn.edu.phenikaa.timetablex.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "semesters")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Semester {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code; // VD: 2025_1 (Năm 2025 kỳ 1)

    @Column(nullable = false)
    private String name; // VD: Học kỳ 1 - Năm học 2025-2026

    @Column(nullable = false)
    private LocalDate startDate; // Ngày bắt đầu

    @Column(nullable = false)
    private LocalDate endDate;   // Ngày kết thúc

    private Boolean isActive = false; // Trạng thái: Đang diễn ra hay không
}