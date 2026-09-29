package com.rentrix.rentrixserver.mapper;

import com.rentrix.rentrixserver.dto.FlatDto;
import com.rentrix.rentrixserver.entity.Flat;

public final class FlatMapper {
	
	private FlatMapper() {}
	
	public static FlatDto toDto(Flat flat) {
		if (flat == null) return null;
		FlatDto dto = new FlatDto();
		dto.setAddress(flat.getAddress());
		dto.setCity(flat.getCity());
		dto.setState(flat.getState());
		dto.setNumberOfRooms(flat.getNumberOfRooms());
		dto.setArea(flat.getArea());
		dto.setRent(flat.getRent());
		dto.setDescription(flat.getDescription());
		dto.setIsAvailable(flat.getIsAvailable());
		return dto;
	}
	
	public static Flat toEntity(FlatDto dto) {
		if (dto == null) return null;
		Flat flat = new Flat();
		flat.setAddress(dto.getAddress());
		flat.setCity(dto.getCity());
		flat.setState(dto.getState());
		flat.setNumberOfRooms(dto.getNumberOfRooms());
		flat.setArea(dto.getArea());
		flat.setRent(dto.getRent());
		flat.setDescription(dto.getDescription());
		flat.setIsAvailable(dto.getIsAvailable());
		return flat;
	}
	
	public static void copyTo(Flat flat, FlatDto dto) {
		flat.setAddress(dto.getAddress());
		flat.setCity(dto.getCity());
		flat.setState(dto.getState());
		flat.setNumberOfRooms(dto.getNumberOfRooms());
		flat.setArea(dto.getArea());
		flat.setRent(dto.getRent());
		flat.setDescription(dto.getDescription());
		flat.setIsAvailable(dto.getIsAvailable());
	}
	
}