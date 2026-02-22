package vn.edu.phenikaa.timetablex.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalTime;

@Entity
@Table(name = "time_slots")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeSlot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Integer periodIndex; // Tiết số mấy (1, 2, 3...)

    @Column(nullable = false)
    private String name; // Tên hiển thị (VD: Tiết 1)

    @Column(nullable = false)
    private LocalTime startTime; // 06:45

    @Column(nullable = false)
    private LocalTime endTime;   // 07:35
}