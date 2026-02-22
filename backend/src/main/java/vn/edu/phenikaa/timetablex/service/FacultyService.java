package vn.edu.phenikaa.timetablex.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.phenikaa.timetablex.entity.Faculty;
import vn.edu.phenikaa.timetablex.repository.FacultyRepository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class FacultyService {

    @Autowired
    private FacultyRepository facultyRepository;

    public List<Faculty> getAll() {
        return facultyRepository.findAll();
    }

    public Faculty save(Faculty faculty) {
        return facultyRepository.save(faculty);
    }

    public Faculty update(Long id, Faculty faculty) {
        Faculty existingFaculty = facultyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Faculty not found with id: " + id));
        existingFaculty.setCode(faculty.getCode());
        existingFaculty.setName(faculty.getName());
        return facultyRepository.save(existingFaculty);
    }

    public void delete(Long id) {
        facultyRepository.deleteById(id);
    }

    // Tạo file mẫu Excel
    public ByteArrayInputStream generateTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("faculty_template");

            // Header
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Mã khoa");
            headerRow.createCell(1).setCellValue("Tên khoa");

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    // Import Excel
    public void importExcel(MultipartFile file) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            List<Faculty> faculties = new ArrayList<>();

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Bỏ qua header

                // Đọc dữ liệu: cột 0 = Mã khoa, cột 1 = Tên khoa
                Cell codeCell = row.getCell(0);
                Cell nameCell = row.getCell(1);

                if (codeCell != null && nameCell != null) {
                    String code = codeCell.getStringCellValue();
                    String name = nameCell.getStringCellValue();

                    // Kiểm tra trùng lặp đơn giản
                    if (!facultyRepository.existsByCode(code)) {
                        Faculty faculty = new Faculty();
                        faculty.setCode(code);
                        faculty.setName(name);
                        faculties.add(faculty);
                    }
                }
            }
            facultyRepository.saveAll(faculties);
        }
    }
}