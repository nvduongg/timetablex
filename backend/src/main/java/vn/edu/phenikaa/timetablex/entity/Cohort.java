package vn.edu.phenikaa.timetablex.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "cohorts")
@Data
public class Cohort {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code; // VD: K17, K18...

    @Column(length = 100)
    private String name; // VD: Khóa 17 - Kỹ thuật phần mềm

    /** Năm nhập học của khóa này (VD: 2024 với K18).
     *  Có thể dùng chung với logic nội suy học kỳ hiện tại. */
    private Integer admissionYear;

    private Boolean active = true;

    @Column(length = 255)
    private String note;
}

