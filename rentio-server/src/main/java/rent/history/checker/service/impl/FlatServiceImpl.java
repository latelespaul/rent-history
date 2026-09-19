package rent.history.checker.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import rent.history.checker.dto.FlatDto;
import rent.history.checker.entity.Flat;
import rent.history.checker.mapper.FlatMapper;
import rent.history.checker.repository.FlatRepository;
import rent.history.checker.service.FlatService;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlatServiceImpl implements FlatService {
	
	private final FlatRepository flatRepository;
	
	private final FlatMapper flatMapper;
	
	public List<FlatDto> getAllFlats() {
		log.info("Get list of all flats");
		return flatRepository.findAll()
									.stream()
									.map(flatMapper::toDto)
									.toList();
	}
	
	public FlatDto getFlatById(Long id) {
		Flat flat = flatRepository.findById(id).orElseThrow();
		log.info("Get flat with id {}", id);
		return flatMapper.toDto(flat);
	}
	
	public String saveFlat(FlatDto flatDto) {
		Flat flat = flatMapper.toEntity(flatDto);
		flatRepository.save(flat);
		log.info("Save flat with id {}", flat.getId());
		return "Flat Saved";
	}
	
	public String updateFlat(Long id, FlatDto flatDto) {
		flatRepository.findById(id)
						  .orElseThrow(() -> new RuntimeException("Flat not found with id: " + id));
		Flat flat = flatMapper.toEntity(flatDto);
		flatRepository.save(flat);
		log.info("Update flat with id {}", id);
		return "Flat Updated";
	}
	
	public String deleteFlat(Long id) {
		log.info("Delete flat with id {}", id);
		flatRepository.deleteById(id);
		return "Flat deleted";
	}
	
}