package rent.history.checker.controller;

import rent.history.checker.entity.Flat;
import rent.history.checker.entity.OwnershipHistory;
import rent.history.checker.entity.User;
import rent.history.checker.service.OwnershipHistoryService;
import rent.history.checker.repository.UserRepository;
import rent.history.checker.repository.FlatRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/ownership-histories")
public class OwnershipHistoryController {

    private final OwnershipHistoryService ownershipHistoryService;
    private final UserRepository userRepository;
    private final FlatRepository flatRepository;

    @Autowired
    public OwnershipHistoryController(OwnershipHistoryService ownershipHistoryService, UserRepository userRepository, FlatRepository flatRepository) {
        this.ownershipHistoryService = ownershipHistoryService;
        this.userRepository = userRepository;
        this.flatRepository = flatRepository;
    }

    @GetMapping
    public List<OwnershipHistory> getAllOwnershipHistories() {
        return ownershipHistoryService.getAllOwnershipHistories();
    }

    @GetMapping("/{id}")
    public ResponseEntity<OwnershipHistory> getOwnershipHistoryById(@PathVariable Long id) {
        return ownershipHistoryService.getOwnershipHistoryById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<OwnershipHistory> createOwnershipHistory(@RequestBody OwnershipHistory ownershipHistory) {
        if (ownershipHistory.getOwner() == null || ownershipHistory.getFlat() == null) {
            return ResponseEntity.badRequest().body(null);
        }

        // Fetch related entities first
        User owner = userRepository.findById(ownershipHistory.getOwner().getId()).orElseThrow(() -> new RuntimeException("User not found"));
        Flat flat = flatRepository.findById(ownershipHistory.getFlat().getId()).orElseThrow(() -> new RuntimeException("Flat not found"));

        // Set the fetched entities
        ownershipHistory.setOwner(owner);
        ownershipHistory.setFlat(flat);

        // Save the ownership history
        OwnershipHistory savedOwnershipHistory = ownershipHistoryService.saveOwnershipHistory(ownershipHistory);

        // Fetch the saved ownership history with fully loaded owner and flat
        Optional<OwnershipHistory> fetchedOwnershipHistory = ownershipHistoryService.getOwnershipHistoryById(savedOwnershipHistory.getId());

        // Return the fully loaded ownership history
        return fetchedOwnershipHistory
                .map(o -> ResponseEntity.status(201).body(o))
                .orElseGet(() -> ResponseEntity.status(500).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<OwnershipHistory> updateOwnershipHistory(@PathVariable Long id, @RequestBody OwnershipHistory ownershipHistory) {
        OwnershipHistory existingOwnershipHistory = ownershipHistoryService.getOwnershipHistoryById(id).orElse(null);
        if (existingOwnershipHistory != null) {
            if (ownershipHistory.getOwner() == null || ownershipHistory.getFlat() == null) {
                return ResponseEntity.badRequest().body(null);
            }

            // Fetch related entities first
            User owner = userRepository.findById(ownershipHistory.getOwner().getId()).orElseThrow(() -> new RuntimeException("User not found"));
            Flat flat = flatRepository.findById(ownershipHistory.getFlat().getId()).orElseThrow(() -> new RuntimeException("Flat not found"));

            // Set the fetched entities
            existingOwnershipHistory.setOwner(owner);
            existingOwnershipHistory.setFlat(flat);
            existingOwnershipHistory.setOwnershipStartDate(ownershipHistory.getOwnershipStartDate());
            existingOwnershipHistory.setOwnershipEndDate(ownershipHistory.getOwnershipEndDate());

            // Save the updated ownership history
            OwnershipHistory updatedOwnershipHistory = ownershipHistoryService.saveOwnershipHistory(existingOwnershipHistory);

            // Fetch the updated ownership history with fully loaded owner and flat
            Optional<OwnershipHistory> fetchedOwnershipHistory = ownershipHistoryService.getOwnershipHistoryById(updatedOwnershipHistory.getId());

            // Return the fully loaded ownership history
            return fetchedOwnershipHistory
                    .map(o -> ResponseEntity.ok(o))
                    .orElseGet(() -> ResponseEntity.status(500).build());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOwnershipHistory(@PathVariable Long id) {
        ownershipHistoryService.deleteOwnershipHistory(id);
        return ResponseEntity.noContent().build();
    }
}

