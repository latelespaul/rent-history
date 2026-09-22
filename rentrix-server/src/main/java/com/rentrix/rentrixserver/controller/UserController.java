package com.rentrix.rentrixserver.controller;

import com.rentrix.rentrixserver.entity.User;
import com.rentrix.rentrixserver.service.impl.UserServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/users")
public class UserController {
	
	private final UserServiceImpl userServiceImpl;
	
	@Autowired
	public UserController(UserServiceImpl userServiceImpl) {
		this.userServiceImpl = userServiceImpl;
	}
	
	@GetMapping
	public List<User> getAllUsers() {
		return userServiceImpl.getAllUsers();
	}
	
	@GetMapping("/{id}")
	public ResponseEntity<User> getUserById(@PathVariable Long id) {
		User user = userServiceImpl.getUserById(id);
		return user != null ? ResponseEntity.ok(user) : ResponseEntity.notFound().build();
	}
	
	@GetMapping("/username/{username}")
	public ResponseEntity<User> getUserByUsername(@PathVariable String username) {
		Optional<User> user = userServiceImpl.getUserByUsername(username);
		return user.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}
	
	@GetMapping("/telephone/{telephone}")
	public ResponseEntity<User> getUserByTelephone(@PathVariable String telephone) {
		Optional<User> user = userServiceImpl.getUserByTelephone(telephone);
		return user.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}
	
	@GetMapping("/email/{email}")
	public ResponseEntity<User> getUserByEmail(@PathVariable String email) {
		Optional<User> user = userServiceImpl.getUserByEmail(email);
		return user.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

//	@GetMapping("/me/reviews")
//	public List<ReviewDto> getMyReviews() {
//		return userServiceImpl.getUserByUsername("me");
//
//	}
	
	@PostMapping
	public ResponseEntity<User> createUser(@RequestBody User user) {
		User savedUser = userServiceImpl.saveUser(user);
		return ResponseEntity.status(201).body(savedUser);
	}
	
	@PutMapping("/{id}")
	public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User user) {
		User existingUser = userServiceImpl.getUserById(id);
		if (existingUser != null) {
			user.setId(id);
			User updatedUser = userServiceImpl.saveUser(user);
			return ResponseEntity.ok(updatedUser);
		} else {
			return ResponseEntity.notFound().build();
		}
	}
	
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
		userServiceImpl.deleteUser(id);
		return ResponseEntity.noContent().build();
	}
	
}