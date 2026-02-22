package vn.edu.phenikaa.timetablex.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "administrative_classes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdministrativeClass {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code; // VD: K17-CNTT1

    @Column(nullable = false)
    private String name; // VD: Lớp CNTT 1 - K17

    private String cohort; // Khóa: K17, K18...

    private Integer studentCount; // Sĩ số (Quan trọng để chọn phòng học)

    @ManyToOne
    @JoinColumn(name = "major_id", nullable = false)
    private Major major; // Thuộc ngành nào
}