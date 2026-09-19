package rent.history.checker.controller;

import lombok.RequiredArgsConstructor;
import rent.history.checker.entity.Flat;
import rent.history.checker.entity.RentHistory;
import rent.history.checker.entity.User;
import rent.history.checker.service.impl.OwnershipHistoryService;
import rent.history.checker.repository.UserRepository;
import rent.history.checker.repository.FlatRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/history")
public class RentHistoryController {
	
	private final OwnershipHistoryService ownershipHistoryService;
	private final UserRepository userRepository;
	private final FlatRepository flatRepository;
	
	@GetMapping
	public List<RentHistory> getAllOwnershipHistories() {
		return ownershipHistoryService.getAllOwnershipHistories();
	}
	
	@GetMapping("/{id}")
	public ResponseEntity<RentHistory> getOwnershipHistoryById(@PathVariable Long id) {
		return ownershipHistoryService.getOwnershipHistoryById(id)
												.map(ResponseEntity::ok)
												.orElseGet(() -> ResponseEntity.notFound().build());
	}
	
	@PostMapping
	public ResponseEntity<RentHistory> createOwnershipHistory(@RequestBody RentHistory rentHistory) {
		if (rentHistory.getOwner() == null || rentHistory.getFlat() == null) {
			return ResponseEntity.badRequest().body(null);
		}
		
		// Fetch related entities first
		User owner = rentHistory.getOwner();
		Flat flat = rentHistory.getFlat();
		
		// Set the fetched entities
		rentHistory.setOwner(owner);
		rentHistory.setFlat(flat);
		
		// Save the ownership history
		RentHistory savedRentHistory = ownershipHistoryService.saveOwnershipHistory(rentHistory);
		
		// Fetch the saved ownership history with fully loaded owner and flat
		Optional<RentHistory> fetchedOwnershipHistory = ownershipHistoryService.getOwnershipHistoryById(
			savedRentHistory.getId());
		
		// Return the fully loaded ownership history
		return fetchedOwnershipHistory
					 .map(o -> ResponseEntity.status(201).body(o))
					 .orElseGet(() -> ResponseEntity.status(500).build());
	}
	
	@PutMapping("/{id}")
	public ResponseEntity<RentHistory> updateOwnershipHistory(@PathVariable Long id,
		@RequestBody RentHistory rentHistory) {
		RentHistory existingRentHistory = ownershipHistoryService.getOwnershipHistoryById(id).orElse(null);
		if (existingRentHistory != null) {
			if (rentHistory.getOwner() == null || rentHistory.getFlat() == null) {
				return ResponseEntity.badRequest().body(null);
			}
			
			// Fetch related entities first
			User owner = rentHistory.getOwner();
			Flat flat = rentHistory.getFlat();
			
			// Set the fetched entities
			existingRentHistory.setOwner(owner);
			existingRentHistory.setFlat(flat);
			existingRentHistory.setRentStartDate(rentHistory.getRentStartDate());
			existingRentHistory.setRentEndDate(rentHistory.getRentEndDate());
			
			// Save the updated ownership history
			RentHistory updatedRentHistory = ownershipHistoryService.saveOwnershipHistory(existingRentHistory);
			
			// Fetch the updated ownership history with fully loaded owner and flat
			Optional<RentHistory> fetchedOwnershipHistory = ownershipHistoryService.getOwnershipHistoryById(
				updatedRentHistory.getId());
			
			// Return the fully loaded ownership history
			return fetchedOwnershipHistory
						 .map(ResponseEntity::ok)
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