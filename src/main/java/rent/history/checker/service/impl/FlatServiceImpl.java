package rent.history.checker.service.impl;

import org.springframework.stereotype.Service;
import rent.history.checker.entity.Flat;
import rent.history.checker.repository.FlatRepository;
import rent.history.checker.service.FlatService;

import java.util.List;

@Service
public class FlatServiceImpl implements FlatService {
	
	private final FlatRepository flatRepository;
	
	public FlatServiceImpl(FlatRepository flatRepository) {
		this.flatRepository = flatRepository;
	}
	
	public List<Flat> getAllFlats() {
		return flatRepository.findAll();
	}
	
	public Flat getFlatById(Long id) {
		return flatRepository.findById(id).orElse(null);
	}
	
	public Flat saveFlat(Flat flat) {
		return flatRepository.save(flat);
	}
	
	public void deleteFlat(Long id) {
		flatRepository.deleteById(id);
	}
	
}