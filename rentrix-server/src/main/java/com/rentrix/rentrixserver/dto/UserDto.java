package com.rentrix.rentrixserver.dto;
import lombok.Data;

@Data
public class UserDto {
	private Long id;
	private String name;
	private String email;
	public String role;
	
}