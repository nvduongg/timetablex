package vn.edu.phenikaa.timetablex.dto;
import lombok.Data;

@Data
public class AutoGenerateRequest {
    private Long semesterId;
    // Map: Khóa K17 -> đang học Kỳ 1 (của khung chương trình)
    // Ví dụ: K17 học kỳ 1, K16 học kỳ 3...
    private String cohort; // VD: "K17"
    private Integer semesterIndex; // VD: 1
}