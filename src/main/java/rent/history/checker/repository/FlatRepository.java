package rent.history.checker.repository;



import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rent.history.checker.entity.Flat;

@Repository
public interface FlatRepository extends JpaRepository<Flat, Long> {
}

