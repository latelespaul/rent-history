package com.rentrix.rentrixserver.repository;

import com.rentrix.rentrixserver.entity.UserDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserDetailRepository extends JpaRepository<UserDetail, Long> {
	
	Optional<UserDetail> findByTelephone(String telephone);
	
}