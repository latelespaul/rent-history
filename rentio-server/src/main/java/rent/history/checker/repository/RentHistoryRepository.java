package rent.history.checker.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rent.history.checker.entity.RentHistory;

@Repository
public interface RentHistoryRepository extends JpaRepository<RentHistory, Long> {
}