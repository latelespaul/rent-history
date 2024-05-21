package rent.history.checker.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rent.history.checker.entity.Flat;
import rent.history.checker.service.FlatService;

import java.util.List;

@RestController
@RequestMapping("/api/flats")
public class FlatController {

    private final FlatService flatService;

    @Autowired
    public FlatController(FlatService flatService) {
        this.flatService = flatService;
    }

    @GetMapping
    public List<Flat> getAllFlats() {
        return flatService.getAllFlats();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Flat> getFlatById(@PathVariable Long id) {
        Flat flat = flatService.getFlatById(id);
        return flat != null ? ResponseEntity.ok(flat) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<Flat> createFlat(@RequestBody Flat flat) {
        Flat savedFlat = flatService.saveFlat(flat);
        return ResponseEntity.status(201).body(savedFlat);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Flat> updateFlat(@PathVariable Long id, @RequestBody Flat flat) {
        Flat existingFlat = flatService.getFlatById(id);
        if (existingFlat != null) {
            flat.setId(id);
            Flat updatedFlat = flatService.saveFlat(flat);
            return ResponseEntity.ok(updatedFlat);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlat(@PathVariable Long id) {
        flatService.deleteFlat(id);
        return ResponseEntity.noContent().build();
    }
}

