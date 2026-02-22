package vn.edu.phenikaa.timetablex.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rooms") // Đặt tên bảng số nhiều để tránh từ khóa SQL
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name; // VD: A2-301, A5-102

    @Column(nullable = false)
    private Integer capacity; // Sức chứa (VD: 60, 120)

    @Column(nullable = false)
    private String type;
    /*
     * Loại phòng / địa điểm học:
     *   LT     — Giảng đường lý thuyết
     *   PM     — Phòng máy tính
     *   TN     — Phòng thí nghiệm khoa học
     *   SB     — Sân bãi / Nhà thể chất (Thể dục thể thao)
     *   XT     — Xưởng thực hành (Cơ khí, Điện, Chế tạo...)
     *   BV     — Bệnh viện / Cơ sở y tế (Thực tập lâm sàng)
     *   DN     — Cơ sở thực tập doanh nghiệp (ngoài trường)
     *   ONLINE — Môi trường trực tuyến (ảo, không chiếm không gian vật lý)
     */
}