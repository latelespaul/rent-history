package rent.history.checker.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ReviewDto {
	public Long id;
	public Long userId;
	public String userName;
	public Long flatId;
	public String title;
	public String content;
	public Integer rating;
	public LocalDate reviewDate;
	
}