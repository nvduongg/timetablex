package vn.edu.phenikaa.timetablex.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.phenikaa.timetablex.entity.Room;

public interface RoomRepository extends JpaRepository<Room, Long> {
    boolean existsByName(String name);
}