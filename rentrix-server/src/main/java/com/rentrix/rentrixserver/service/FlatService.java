package com.rentrix.rentrixserver.service;

import com.rentrix.rentrixserver.dto.FlatDto;
import com.rentrix.rentrixserver.dto.PageResponse;
import org.springframework.data.domain.Pageable;

public interface FlatService {
	PageResponse<FlatDto> getAllFlats(Pageable pageable);
	
	FlatDto getFlatById(Long id);
	
	String saveFlat(FlatDto flatDto);
	
	String updateFlat(Long id, FlatDto flatDto);
	
	String deleteFlat(Long id);
	
}