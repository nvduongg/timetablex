package vn.edu.phenikaa.timetablex.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Role role;

    @ManyToOne
    @JoinColumn(name = "faculty_id")
    private Faculty faculty; // null nếu là ADMIN (P.ĐT)

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    public enum Role {
        ADMIN,  // Phòng Đào tạo
        FACULTY // Khoa/Viện
    }
}

