package com.rentrix.rentrixserver.dataSeed;

import com.github.javafaker.Faker;
import com.rentrix.rentrixserver.entity.Flat;
import com.rentrix.rentrixserver.repository.FlatRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Random;

@Component
@Profile("dev")
public class FlatDataSeed {
	private final Faker faker = new Faker(Locale.of("en", "IN"));
	private final Random random = new Random();
	
	@Bean
	CommandLineRunner seedFlats(FlatRepository flatRepository) {
		return args -> {
			
			if (flatRepository.count() > 0) {
				return;
			}
			
			for (int i = 0; i < 20; i++) {
				
				Flat flat = new Flat();
				flat.setAddress(faker.address().streetAddress());
				flat.setCity(faker.address().city());
				flat.setState(faker.address().state());
				flat.setNumberOfRooms(faker.number().numberBetween(1, 6));
				flat.setArea(BigDecimal.valueOf(faker.number().numberBetween(500, 3000)));
				flat.setRent(BigDecimal.valueOf(faker.number().numberBetween(8000, 50000)));
				flat.setDescription(faker.lorem().paragraph());
				flat.setIsAvailable(faker.bool().bool());
				flatRepository.save(flat);
			}
		};
	}
	
}