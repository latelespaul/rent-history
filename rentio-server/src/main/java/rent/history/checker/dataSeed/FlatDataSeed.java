package rent.history.checker.dataSeed;

import com.github.javafaker.Faker;
import org.springframework.boot.CommandLineRunner;
import rent.history.checker.entity.Flat;
import rent.history.checker.repository.FlatRepository;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Random;

//@Component
public class FlatDataSeed {
	private final Faker faker = new Faker(new Locale("en-IN"));
	private final Random random = new Random();
	
	//	@Bean
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
				flat.setAvailable(faker.bool().bool());
				flatRepository.save(flat);
			}
		};
	}
	
}