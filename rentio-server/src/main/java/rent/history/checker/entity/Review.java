package rent.history.checker.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

@Entity
@Table(name = "reviews")
public class Review {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;
	
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "flat_id", nullable = false)
	private Flat flat;
	
	@NotBlank
	private String title;
	
	@NotBlank
	@Lob
	private String content;
	
	@Min(1)
	@Max(10)
	private int rating;
	
	private LocalDate reviewDate;
	
}