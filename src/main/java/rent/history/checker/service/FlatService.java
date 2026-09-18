package rent.history.checker.service;

import org.springframework.beans.factory.annotation.Autowired;
import rent.history.checker.entity.Flat;
import rent.history.checker.repository.FlatRepository;

import java.util.List;

public interface FlatService {
	
	public List<Flat> getAllFlats();
	
	public Flat getFlatById(Long id);
	
	public Flat saveFlat(Flat flat);
	
	public void deleteFlat(Long id);
	
}