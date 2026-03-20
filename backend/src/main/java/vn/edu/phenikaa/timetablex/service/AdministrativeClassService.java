package vn.edu.phenikaa.timetablex.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.phenikaa.timetablex.entity.AdministrativeClass;
import vn.edu.phenikaa.timetablex.entity.Cohort;
import vn.edu.phenikaa.timetablex.entity.Major;
import vn.edu.phenikaa.timetablex.repository.AdministrativeClassRepository;
import vn.edu.phenikaa.timetablex.repository.CohortRepository;
import vn.edu.phenikaa.timetablex.repository.MajorRepository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class AdministrativeClassService {

    @Autowired
    private AdministrativeClassRepository classRepository;

    @Autowired
    private MajorRepository majorRepository;

    @Autowired
    private CohortRepository cohortRepository;

    public List<AdministrativeClass> getAll() {
        return classRepository.findAll();
    }

    public List<AdministrativeClass> getAll(Long facultyId) {
        if (facultyId == null) return classRepository.findAll();
        return classRepository.findByMajor_Faculty_Id(facultyId);
    }

    public AdministrativeClass save(AdministrativeClass adminClass) {
        // Nếu frontend gửi theo cohortRef (id Niên khóa) thì đồng bộ luôn trường cohort (code) cho thuận tiện tra cứu
        if (adminClass.getCohortRef() != null && adminClass.getCohortRef().getId() != null) {
            Cohort cohort = cohortRepository.findById(adminClass.getCohortRef().getId()).orElse(null);
            if (cohort != null) {
                adminClass.setCohort(cohort.getCode());
            }
        }
        return classRepository.save(adminClass);
    }

    public AdministrativeClass update(Long id, AdministrativeClass adminClass) {
        AdministrativeClass existingClass = classRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Class not found with id: " + id));
        existingClass.setCode(adminClass.getCode());
        existingClass.setName(adminClass.getName());
        existingClass.setCohort(adminClass.getCohort());
        existingClass.setStudentCount(adminClass.getStudentCount());
        existingClass.setMajor(adminClass.getMajor());
        return classRepository.save(existingClass);
    }

    public void delete(Long id) {
        classRepository.deleteById(id);
    }

    public boolean belongsToFaculty(Long classId, Long facultyId) {
        if (facultyId == null) return true;
        return classRepository.findById(classId)
                .map(c -> c.getMajor() != null && c.getMajor().getFaculty() != null
                        && c.getMajor().getFaculty().getId().equals(facultyId))
                .orElse(false);
    }

    public ByteArrayInputStream generateTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("class_template");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Mã lớp");
            header.createCell(1).setCellValue("Tên lớp");
            header.createCell(2).setCellValue("Khóa");
            header.createCell(3).setCellValue("Sĩ số");
            header.createCell(4).setCellValue("Mã ngành"); // Cột tham chiếu

            // Dữ liệu mẫu
            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue("K17-CNTT1");
            sample.createCell(1).setCellValue("Công nghệ thông tin 1");
            sample.createCell(2).setCellValue("K17");
            sample.createCell(3).setCellValue(60);
            sample.createCell(4).setCellValue("7480201"); // Mã ngành phải tồn tại

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    public void importExcel(MultipartFile file) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            List<AdministrativeClass> classes = new ArrayList<>();
            List<Major> allMajors = majorRepository.findAll();
            
            // Load DB classes to memory
            Set<String> existingClasses = classRepository.findAll().stream()
                    .map(AdministrativeClass::getCode)
                    .collect(java.util.stream.Collectors.toSet());
            Set<String> processedCodes = new HashSet<>();

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;

                Cell codeCell = row.getCell(0);
                Cell nameCell = row.getCell(1);
                Cell cohortCell = row.getCell(2);
                Cell sizeCell = row.getCell(3);
                Cell majorCodeCell = row.getCell(4);

                if (codeCell != null && majorCodeCell != null) {
                    String code = getCellValue(codeCell);
                    String majorCode = getCellValue(majorCodeCell);
                    if (code == null || majorCode == null) continue;

                    if (existingClasses.contains(code) || processedCodes.contains(code)) continue;

                    Optional<Major> matchingMajor = allMajors.stream()
                            .filter(m -> majorCode.equalsIgnoreCase(m.getCode()))
                            .findFirst();

                    if (matchingMajor.isPresent()) {
                        AdministrativeClass adminClass = new AdministrativeClass();
                        adminClass.setCode(code);
                        String nameVal = getCellValue(nameCell);
                        adminClass.setName(nameVal != null ? nameVal : "");
                        String cohortVal = getCellValue(cohortCell);
                        if (cohortVal != null && !cohortVal.isBlank()) {
                            String normalized = cohortVal.trim();
                            // Chỉ chấp nhận các Khóa đã được khai báo trong danh mục Niên khóa
                            var cohortOpt = cohortRepository.findByCode(normalized);
                            if (cohortOpt.isEmpty()) {
                                // Bỏ qua dòng này nếu Khóa chưa được định nghĩa trong Cohort
                                System.err.printf("[ImportClass] Bỏ qua lớp %s: Khóa '%s' chưa được khai báo trong Niên khóa.%n",
                                        code, normalized);
                                continue;
                            }
                            adminClass.setCohort(normalized);
                            adminClass.setCohortRef(cohortOpt.get());
                        } else {
                            adminClass.setCohort("");
                        }

                        int size = 0;
                        if (sizeCell != null && sizeCell.getCellType() == CellType.NUMERIC) {
                            size = (int) sizeCell.getNumericCellValue();
                        }
                        adminClass.setStudentCount(size);
                        
                        adminClass.setMajor(matchingMajor.get());
                        classes.add(adminClass);
                        processedCodes.add(code);
                    }
                }
            }
            if (!classes.isEmpty()) {
                classRepository.saveAll(classes);
            }
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue().trim();
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return String.valueOf((long) cell.getNumericCellValue());
        }
        return null;
    }
}