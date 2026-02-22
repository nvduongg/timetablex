package vn.edu.phenikaa.timetablex.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "shifts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Shift {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // VD: Ca 1 (Sáng), Ca 2 (Sáng)

    private Integer startPeriod; // Bắt đầu từ tiết mấy (VD: 1)
    private Integer endPeriod;   // Kết thúc ở tiết mấy (VD: 3)
}