package rent.history.checker.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rent.history.checker.entity.Review;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
}
