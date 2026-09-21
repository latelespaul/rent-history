package com.rentrix.rentrixserver.controller;

import com.rentrix.rentrixserver.dto.FlatDto;
import com.rentrix.rentrixserver.service.impl.FlatServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/flats")
public class FlatController {
	
	private final FlatServiceImpl flatServiceImpl;
	
	@GetMapping
	public Page<FlatDto> getAllFlats(Pageable pageable) {
		return flatServiceImpl.getAllFlats(pageable);
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