package com.rentrix.rentrixserver.entity;

import com.rentrix.rentrixserver.entity.constants.IssueStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@Table(name = "issues")
public class Issue extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@ManyToOne
	@JoinColumn(name = "flat_id", nullable = false)
	private Flat flat;
	private String title;
	
	@Lob
	private String description;
	private LocalDate reportedDate;
	private LocalDate resolvedDate;
	
	@Enumerated(EnumType.STRING)
	private IssueStatus status;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reported_by")
	private User reportedBy;
	
}