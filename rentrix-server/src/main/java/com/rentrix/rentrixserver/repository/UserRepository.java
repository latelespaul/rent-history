package com.rentrix.rentrixserver.repository;

import com.rentrix.rentrixserver.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
	Optional<User> findByUsername(String username);
	
	Optional<User> findByTelephone(String telephone);
	
	Optional<User> findByEmail(String email);
	
}