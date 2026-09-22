package com.rentrix.rentrixserver.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ReviewDto {
	private Long id;
	private Long userId;
	private String userName;
	private Long flatId;
	private String flatAddress;
	private String title;
	private String content;
	private Integer rating;
	private LocalDate reviewDate;
	private String status;
	
}