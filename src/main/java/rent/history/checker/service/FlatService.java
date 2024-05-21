package rent.history.checker.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import rent.history.checker.entity.Flat;
import rent.history.checker.repository.FlatRepository;

import java.util.List;

@Service
public class FlatService {

    private final FlatRepository flatRepository;

    @Autowired
    public FlatService(FlatRepository flatRepository) {
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
