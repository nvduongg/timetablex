package vn.edu.phenikaa.timetablex.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.phenikaa.timetablex.entity.Shift;
import vn.edu.phenikaa.timetablex.entity.TimeSlot;
import vn.edu.phenikaa.timetablex.repository.ShiftRepository;
import vn.edu.phenikaa.timetablex.repository.TimeSlotRepository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class TimeSlotService {

    @Autowired
    private TimeSlotRepository timeSlotRepo;
    @Autowired
    private ShiftRepository shiftRepo;

    // --- TimeSlot CRUD ---
    public List<TimeSlot> getAllSlots() {
        return timeSlotRepo.findAll();
    }

    public TimeSlot saveSlot(TimeSlot slot) {
        return timeSlotRepo.save(slot);
    }

    public void deleteSlot(Long id) {
        timeSlotRepo.deleteById(id);
    }

    // Logic Update TimeSlot
    public TimeSlot updateSlot(Long id, TimeSlot newData) {
        return timeSlotRepo.findById(id).map(slot -> {
            slot.setName(newData.getName());
            slot.setStartTime(newData.getStartTime());
            slot.setEndTime(newData.getEndTime());
            slot.setPeriodIndex(newData.getPeriodIndex());
            return timeSlotRepo.save(slot);
        }).orElseThrow(() -> new RuntimeException("Không tìm thấy tiết học"));
    }

    // --- Shift CRUD ---
    public List<Shift> getAllShifts() {
        return shiftRepo.findAll();
    }

    public Shift saveShift(Shift shift) {
        return shiftRepo.save(shift);
    }

    public void deleteShift(Long id) {
        shiftRepo.deleteById(id);
    }

    // Logic Update Shift
    public Shift updateShift(Long id, Shift newData) {
        return shiftRepo.findById(id).map(shift -> {
            shift.setName(newData.getName());
            shift.setStartPeriod(newData.getStartPeriod());
            shift.setEndPeriod(newData.getEndPeriod());
            return shiftRepo.save(shift);
        }).orElseThrow(() -> new RuntimeException("Không tìm thấy ca học"));
    }

    // --- Excel Logic ---
    public ByteArrayInputStream generateTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Sheet 1: Tiết học (15 tiết)
            Sheet sheet1 = workbook.createSheet("TimeSlots");
            Row header1 = sheet1.createRow(0);
            header1.createCell(0).setCellValue("Tiết số");
            header1.createCell(1).setCellValue("Tên tiết");
            header1.createCell(2).setCellValue("Giờ bắt đầu (HH:mm)");
            header1.createCell(3).setCellValue("Giờ kết thúc (HH:mm)");

            // Sheet 2: Ca học (5 ca)
            Sheet sheet2 = workbook.createSheet("Shifts");
            Row header2 = sheet2.createRow(0);
            header2.createCell(0).setCellValue("Tên ca");
            header2.createCell(1).setCellValue("Từ tiết");
            header2.createCell(2).setCellValue("Đến tiết");

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    public void importExcel(MultipartFile file) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

            // 1. Import TimeSlots (Sheet 1)
            Sheet sheet1 = workbook.getSheet("TimeSlots");
            if (sheet1 != null) {
                List<TimeSlot> slotsToSave = new ArrayList<>();
                for (Row row : sheet1) {
                    if (row.getRowNum() == 0)
                        continue;
                    try {
                        Integer periodIndex = (int) row.getCell(0).getNumericCellValue();
                        String name = row.getCell(1).getStringCellValue();
                        LocalTime startTime = null;
                        LocalTime endTime = null;

                        // Xử lý giờ
                        Cell startCell = row.getCell(2);
                        if (startCell != null) {
                            if (startCell.getCellType() == CellType.STRING) {
                                startTime = LocalTime.parse(startCell.getStringCellValue(), formatter);
                            } else if (startCell.getCellType() == CellType.NUMERIC) {
                                startTime = startCell.getLocalDateTimeCellValue().toLocalTime();
                            }
                        }

                        Cell endCell = row.getCell(3);
                        if (endCell != null) {
                            if (endCell.getCellType() == CellType.STRING) {
                                endTime = LocalTime.parse(endCell.getStringCellValue(), formatter);
                            } else if (endCell.getCellType() == CellType.NUMERIC) {
                                endTime = endCell.getLocalDateTimeCellValue().toLocalTime();
                            }
                        }

                        if (startTime != null && endTime != null) {
                            // Kiểm tra tồn tại để update hoặc tạo mới
                            TimeSlot slot = timeSlotRepo.findByPeriodIndex(periodIndex);
                            if (slot == null) {
                                slot = new TimeSlot();
                                slot.setPeriodIndex(periodIndex);
                            }
                            slot.setName(name);
                            slot.setStartTime(startTime);
                            slot.setEndTime(endTime);
                            slotsToSave.add(slot);
                        }
                    } catch (Exception e) {
                        /* Skip row error */ }
                }
                if (!slotsToSave.isEmpty())
                    timeSlotRepo.saveAll(slotsToSave);
            }

            // 2. Import Shifts (Sheet 2)
            Sheet sheet2 = workbook.getSheet("Shifts");
            if (sheet2 != null) {
                List<Shift> shiftsToSave = new ArrayList<>();
                for (Row row : sheet2) {
                    if (row.getRowNum() == 0)
                        continue;
                    try {
                        String name = row.getCell(0).getStringCellValue();
                        Integer startPeriod = (int) row.getCell(1).getNumericCellValue();
                        Integer endPeriod = (int) row.getCell(2).getNumericCellValue();

                        // Kiểm tra tồn tại
                        Shift shift = shiftRepo.findByName(name);
                        if (shift == null) {
                            shift = new Shift();
                            shift.setName(name);
                        }
                        shift.setStartPeriod(startPeriod);
                        shift.setEndPeriod(endPeriod);
                        shiftsToSave.add(shift);
                    } catch (Exception e) {
                        /* Skip row error */ }
                }
                if (!shiftsToSave.isEmpty())
                    shiftRepo.saveAll(shiftsToSave);
            }
        }
    }
}