package com.rentrix.rentrixserver.entity;

import com.rentrix.rentrixserver.entity.constants.ReviewStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
@Entity
@Table(name = "reviews")
public class Review {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "flat_id", nullable = false)
	private Flat flat;
	
	@NotBlank
	private String title;
	
	@NotBlank
	@Lob
	private String content;
	
	@Min(1)
	@Max(10)
	private Integer rating;
	
	private LocalDate reviewDate;
	
	@Enumerated(EnumType.STRING)
	private ReviewStatus status = ReviewStatus.PENDING;
	
}