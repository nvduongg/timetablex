package vn.edu.phenikaa.timetablex.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.phenikaa.timetablex.entity.Semester;
import vn.edu.phenikaa.timetablex.repository.SemesterRepository;

import java.util.List;

@Service
public class SemesterService {
    @Autowired
    private SemesterRepository semesterRepo;

    public List<Semester> getAll() {
        return semesterRepo.findAll();
    }

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

    public void delete(Long id) {
        semesterRepo.deleteById(id);
    }
}