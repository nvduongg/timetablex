package vn.edu.phenikaa.timetablex.dto;
import lombok.Data;

@Data
public class AutoGenerateRequest {
    private Long semesterId;

    /** Năm học đang lập kế hoạch (VD: 2025 nghĩa là năm học 2025-2026) */
    private Integer planningYear;
    /** Kỳ trong năm đang lập KH (VD: 2 = kỳ 2 của năm đó) */
    private Integer planningTerm;
    /** Số kỳ mỗi năm học của trường (thường là 2 hoặc 3) */
    private Integer termsPerYear;
}