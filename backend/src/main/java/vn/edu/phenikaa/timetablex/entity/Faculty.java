package vn.edu.phenikaa.timetablex.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "faculties")
@Data // Lombok getter/setter
@NoArgsConstructor
@AllArgsConstructor
public class Faculty {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code; // Mã khoa (VD: CNTT)

    @Column(nullable = false)
    private String name; // Tên khoa (VD: Khoa Công nghệ thông tin)

    /** Cho phép Khoa bỏ qua bước phân công (phân công sau khi có TKB dự kiến) */
    private Boolean allowSkipAssignment = false;
}