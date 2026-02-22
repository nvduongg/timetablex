package vn.edu.phenikaa.timetablex.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.phenikaa.timetablex.entity.Shift;

@Repository
public interface ShiftRepository extends JpaRepository<Shift, Long> {
    Shift findByName(String name);
}
