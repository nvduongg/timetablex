package vn.edu.phenikaa.timetablex.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Thống kê tải giảng: số lớp học phần mỗi giảng viên đã được phân công */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeachingLoadDto {
    private Long lecturerId;
    private String lecturerName;
    private String lecturerEmail;
    private Integer sectionCount;
}
