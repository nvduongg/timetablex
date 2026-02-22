package vn.edu.phenikaa.timetablex.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.phenikaa.timetablex.entity.Faculty;
import vn.edu.phenikaa.timetablex.entity.Major;
import vn.edu.phenikaa.timetablex.repository.FacultyRepository;
import vn.edu.phenikaa.timetablex.repository.MajorRepository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import java.util.HashSet;
import java.util.Set;

@Service
public class MajorService {

    @Autowired
    private MajorRepository majorRepository;

    @Autowired
    private FacultyRepository facultyRepository; // Cần repo này để tìm Khoa

    public List<Major> getAll() {
        return majorRepository.findAll();
    }

    public List<Major> getAll(Long facultyId) {
        if (facultyId == null) return majorRepository.findAll();
        return majorRepository.findByFaculty_IdOrderByCode(facultyId);
    }

    public Major save(Major major) {
        return majorRepository.save(major);
    }

    public Major update(Long id, Major major) {
        Major existingMajor = majorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Major not found with id: " + id));
        existingMajor.setCode(major.getCode());
        existingMajor.setName(major.getName());
        existingMajor.setFaculty(major.getFaculty());
        return majorRepository.save(existingMajor);
    }

    public void delete(Long id) {
        majorRepository.deleteById(id);
    }

    /** Kiểm tra ngành có thuộc khoa không (dùng cho phân quyền Khoa) */
    public boolean belongsToFaculty(Long majorId, Long facultyId) {
        if (facultyId == null) return true;
        return majorRepository.findById(majorId)
                .map(m -> m.getFaculty() != null && m.getFaculty().getId().equals(facultyId))
                .orElse(false);
    }

    // Tạo Template Excel: Cần thêm cột "Mã Khoa" để người dùng nhập
    public ByteArrayInputStream generateTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("major_template");

            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Mã ngành");
            headerRow.createCell(1).setCellValue("Tên ngành");
            headerRow.createCell(2).setCellValue("Mã khoa");

            // Dữ liệu mẫu
            Row sampleRow = sheet.createRow(1);
            sampleRow.createCell(0).setCellValue("7480201");
            sampleRow.createCell(1).setCellValue("Công nghệ thông tin");
            sampleRow.createCell(2).setCellValue("IT");

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    // Import Excel: Logic map từ Mã Khoa -> Đối tượng Khoa
    public void importExcel(MultipartFile file) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            List<Major> majors = new ArrayList<>();
            List<Faculty> allFaculties = facultyRepository.findAll(); // Lấy tất cả khoa để tra cứu cho nhanh
            Set<String> processedCodes = new HashSet<>();

            for (Row row : sheet) {
                if (row.getRowNum() == 0)
                    continue;

                Cell codeCell = row.getCell(0);
                Cell nameCell = row.getCell(1);
                Cell facultyCodeCell = row.getCell(2);

                String code = getCellValue(codeCell);
                String name = getCellValue(nameCell);
                String facultyCode = getCellValue(facultyCodeCell);

                if (code == null || name == null || facultyCode == null)
                    continue;

                // 1. Kiểm tra xem Ngành đã tồn tại chưa (trong file hiện tại hoặc trong DB)
                if (processedCodes.contains(code) || majorRepository.existsByCode(code))
                    continue;

                // 2. Tìm Khoa tương ứng với Mã Khoa trong Excel
                Optional<Faculty> matchingFaculty = allFaculties.stream()
                        .filter(f -> f.getCode().equalsIgnoreCase(facultyCode))
                        .findFirst();

                if (matchingFaculty.isPresent()) {
                    Major major = new Major();
                    major.setCode(code);
                    major.setName(name);
                    major.setFaculty(matchingFaculty.get());
                    majors.add(major);
                    processedCodes.add(code);
                }
            }
            majorRepository.saveAll(majors);
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null)
            return null;
        CellType type = cell.getCellType();
        if (type == CellType.STRING) {
            return cell.getStringCellValue().trim();
        } else if (type == CellType.NUMERIC) {
            return String.valueOf((long) cell.getNumericCellValue());
        }
        return null;
    }
}