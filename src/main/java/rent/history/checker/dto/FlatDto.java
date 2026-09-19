package rent.history.checker.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FlatDto {
	private String address;
	private String city;
	private String state;
	
	private int numberOfRooms;
	private BigDecimal area;
	private BigDecimal rent;
	
	private String description;
	private boolean isAvailable;
	
}