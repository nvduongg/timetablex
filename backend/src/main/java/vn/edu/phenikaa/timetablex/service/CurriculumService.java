package vn.edu.phenikaa.timetablex.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.phenikaa.timetablex.entity.*;
import vn.edu.phenikaa.timetablex.repository.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

@Service
public class CurriculumService {
    @Autowired
    private CurriculumRepository curriculumRepo;
    @Autowired
    private CurriculumDetailRepository detailRepo;
    @Autowired
    private CourseRepository courseRepo;
    @Autowired
    private CohortRepository cohortRepo;

    public List<Curriculum> getAll() {
        return curriculumRepo.findAllWithDetailsAndCourses();
    }

    public List<Curriculum> getAll(Long facultyId) {
        if (facultyId == null) return curriculumRepo.findAllWithDetailsAndCourses();
        return curriculumRepo.findByMajor_Faculty_Id(facultyId);
    }

    public Curriculum create(Curriculum curr) {
        // Nếu frontend gửi kèm cohortRef (id Niên khóa) thì tự đồng bộ
        if (curr.getCohortRef() != null && curr.getCohortRef().getId() != null) {
            Long cohortId = curr.getCohortRef().getId();
            Cohort cohort = cohortRepo.findById(cohortId).orElse(null);
            if (cohort != null) {
                if (curr.getCohort() == null || curr.getCohort().isBlank()) {
                    curr.setCohort(cohort.getCode());
                }
                if (curr.getAdmissionYear() == null && cohort.getAdmissionYear() != null) {
                    curr.setAdmissionYear(cohort.getAdmissionYear());
                }
            }
        }
        return curriculumRepo.save(curr);
    }

    public void delete(Long id) {
        curriculumRepo.deleteById(id);
    }

    public boolean belongsToFaculty(Long curriculumId, Long facultyId) {
        if (facultyId == null) return true;
        return curriculumRepo.findById(curriculumId)
                .map(c -> c.getMajor() != null && c.getMajor().getFaculty() != null
                        && c.getMajor().getFaculty().getId().equals(facultyId))
                .orElse(false);
    }

    /** Kiểm tra detail thuộc CTĐT của khoa (dùng cho removeDetail) */
    public boolean detailBelongsToFaculty(Long detailId, Long facultyId) {
        if (facultyId == null) return true;
        return detailRepo.findById(detailId)
                .map(d -> d.getCurriculum() != null && belongsToFaculty(d.getCurriculum().getId(), facultyId))
                .orElse(false);
    }

    public void removeDetail(Long detailId) {
        detailRepo.deleteById(detailId);
    }

    @Transactional
    public CurriculumDetail addDetail(Long curriculumId, Long courseId, String semesterIndex) {
        Curriculum curriculum = curriculumRepo.findById(curriculumId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy CTĐT"));
        Course course = courseRepo.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy môn học"));

        if (detailRepo.existsByCurriculumAndCourse(curriculum, course)) {
            throw new RuntimeException("Môn học " + course.getCode() + " đã có trong khung CTĐT");
        }

        CurriculumDetail detail = new CurriculumDetail();
        detail.setCurriculum(curriculum);
        detail.setCourse(course);
        detail.setSemesterIndex(semesterIndex != null ? semesterIndex.trim() : "1");
        return detailRepo.save(detail);
    }

    public ByteArrayInputStream generateRoadmapTemplate() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Sheet sheet = workbook.createSheet("Lộ trình");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Học kỳ (số, ví dụ 1–20; Y khoa thường 15 kỳ)");
            header.createCell(1).setCellValue("Mã học phần");
            header.createCell(2).setCellValue("Tên học phần (gợi ý, không bắt buộc)");

            workbook.write(out);
        } finally {
            workbook.close();
        }
        return new ByteArrayInputStream(out.toByteArray());
    }

    @Transactional
    public void importRoadmap(Long curriculumId, MultipartFile file) throws IOException {
        Curriculum curriculum = curriculumRepo.findById(curriculumId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy CTĐT"));

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            // Map: courseId -> tập các học kỳ (merge khi cùng môn xuất hiện nhiều dòng)
            Map<Long, TreeSet<String>> courseToSemesters = new LinkedHashMap<>();

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;

                Cell semCell = row.getCell(0);
                Cell codeCell = row.getCell(1);

                if (semCell != null && codeCell != null) {
                    String semesterStr = getCellValue(semCell);
                    String courseCode = getCellValue(codeCell);

                    if (semesterStr == null || courseCode == null) continue;

                    // Chuẩn hóa format: "1", "1,2", "1;2" → ["1","2"]; chỉ lấy số dương
                    String[] parts = semesterStr.replace(";", ",").split(",");
                    Set<String> semestersFromRow = new LinkedHashSet<>();
                    for (String p : parts) {
                        String s = p.trim();
                        if (s.matches("\\d+")) semestersFromRow.add(s);
                    }
                    if (semestersFromRow.isEmpty()) continue;

                    Optional<Course> courseOpt = courseRepo.findAll().stream()
                            .filter(c -> c.getCode().equalsIgnoreCase(courseCode))
                            .findFirst();

                    if (courseOpt.isPresent()) {
                        Long cid = courseOpt.get().getId();
                        courseToSemesters.computeIfAbsent(cid, k -> new TreeSet<>(Comparator.comparingInt(Integer::parseInt)))
                                .addAll(semestersFromRow);
                    }
                }
            }

            // Tạo / cập nhật detail: mỗi môn 1 record, semesterIndex gộp các kỳ (có thể >12, vd. Y khoa)
            List<CurriculumDetail> toSave = new ArrayList<>();
            List<CurriculumDetail> existingDetails = curriculum.getDetails();
            Map<Long, CurriculumDetail> existingByCourse = new HashMap<>();
            for (CurriculumDetail d : existingDetails) {
                existingByCourse.put(d.getCourse().getId(), d);
            }

            for (Map.Entry<Long, TreeSet<String>> e : courseToSemesters.entrySet()) {
                Long courseId = e.getKey();
                String merged = String.join(",", e.getValue());

                CurriculumDetail existing = existingByCourse.get(courseId);
                if (existing != null) {
                    // Gộp với học kỳ đã có
                    Set<String> all = new TreeSet<>(Comparator.comparingInt(Integer::parseInt));
                    for (String s : merged.split(",")) {
                        String t = s.trim();
                        if (t.matches("\\d+")) all.add(t);
                    }
                    String prev = existing.getSemesterIndex();
                    if (prev != null) {
                        for (String s : prev.split(",")) {
                            String t = s.trim();
                            if (t.matches("\\d+")) all.add(t);
                        }
                    }
                    existing.setSemesterIndex(String.join(",", all));
                    toSave.add(existing);
                    existingByCourse.remove(courseId); // tránh cập nhật 2 lần
                } else {
                    Course course = courseRepo.findById(courseId).orElseThrow();
                    CurriculumDetail detail = new CurriculumDetail();
                    detail.setCurriculum(curriculum);
                    detail.setCourse(course);
                    detail.setSemesterIndex(merged);
                    toSave.add(detail);
                }
            }
            detailRepo.saveAll(toSave);
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return null;
        CellType type = cell.getCellType();
        if (type == CellType.STRING) return cell.getStringCellValue().trim();
        if (type == CellType.NUMERIC) return String.valueOf(Math.round(cell.getNumericCellValue()));
        return null;
    }
}
