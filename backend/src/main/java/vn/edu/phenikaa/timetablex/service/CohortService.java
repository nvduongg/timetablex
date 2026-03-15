package vn.edu.phenikaa.timetablex.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import vn.edu.phenikaa.timetablex.entity.Cohort;
import vn.edu.phenikaa.timetablex.repository.CohortRepository;

import java.util.List;

@Service
public class CohortService {

    @Autowired
    private CohortRepository cohortRepository;

    public List<Cohort> getAll() {
        return cohortRepository.findAll();
    }

    public List<Cohort> getActive() {
        return cohortRepository.findByActiveTrueOrderByAdmissionYearDesc();
    }

    public Cohort create(Cohort cohort) {
        return cohortRepository.save(cohort);
    }

    public Cohort update(Long id, Cohort updated) {
        Cohort existing = cohortRepository.findById(id).orElseThrow();
        existing.setCode(updated.getCode());
        existing.setName(updated.getName());
        existing.setAdmissionYear(updated.getAdmissionYear());
        existing.setActive(updated.getActive());
        existing.setNote(updated.getNote());
        return cohortRepository.save(existing);
    }

    public void delete(Long id) {
        cohortRepository.deleteById(id);
    }

    public Cohort getByCodeOrNull(String code) {
        if (code == null || code.isBlank()) return null;
        return cohortRepository.findByCode(code).orElse(null);
    }
}

