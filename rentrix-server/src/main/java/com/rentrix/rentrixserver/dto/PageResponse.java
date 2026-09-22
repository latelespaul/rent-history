package com.rentrix.rentrixserver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {
	private List<T> content;
	private long totalElements;
	private int totalPages;
	private int number;
	private int size;
	
	public static <E, D> PageResponse<D> from(Page<E> page, Function<E, D> mapper) {
		return new PageResponse<>(
			page.getContent().stream().map(mapper).toList(),
			page.getTotalElements(),
			page.getTotalPages(),
			page.getNumber(),
			page.getSize()
		);
	}
	
}