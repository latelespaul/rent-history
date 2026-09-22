package com.rentrix.rentrixserver.mapper;

import com.rentrix.rentrixserver.dto.UserDto;
import com.rentrix.rentrixserver.entity.User;

public final class UserMapper {
	private UserMapper() {}
	
	public static UserDto toDto(User user) {
		if (user == null) return null;
		UserDto dto = new UserDto();
		dto.setId(user.getId());
		dto.setEmail(user.getEmail());
		dto.setName(user.getName());
		dto.setRole(user.getRole() != null ? user.getRole().name() : null);
		return dto;
	}
	
}