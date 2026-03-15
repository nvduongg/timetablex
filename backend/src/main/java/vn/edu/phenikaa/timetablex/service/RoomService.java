package vn.edu.phenikaa.timetablex.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.phenikaa.timetablex.entity.Room;
import vn.edu.phenikaa.timetablex.repository.RoomRepository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RoomService {

    @Autowired
    private RoomRepository roomRepository;

    public List<Room> getAll() {
        return roomRepository.findAll();
    }

    public Room save(Room room) {
        return roomRepository.save(room);
    }

    public Room update(Long id, Room room) {
        Room existingRoom = roomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Room not found with id: " + id));
        existingRoom.setName(room.getName());
        existingRoom.setCapacity(room.getCapacity());
        existingRoom.setType(room.getType());
        return roomRepository.save(existingRoom);
    }

    public void delete(Long id) {
        roomRepository.deleteById(id);
    }

    // Tạo Template Excel
    public ByteArrayInputStream generateTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("room_template");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Tên phòng");
            header.createCell(1).setCellValue("Sức chứa");
            header.createCell(2).setCellValue("Loại phòng");
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    public void importExcel(MultipartFile file) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            List<Room> rooms = new ArrayList<>();
            Set<String> processedNames = new HashSet<>();
            
            // Prefetch existing rooms to memory
            Set<String> existingRooms = roomRepository.findAll().stream()
                    .map(Room::getName)
                    .collect(java.util.stream.Collectors.toSet());

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;

                Cell nameCell = row.getCell(0);
                Cell capacityCell = row.getCell(1);
                Cell typeCell = row.getCell(2);

                String name = getCellValue(nameCell);
                if (name == null || name.isBlank() || name.startsWith("---")) continue;
                if (existingRooms.contains(name) || processedNames.contains(name)) continue;

                int capacity = 0;
                if (capacityCell != null && capacityCell.getCellType() == CellType.NUMERIC) {
                    capacity = (int) capacityCell.getNumericCellValue();
                } else {
                    String capStr = getCellValue(capacityCell);
                    if (capStr != null) {
                        try { capacity = Integer.parseInt(capStr.trim()); } catch (Exception ignored) {}
                    }
                }
                if (capacity <= 0) continue;

                String typeRaw = getCellValue(typeCell);
                String type = (typeRaw != null && !typeRaw.isBlank()) ? typeRaw.trim().toUpperCase() : "LT";
                if (!isValidRoomType(type)) type = "LT";

                Room room = new Room();
                room.setName(name);
                room.setCapacity(capacity);
                room.setType(type);
                rooms.add(room);
                processedNames.add(name);
            }
            if (!rooms.isEmpty()) {
                roomRepository.saveAll(rooms);
            }
        }
    }

    private boolean isValidRoomType(String t) {
        return "LT".equals(t) || "PM".equals(t) || "TN".equals(t) || "SB".equals(t)
                || "XT".equals(t) || "BV".equals(t) || "DN".equals(t) || "ONLINE".equals(t);
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue().trim();
        if (cell.getCellType() == CellType.NUMERIC) return String.valueOf((long) cell.getNumericCellValue());
        return null;
    }
}