package com.rentrix.rentrixserver.city;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CityRepository extends JpaRepository<City, String> {
	
	List<City> findByLiveTrueOrderByNameAsc();
	
	boolean existsByCodeAndLiveTrue(String code);
	
}
