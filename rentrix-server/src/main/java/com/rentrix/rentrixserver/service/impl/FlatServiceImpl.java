package com.rentrix.rentrixserver.service.impl;

import com.rentrix.rentrixserver.dto.FlatDto;
import com.rentrix.rentrixserver.entity.Flat;
import com.rentrix.rentrixserver.exception.ApiException;
import com.rentrix.rentrixserver.mapper.FlatMapper;
import com.rentrix.rentrixserver.repository.FlatRepository;
import com.rentrix.rentrixserver.service.FlatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlatServiceImpl implements FlatService {
	
	private final FlatRepository flatRepository;
	
	@Override
	public Page<FlatDto> getAllFlats(Pageable pageable) {
		log.info("Get list of all flats");
		return flatRepository.findAll(pageable).map(FlatMapper::toDto);
	}
	
	@Override
	public FlatDto getFlatById(Long id) {
		Flat flat = flatRepository.findById(id)
										  .orElseThrow(() -> ApiException.notFound("Flat not found with id: " + id));
		return FlatMapper.toDto(flat);
	}
	
	@Override
	public String saveFlat(FlatDto flatDto) {
		Flat flat = FlatMapper.toEntity(flatDto);
		flatRepository.save(flat);
		log.info("Save flat with id {}", flat.getId());
		return "Flat Saved";
	}
	
	@Override
	public String updateFlat(Long id, FlatDto flatDto) {
		Flat flat = flatRepository.findById(id)
										  .orElseThrow(() -> ApiException.notFound("Flat not found with id: " + id));
		FlatMapper.copyTo(flat, flatDto);
		flatRepository.save(flat);
		log.info("Update flat with id {}", id);
		return "Flat Updated";
	}
	
	@Override
	public String deleteFlat(Long id) {
		if (!flatRepository.existsById(id)) {
			throw ApiException.notFound("Flat not found with id: " + id);
		}
		log.info("Delete flat with id {}", id);
		flatRepository.deleteById(id);
		return "Flat deleted";
	}
	
}