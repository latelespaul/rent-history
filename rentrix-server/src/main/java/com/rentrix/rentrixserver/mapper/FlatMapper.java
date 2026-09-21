package com.rentrix.rentrixserver.mapper;

import com.rentrix.rentrixserver.dto.FlatDto;
import com.rentrix.rentrixserver.entity.Flat;
import org.springframework.stereotype.Component;

@Component
public class FlatMapper {
	
	public FlatDto toDto(Flat flat) {
		FlatDto flatDto = new FlatDto();
		flatDto.setAddress(flat.getAddress());
		flatDto.setCity(flat.getCity());
		flatDto.setState(flat.getState());
		
		flatDto.setArea(flat.getArea());
		flatDto.setNumberOfRooms(flat.getNumberOfRooms());
		flatDto.setDescription(flat.getDescription());
		
		flatDto.setRent(flat.getRent());
		flatDto.setAvailable(flat.getAvailable());
		
		return flatDto;
	}
	
	public Flat toEntity(FlatDto flatDto) {
		Flat flat = new Flat();
		flat.setAddress(flatDto.getAddress());
		flat.setCity(flatDto.getCity());
		flat.setState(flatDto.getState());
		
		flat.setArea(flatDto.getArea());
		flat.setNumberOfRooms(flatDto.getNumberOfRooms());
		flat.setDescription(flatDto.getDescription());
		
		flat.setRent(flatDto.getRent());
		flat.setAvailable(flatDto.getAvailable());
		
		return flat;
	}
	
}