package vn.edu.phenikaa.timetablex.dto;
import lombok.Data;

import java.util.List;

@Data
public class AutoGenerateRequest {
    private Long semesterId;

    /** Danh sách Khóa (niên khóa) cần lập kế hoạch (VD: K17, K18...) */
    private List<String> cohortCodes;

    /** Năm học đang lập kế hoạch (VD: 2025 nghĩa là năm học 2025-2026) */
    private Integer planningYear;
    /** Kỳ trong năm đang lập KH (VD: 2 = kỳ 2 của năm đó) */
    private Integer planningTerm;
    /** Số kỳ mỗi năm học của trường (thường là 2 hoặc 3) */
    private Integer termsPerYear;
}