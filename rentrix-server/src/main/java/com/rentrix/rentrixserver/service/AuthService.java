package com.rentrix.rentrixserver.service;

import com.rentrix.rentrixserver.dto.AuthResponse;
import com.rentrix.rentrixserver.dto.LoginRequest;
import com.rentrix.rentrixserver.dto.SignupRequest;
import com.rentrix.rentrixserver.dto.UserDto;

public interface AuthService {
	
	AuthResponse signup(SignupRequest request);
	
	AuthResponse login(LoginRequest request);
	
	String refresh(String refreshToken);
	
	UserDto me(Long userId);
	
	void logout(Long userId);
	
}