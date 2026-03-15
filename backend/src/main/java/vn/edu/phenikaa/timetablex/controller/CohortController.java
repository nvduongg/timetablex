package vn.edu.phenikaa.timetablex.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import vn.edu.phenikaa.timetablex.entity.Cohort;
import vn.edu.phenikaa.timetablex.service.CohortService;

import java.util.List;

@RestController
@RequestMapping("/api/cohorts")
public class CohortController {

    @Autowired
    private CohortService cohortService;

    @GetMapping
    public List<Cohort> getAll() {
        return cohortService.getAll();
    }

    @GetMapping("/active")
    public List<Cohort> getActive() {
        return cohortService.getActive();
    }

    @PostMapping
    public Cohort create(@RequestBody Cohort cohort) {
        return cohortService.create(cohort);
    }

    @PutMapping("/{id}")
    public Cohort update(@PathVariable Long id, @RequestBody Cohort cohort) {
        return cohortService.update(id, cohort);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        cohortService.delete(id);
    }
}

