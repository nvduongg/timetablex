package vn.edu.phenikaa.timetablex.algorithm;

import vn.edu.phenikaa.timetablex.entity.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Quy đổi theo Quy chế đào tạo (tham chiếu phổ biến):
 * <ul>
 * <li>1 giờ định mức lên lớp = 50 phút</li>
 * <li>1 tín chỉ lý thuyết = 15 giờ; 1 tín chỉ thực hành = 30 giờ (trên cả học kỳ)</li>
 * </ul>
 * Số buổi/tuần suy ra từ khối lượng giờ/tuần và độ dài trung bình một ca (theo {@link TimeSlot}).
 */
public final class TimetableRegulationHelper {

    public static final int MINUTES_PER_REGULATION_HOUR = 50;
    public static final double LT_HOURS_PER_CREDIT = 15.0;
    public static final double TH_HOURS_PER_CREDIT = 30.0;

    private static final int WEEK_HOUR_OVERFLOW_PENALTY = 350;
    private static final int DAY_HOUR_OVERFLOW_PENALTY = 350;

    private TimetableRegulationHelper() {
    }

    /** Số tuần học kỳ (ước lượng từ ngày bắt đầu/kết thúc), tối thiểu 1. */
    public static int semesterWeeksFromDates(LocalDate start, LocalDate end) {
        if (start == null || end == null)
            return 15;
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        return (int) Math.max(1, Math.round(days / 7.0));
    }

    public static Map<Integer, TimeSlot> toPeriodMap(List<TimeSlot> timeSlots) {
        Map<Integer, TimeSlot> m = new HashMap<>();
        if (timeSlots == null)
            return m;
        for (TimeSlot ts : timeSlots) {
            if (ts.getPeriodIndex() != null)
                m.put(ts.getPeriodIndex(), ts);
        }
        return m;
    }

    public static long shiftDurationMinutes(Shift shift, Map<Integer, TimeSlot> periodMap) {
        if (shift == null || shift.getStartPeriod() == null || shift.getEndPeriod() == null)
            return 0;
        long total = 0;
        for (int p = shift.getStartPeriod(); p <= shift.getEndPeriod(); p++) {
            TimeSlot ts = periodMap.get(p);
            if (ts != null && ts.getStartTime() != null && ts.getEndTime() != null) {
                total += Duration.between(ts.getStartTime(), ts.getEndTime()).toMinutes();
            }
        }
        return total;
    }

    public static double regulationHoursFromMinutes(long minutes) {
        return minutes / (double) MINUTES_PER_REGULATION_HOUR;
    }

    public static double shiftRegulationHours(Shift shift, Map<Integer, TimeSlot> periodMap) {
        return regulationHoursFromMinutes(shiftDurationMinutes(shift, periodMap));
    }

    public static double averageShiftRegulationHours(List<Shift> shifts, Map<Integer, TimeSlot> periodMap) {
        if (shifts == null || shifts.isEmpty())
            return 0;
        double sum = 0;
        int n = 0;
        for (Shift sh : shifts) {
            double h = shiftRegulationHours(sh, periodMap);
            if (h > 0) {
                sum += h;
                n++;
            }
        }
        return n > 0 ? sum / n : 0;
    }

    /**
     * Số buổi học/tuần cần xếp cho lớp học phần, từ khối lượng tín chỉ và số tuần học kỳ.
     */
    public static int calcSessionsPerWeek(
            ClassSection section,
            Course course,
            int semesterWeeks,
            List<TimeSlot> timeSlots,
            List<Shift> shifts) {

        int weeks = Math.max(1, semesterWeeks);
        Map<Integer, TimeSlot> periodMap = toPeriodMap(timeSlots);
        double avgSessionHours = averageShiftRegulationHours(shifts, periodMap);
        if (avgSessionHours <= 0.01)
            avgSessionHours = 2.0;

        double tc = course.getTheoryCredits() != null ? course.getTheoryCredits() : 0;
        double pc = course.getPracticeCredits() != null ? course.getPracticeCredits() : 0;

        double weeklyRegulationHours;
        if (section.getSectionType() == ClassSection.SectionType.TH) {
            weeklyRegulationHours = pc * TH_HOURS_PER_CREDIT / weeks;
        } else {
            weeklyRegulationHours = tc * LT_HOURS_PER_CREDIT / weeks;
        }

        if (weeklyRegulationHours <= 0)
            return 1;

        int sessions = (int) Math.ceil(weeklyRegulationHours / avgSessionHours);
        return Math.min(8, Math.max(1, sessions));
    }

    /**
     * Ca học nằm ngoài khung giờ giảng dạy chính quy (mặc định 06:00–20:00).
     */
    public static boolean isShiftOutsideTeachingWindow(
            Shift shift,
            Map<Integer, TimeSlot> periodMap,
            LocalTime windowStart,
            LocalTime windowEnd) {

        if (shift == null || shift.getStartPeriod() == null || shift.getEndPeriod() == null)
            return false;
        TimeSlot first = periodMap.get(shift.getStartPeriod());
        TimeSlot last = periodMap.get(shift.getEndPeriod());
        if (first == null || last == null || first.getStartTime() == null || last.getEndTime() == null)
            return false;
        // Bắt đầu trước 06:00 hoặc kết thúc sau 20:00
        if (first.getStartTime().isBefore(windowStart))
            return true;
        if (last.getEndTime().isAfter(windowEnd))
            return true;
        return false;
    }

    public record RegulationPenaltyBreakdown(
            int penaltyLecturerWeek,
            int penaltyLecturerDay,
            int penaltyStudentWeek,
            int penaltyStudentDay,
            /** Số gene nằm ngoài khung 06:00–20:00 — cộng vào conflict để xử lý cứng */
            int outsideWindowViolations) {
    }

    /**
     * Phạt mềm: ngoài khung giờ; vượt giờ định mức/tuần hoặc/ngày (GV và — tùy cấu hình — sinh viên theo lớp biên chế).
     */
    public static RegulationPenaltyBreakdown computeRegulationPenalties(
            List<GeneticTimetableScheduler.Gene> genes,
            Map<Long, ClassSection> sectionMap,
            Map<Long, Shift> shiftMap,
            Map<Integer, TimeSlot> periodToSlot,
            TimetableRegulationConfig config) {

        int outsideCount = 0;
        LocalTime ws = config.teachingWindowStart();
        LocalTime we = config.teachingWindowEnd();

        Map<Long, Double> lectWeek = new HashMap<>();
        Map<Long, Map<Integer, Double>> lectDay = new HashMap<>();
        Map<Long, Double> studentWeek = new HashMap<>();
        Map<Long, Map<Integer, Double>> studentDay = new HashMap<>();

        for (GeneticTimetableScheduler.Gene g : genes) {
            Shift shift = shiftMap.get(g.shiftId);
            if (shift == null)
                continue;

            double hrs = shiftRegulationHours(shift, periodToSlot);
            if (hrs <= 0)
                hrs = 0.01;

            if (isShiftOutsideTeachingWindow(shift, periodToSlot, ws, we)) {
                outsideCount++;
            }

            ClassSection sec = sectionMap.get(g.sectionId);
            if (sec != null && sec.getLecturer() != null) {
                Long lid = sec.getLecturer().getId();
                lectWeek.merge(lid, hrs, Double::sum);
                lectDay.computeIfAbsent(lid, k -> new HashMap<>()).merge(g.dayOfWeek, hrs, Double::sum);
            }

            if (config.enforceStudentWorkload() && sec != null) {
                Set<AdministrativeClass> admins = sec.getAdministrativeClasses();
                if (admins != null && !admins.isEmpty()) {
                    for (AdministrativeClass ac : admins) {
                        if (ac == null || ac.getId() == null)
                            continue;
                        Long aid = ac.getId();
                        studentWeek.merge(aid, hrs, Double::sum);
                        studentDay.computeIfAbsent(aid, k -> new HashMap<>()).merge(g.dayOfWeek, hrs, Double::sum);
                    }
                }
            }
        }

        int penLw = 0;
        double maxW = config.maxWeeklyTeachingHours();
        for (double h : lectWeek.values()) {
            if (h > maxW)
                penLw += (int) Math.ceil((h - maxW) * WEEK_HOUR_OVERFLOW_PENALTY);
        }

        int penLd = 0;
        double maxD = config.maxDailyTeachingHours();
        for (Map<Integer, Double> dm : lectDay.values()) {
            for (double h : dm.values()) {
                if (h > maxD)
                    penLd += (int) Math.ceil((h - maxD) * DAY_HOUR_OVERFLOW_PENALTY);
            }
        }

        int penSw = 0;
        for (double h : studentWeek.values()) {
            if (h > maxW)
                penSw += (int) Math.ceil((h - maxW) * WEEK_HOUR_OVERFLOW_PENALTY);
        }

        int penSd = 0;
        for (Map<Integer, Double> dm : studentDay.values()) {
            for (double h : dm.values()) {
                if (h > maxD)
                    penSd += (int) Math.ceil((h - maxD) * DAY_HOUR_OVERFLOW_PENALTY);
            }
        }

        return new RegulationPenaltyBreakdown(penLw, penLd, penSw, penSd, outsideCount);
    }

    /**
     * Các loại phòng hợp lệ cho lớp TH.
     * {@code LT} — thực hành/tình huống trên lớp (pháp luật, seminar…), không phải phòng bệnh viện.
     */
    public static final Set<String> VALID_TH_ROOM_TYPES = Set.of(
            "LT", "PM", "TN", "SB", "XT", "BV", "DN", "ONLINE", "DA");

    public static List<String> parseCommaSeparatedMarkers(String config) {
        if (config == null || config.isBlank())
            return List.of();
        List<String> out = new ArrayList<>();
        for (String p : config.split(",")) {
            String t = p.trim();
            if (!t.isEmpty())
                out.add(t);
        }
        return out;
    }

    /**
     * Ca tối: tiết bắt đầu ≥ {@code startPeriodFrom} <b>hoặc</b> tên ca chứa một trong các chuỗi
     * cấu hình (vd. "Ca 5").
     */
    public static boolean isEveningShift(Shift shift, int startPeriodFrom, List<String> nameMarkers) {
        if (shift == null)
            return false;
        if (shift.getStartPeriod() != null && shift.getStartPeriod() >= startPeriodFrom)
            return true;
        if (nameMarkers == null || nameMarkers.isEmpty())
            return false;
        String name = shift.getName() != null ? shift.getName() : "";
        String nl = name.toLowerCase(Locale.ROOT);
        for (String m : nameMarkers) {
            if (m != null && !m.isBlank() && nl.contains(m.toLowerCase(Locale.ROOT).trim()))
                return true;
        }
        return false;
    }

    /** Chỉ E-learning 100% hoặc Coursera hybrid được xếp ca tối (theo quy ước trường). */
    public static boolean isEveningAllowedForCourse(Course course) {
        if (course == null || course.getLearningMethod() == null)
            return false;
        return course.getLearningMethod() == Course.LearningMethod.ONLINE_ELEARNING
                || course.getLearningMethod() == Course.LearningMethod.ONLINE_COURSERA;
    }

    /**
     * Loại phòng cần cho lớp HP: TH ưu tiên đúng {@code requiredRoomType} nếu hợp lệ;
     * nếu môn ghi {@code ONLINE} nhưng không phải {@code ONLINE_ELEARNING} thì TH vẫn xếp phòng vật lý (PM) —
     * áp dụng cho Coursera hybrid có buổi thực hành tại trường.
     * Ca lý thuyết: {@code ONLINE_ELEARNING} hoặc {@code ONLINE_COURSERA} cùng {@code requiredRoomType ONLINE}
     * thì không dùng giảng đường ({@code ONLINE}).
     */
    public static String determineRequiredRoomType(ClassSection section, Course course) {
        String rt = course.getRequiredRoomType();
        String rtU = rt != null ? rt.trim().toUpperCase(Locale.ROOT) : null;
        if (section.getSectionType() == ClassSection.SectionType.TH) {
            if (rtU != null && VALID_TH_ROOM_TYPES.contains(rtU)) {
                if ("ONLINE".equals(rtU) && course.getLearningMethod() != Course.LearningMethod.ONLINE_ELEARNING)
                    return "PM";
                return rtU;
            }
            return "PM";
        }
        // E-learning hoặc Coursera hybrid: nếu HP ghi ONLINE thì ca lý thuyết không xếp giảng đường.
        boolean onlineTheoryRoom = course.getLearningMethod() == Course.LearningMethod.ONLINE_ELEARNING
                || course.getLearningMethod() == Course.LearningMethod.ONLINE_COURSERA;
        if (onlineTheoryRoom && "ONLINE".equals(rtU))
            return "ONLINE";
        return "LT";
    }
}
