package rent.history.checker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RentHistoryApplication {

	public static void main(String[] args) {
		String strng = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
		System.out.println("Env Variable is: "+strng);
		SpringApplication.run(RentHistoryApplication.class, args);
	}

}
