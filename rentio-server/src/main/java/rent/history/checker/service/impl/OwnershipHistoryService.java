package rent.history.checker.service.impl;

import jakarta.transaction.Transactional;
import rent.history.checker.entity.RentHistory;
import rent.history.checker.repository.FlatRepository;
import rent.history.checker.repository.OwnershipHistoryRepository;
import rent.history.checker.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class OwnershipHistoryService {

    private final OwnershipHistoryRepository ownershipHistoryRepository;
    private final UserRepository userRepository;
    private final FlatRepository flatRepository;

    @Autowired
    public OwnershipHistoryService(OwnershipHistoryRepository ownershipHistoryRepository, UserRepository userRepository, FlatRepository flatRepository) {
        this.ownershipHistoryRepository = ownershipHistoryRepository;
        this.userRepository = userRepository;
        this.flatRepository = flatRepository;
    }

    public List<RentHistory> getAllOwnershipHistories() {
        return ownershipHistoryRepository.findAll();
    }

    @Transactional
    public Optional<RentHistory> getOwnershipHistoryById(Long id) {
        Optional<RentHistory> ownershipHistory = ownershipHistoryRepository.findById(id);
        ownershipHistory.ifPresent(o -> {
            // Ensure the related entities are fully fetched
            o.getFlat().getAddress();  // Accessing fields to ensure they are loaded
            o.getOwner().getUsername();  // Accessing fields to ensure they are loaded
        });
        return ownershipHistory;
    }

    public RentHistory saveOwnershipHistory(RentHistory rentHistory) {
        return ownershipHistoryRepository.save(rentHistory);
    }

    public void deleteOwnershipHistory(Long id) {
        ownershipHistoryRepository.deleteById(id);
    }
}