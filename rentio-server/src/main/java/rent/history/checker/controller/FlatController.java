package rent.history.checker.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rent.history.checker.dto.FlatDto;
import rent.history.checker.service.impl.FlatServiceImpl;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/flats")
public class FlatController {
	
	private final FlatServiceImpl flatServiceImpl;
	
	@GetMapping
	public List<FlatDto> getAllFlats() {
		return flatServiceImpl.getAllFlats();
	}
	
	@GetMapping("/{id}")
	public ResponseEntity<FlatDto> getFlatById(@PathVariable Long id) {
		FlatDto flatDto = flatServiceImpl.getFlatById(id);
		return ResponseEntity.ok(flatDto);
	}
	
	@PostMapping
	public ResponseEntity<String> createFlat(@RequestBody FlatDto flatDto) {
		String msg = flatServiceImpl.saveFlat(flatDto);
		return ResponseEntity.status(201).body(msg);
	}
	
	@PutMapping("/{id}")
	public ResponseEntity<String> updateFlat(@PathVariable Long id, @RequestBody FlatDto flatDto) {
		String msg = flatServiceImpl.updateFlat(id, flatDto);
		return ResponseEntity.ok(msg);
		
	}
	
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteFlat(@PathVariable Long id) {
		flatServiceImpl.deleteFlat(id);
		return ResponseEntity.noContent().build();
	}
	
}