package rent.history.checker.service.impl;

import jakarta.transaction.Transactional;
import rent.history.checker.entity.RentHistory;
import rent.history.checker.repository.FlatRepository;
import rent.history.checker.repository.RentHistoryRepository;
import rent.history.checker.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class OwnershipHistoryService {

    private final RentHistoryRepository rentHistoryRepository;
    private final UserRepository userRepository;
    private final FlatRepository flatRepository;

    @Autowired
    public OwnershipHistoryService(RentHistoryRepository rentHistoryRepository, UserRepository userRepository, FlatRepository flatRepository) {
        this.rentHistoryRepository = rentHistoryRepository;
        this.userRepository = userRepository;
        this.flatRepository = flatRepository;
    }

    public List<RentHistory> getAllOwnershipHistories() {
        return rentHistoryRepository.findAll();
    }

    @Transactional
    public Optional<RentHistory> getOwnershipHistoryById(Long id) {
        Optional<RentHistory> ownershipHistory = rentHistoryRepository.findById(id);
        ownershipHistory.ifPresent(o -> {
            // Ensure the related entities are fully fetched
            o.getFlat().getAddress();  // Accessing fields to ensure they are loaded
            o.getOwner().getUsername();  // Accessing fields to ensure they are loaded
        });
        return ownershipHistory;
    }

    public RentHistory saveOwnershipHistory(RentHistory rentHistory) {
        return rentHistoryRepository.save(rentHistory);
    }

    public void deleteOwnershipHistory(Long id) {
        rentHistoryRepository.deleteById(id);
    }
}