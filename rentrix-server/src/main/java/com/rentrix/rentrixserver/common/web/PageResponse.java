package com.rentrix.rentrixserver.common.web;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Pagination envelope for list endpoints (D-59). */
public record PageResponse<T>(List<T> items, PageInfo page) {
	
	public static <E, T> PageResponse<T> of(Page<E> page, Function<? super E, ? extends T> mapper) {
		List<T> items = page.getContent().stream().<T>map(mapper).toList();
		return new PageResponse<>(items,
			new PageInfo(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()));
	}
	
	public record PageInfo(int number, int size, long totalItems, int totalPages) {
	}
	
}
