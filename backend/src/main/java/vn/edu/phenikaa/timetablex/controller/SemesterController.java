package vn.edu.phenikaa.timetablex.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import vn.edu.phenikaa.timetablex.entity.Semester;
import vn.edu.phenikaa.timetablex.service.SemesterService;

import java.util.List;

@RestController
@RequestMapping("/api/semesters")
public class SemesterController {

    @Autowired
    private SemesterService semesterService;

    // Lấy tất cả kỳ học
    @GetMapping
    public List<Semester> getAll() {
        return semesterService.getAll();
    }

    // Lấy kỳ học đang hoạt động
    @GetMapping("/active")
    public Semester getActive() {
        return semesterService.getActiveSemester();
    }

    // Tạo mới kỳ học
    @PostMapping
    public Semester create(@RequestBody Semester semester) {
        return semesterService.save(semester);
    }

    // Cập nhật kỳ học
    @PutMapping("/{id}")
    public Semester update(@PathVariable Long id, @RequestBody Semester semester) {
        semester.setId(id);
        return semesterService.save(semester);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        semesterService.delete(id);
    }
}
