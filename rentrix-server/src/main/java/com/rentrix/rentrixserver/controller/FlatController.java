package com.rentrix.rentrixserver.controller;

import com.rentrix.rentrixserver.dto.FlatDto;
import com.rentrix.rentrixserver.service.FlatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/flats")
public class FlatController {
	
	private final FlatService flatService;
	
	@GetMapping
	public Page<FlatDto> getAllFlats(@PageableDefault(size = 9, sort = "rent") Pageable pageable) {
		return flatService.getAllFlats(pageable);
	}
	
	@GetMapping("/{id}")
	public ResponseEntity<FlatDto> getFlatById(@PathVariable Long id) {
		return ResponseEntity.ok(flatService.getFlatById(id));
	}
	
	@PostMapping
	public ResponseEntity<String> createFlat(@RequestBody FlatDto flatDto) {
		return ResponseEntity.status(201).body(flatService.saveFlat(flatDto));
	}
	
	@PutMapping("/{id}")
	public ResponseEntity<String> updateFlat(@PathVariable Long id, @RequestBody FlatDto flatDto) {
		return ResponseEntity.ok(flatService.updateFlat(id, flatDto));
		
	}
	
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteFlat(@PathVariable Long id) {
		flatService.deleteFlat(id);
		return ResponseEntity.noContent().build();
	}
	
}