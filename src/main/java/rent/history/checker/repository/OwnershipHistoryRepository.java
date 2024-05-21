package rent.history.checker.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rent.history.checker.entity.OwnershipHistory;

@Repository
public interface OwnershipHistoryRepository extends JpaRepository<OwnershipHistory, Long> {
}
