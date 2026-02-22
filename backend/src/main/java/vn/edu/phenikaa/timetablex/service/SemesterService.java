package vn.edu.phenikaa.timetablex.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.phenikaa.timetablex.entity.Semester;
import vn.edu.phenikaa.timetablex.repository.SemesterRepository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class SemesterService {
    @Autowired private SemesterRepository semesterRepo;

    public List<Semester> getAll() { return semesterRepo.findAll(); }
    
    // Lấy học kỳ đang hoạt động
    public Semester getActiveSemester() {
        return semesterRepo.findByIsActiveTrue().orElse(null);
    }

    @Transactional
    public Semester save(Semester semester) {
        // Nếu học kỳ này được set là Active, tắt Active của các kỳ khác
        if (Boolean.TRUE.equals(semester.getIsActive())) {
            List<Semester> actives = semesterRepo.findAllByIsActiveTrue();
            actives.forEach(s -> {
                if (!s.getId().equals(semester.getId())) { // Bỏ qua chính nó nếu đang update
                    s.setIsActive(false);
                    semesterRepo.save(s);
                }
            });
        }
        return semesterRepo.save(semester);
    }

    public void delete(Long id) { semesterRepo.deleteById(id); }

    public ByteArrayInputStream generateTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Học kỳ");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Mã HK");
            header.createCell(1).setCellValue("Tên HK");
            header.createCell(2).setCellValue("Ngày bắt đầu (yyyy-MM-dd)");
            header.createCell(3).setCellValue("Ngày kết thúc (yyyy-MM-dd)");
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    @Transactional
    public void importExcel(MultipartFile file) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            List<Semester> toSave = new ArrayList<>();
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                String code = getCellValue(row.getCell(0));
                String name = getCellValue(row.getCell(1));
                String startStr = getCellValue(row.getCell(2));
                String endStr = getCellValue(row.getCell(3));
                if (code == null || code.isBlank()) continue;
                if (semesterRepo.existsByCode(code)) continue;
                LocalDate start = startStr != null && !startStr.isBlank() ? LocalDate.parse(startStr.trim()) : LocalDate.now();
                LocalDate end = endStr != null && !endStr.isBlank() ? LocalDate.parse(endStr.trim()) : start.plusMonths(4);
                Semester s = new Semester();
                s.setCode(code.trim());
                s.setName(name != null && !name.isBlank() ? name.trim() : "Học kỳ " + code);
                s.setStartDate(start);
                s.setEndDate(end);
                s.setIsActive(false);
                toSave.add(s);
            }
            semesterRepo.saveAll(toSave);
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue().trim();
        if (cell.getCellType() == CellType.NUMERIC) return String.valueOf((long) cell.getNumericCellValue());
        return null;
    }
}