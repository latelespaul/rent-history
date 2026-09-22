package com.rentrix.rentrixserver.service;

import com.rentrix.rentrixserver.dto.FlatDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FlatService {
	
	Page<FlatDto> getAllFlats(Pageable pageable);
	
	FlatDto getFlatById(Long id);
	
	String saveFlat(FlatDto flatDto);
	
	String updateFlat(Long id, FlatDto flatDto);
	
	String deleteFlat(Long id);
	
}