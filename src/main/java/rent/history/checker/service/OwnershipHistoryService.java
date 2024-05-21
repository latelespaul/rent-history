package rent.history.checker.service;

import jakarta.transaction.Transactional;
import rent.history.checker.entity.Flat;
import rent.history.checker.entity.OwnershipHistory;
import rent.history.checker.entity.User;
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

    public List<OwnershipHistory> getAllOwnershipHistories() {
        return ownershipHistoryRepository.findAll();
    }

    @Transactional
    public Optional<OwnershipHistory> getOwnershipHistoryById(Long id) {
        Optional<OwnershipHistory> ownershipHistory = ownershipHistoryRepository.findById(id);
        ownershipHistory.ifPresent(o -> {
            // Ensure the related entities are fully fetched
            o.getFlat().getAddress();  // Accessing fields to ensure they are loaded
            o.getOwner().getUsername();  // Accessing fields to ensure they are loaded
        });
        return ownershipHistory;
    }

    public OwnershipHistory saveOwnershipHistory(OwnershipHistory ownershipHistory) {
        return ownershipHistoryRepository.save(ownershipHistory);
    }

    public void deleteOwnershipHistory(Long id) {
        ownershipHistoryRepository.deleteById(id);
    }
}
