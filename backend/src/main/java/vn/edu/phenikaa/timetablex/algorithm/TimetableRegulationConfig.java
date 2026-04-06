package vn.edu.phenikaa.timetablex.algorithm;

import java.time.LocalTime;

/**
 * Tham số áp dụng Quy chế đào tạo cho bước xếp TKB (khối lượng giờ/tuần, khung giờ giảng).
 */
public record TimetableRegulationConfig(
        int semesterWeeks,
        double maxWeeklyTeachingHours,
        double maxDailyTeachingHours,
        LocalTime teachingWindowStart,
        LocalTime teachingWindowEnd,
        boolean enforceStudentWorkload) {

    public static TimetableRegulationConfig defaults() {
        return new TimetableRegulationConfig(
                15,
                15.0,
                4.0,
                LocalTime.of(6, 0),
                LocalTime.of(20, 0),
                true);
    }
}
