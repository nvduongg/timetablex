package vn.edu.phenikaa.timetablex.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.phenikaa.timetablex.entity.Department;
import vn.edu.phenikaa.timetablex.entity.Faculty;
import vn.edu.phenikaa.timetablex.repository.DepartmentRepository;
import vn.edu.phenikaa.timetablex.repository.FacultyRepository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

@Service
public class DepartmentService {

    @Autowired
    private DepartmentRepository departmentRepo;

    @Autowired
    private FacultyRepository facultyRepo;

    public List<Department> getAll() {
        return departmentRepo.findAll();
    }

    public List<Department> getByFaculty(Long facultyId) {
        if (facultyId == null) return departmentRepo.findAll();
        return departmentRepo.findByFaculty_IdOrderByName(facultyId);
    }

    public Department save(Department department) {
        return departmentRepo.save(department);
    }

    public Department update(Long id, Department department) {
        Department existing = departmentRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bộ môn với id: " + id));
        existing.setCode(department.getCode());
        existing.setName(department.getName());
        existing.setFaculty(department.getFaculty());
        return departmentRepo.save(existing);
    }

    public void delete(Long id) {
        departmentRepo.deleteById(id);
    }

    public boolean belongsToFaculty(Long departmentId, Long facultyId) {
        if (facultyId == null) return true;
        return departmentRepo.findById(departmentId)
                .map(d -> d.getFaculty() != null && d.getFaculty().getId().equals(facultyId))
                .orElse(false);
    }

    // --- EXCEL TEMPLATE ---
    public ByteArrayInputStream generateTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("department_template");

            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            Row header = sheet.createRow(0);
            String[] headers = {"Mã bộ môn", "Tên bộ môn", "Mã khoa"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Dòng mẫu
            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue("KHMT");
            sample.createCell(1).setCellValue("Bộ môn Khoa học máy tính");
            sample.createCell(2).setCellValue("CNTT");

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    // --- EXCEL IMPORT ---
    public void importExcel(MultipartFile file) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            List<Faculty> faculties = facultyRepo.findAll();
            List<Department> toSave = new ArrayList<>();

            Set<String> existingCodes = new HashSet<>();
            departmentRepo.findAll().forEach(d -> existingCodes.add(d.getCode()));
            Set<String> processedCodes = new HashSet<>();

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;

                String code = getCellValue(row.getCell(0));
                String name = getCellValue(row.getCell(1));
                String facultyCode = getCellValue(row.getCell(2));

                if (code == null || facultyCode == null) continue;
                if (existingCodes.contains(code) || processedCodes.contains(code)) continue;

                Optional<Faculty> fac = faculties.stream()
                        .filter(f -> f.getCode().equalsIgnoreCase(facultyCode))
                        .findFirst();

                if (fac.isPresent()) {
                    Department dept = new Department();
                    dept.setCode(code);
                    dept.setName(name != null ? name : code);
                    dept.setFaculty(fac.get());
                    toSave.add(dept);
                    processedCodes.add(code);
                }
            }

            if (!toSave.isEmpty()) departmentRepo.saveAll(toSave);
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue().trim();
        if (cell.getCellType() == CellType.NUMERIC) return String.valueOf((long) cell.getNumericCellValue());
        return null;
    }
}
