package com.rentrix.rentrixserver.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

@Data
@Entity
@Table(name = "issues")
public class Issue {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@ManyToOne
	@JoinColumn(name = "flat_id", nullable = false)
	private Flat flat;
	
	@Lob
	private String description;
	
	private LocalDate reportedDate;
	private LocalDate resolvedDate;
	
	private Boolean isResolved;
	
}