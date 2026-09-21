package com.rentrix.rentrixserver.service;

import com.rentrix.rentrixserver.dto.FlatDto;

import java.util.List;

public interface FlatService {
	
	List<FlatDto> getAllFlats();
	
	FlatDto getFlatById(Long id);
	
	String saveFlat(FlatDto flatDto);
	
	String updateFlat(Long id, FlatDto flatDto);
	
	String deleteFlat(Long id);
	
}