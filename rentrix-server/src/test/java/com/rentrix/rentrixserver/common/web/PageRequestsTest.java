package com.rentrix.rentrixserver.common.web;

import com.rentrix.rentrixserver.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.List;
import java.util.Map;

/** D-59: 0-based page, size 1–50 (default 20), sort only from the endpoint allow-list. */
class PageRequestsTest {

	private static final Map<String, String> SORTS = Map.of("rating", "bayesScore", "rent", "currentRentInr");
	private static final Sort DEFAULT = Sort.by(Sort.Direction.DESC, "createdAt");

	private static ApiException rejected(Integer page, Integer size, String sort) {
		return catchThrowableOfType(ApiException.class, () -> PageRequests.of(page, size, sort, SORTS, DEFAULT));
	}

	@Test
	void defaultsToFirstPageOf20WithDefaultSort() {
		Pageable p = PageRequests.of(null, null, null, SORTS, DEFAULT);
		assertThat(p.getPageNumber()).isZero();
		assertThat(p.getPageSize()).isEqualTo(20);
		assertThat(p.getSort()).isEqualTo(DEFAULT);
	}

	@Test
	void acceptsTheBounds() {
		assertThat(PageRequests.of(0, 1, null, SORTS, DEFAULT).getPageSize()).isEqualTo(1);
		assertThat(PageRequests.of(7, 50, null, SORTS, DEFAULT).getPageSize()).isEqualTo(50);
		assertThat(PageRequests.of(7, 50, null, SORTS, DEFAULT).getPageNumber()).isEqualTo(7);
	}

	@ParameterizedTest
	@ValueSource(ints = {0, -1, 51, 1000})
	void rejectsSizeOutsideOneToFifty(int size) {
		ApiException ex = rejected(0, size, null);
		assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(ex.getCode()).isEqualTo("validation_failed");
		assertThat(ex.getErrors()).singleElement().satisfies(e -> assertThat(e.field()).isEqualTo("size"));
	}

	@Test
	void rejectsNegativePage() {
		assertThat(rejected(-1, 20, null).getErrors()).singleElement()
			.satisfies(e -> assertThat(e.field()).isEqualTo("page"));
	}

	@Test
	void mapsAllowedSortNamesToProperties() {
		assertThat(PageRequests.of(0, 20, "rating,desc", SORTS, DEFAULT).getSort())
			.isEqualTo(Sort.by(Sort.Direction.DESC, "bayesScore"));
		assertThat(PageRequests.of(0, 20, "rent", SORTS, DEFAULT).getSort())
			.isEqualTo(Sort.by(Sort.Direction.ASC, "currentRentInr"));
		assertThat(PageRequests.of(0, 20, "rent,DESC", SORTS, DEFAULT).getSort())
			.isEqualTo(Sort.by(Sort.Direction.DESC, "currentRentInr"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"bayesScore,desc", "password", "rating,sideways", "rating,asc,extra", ",asc"})
	void rejectsSortsOutsideTheAllowList(String sort) {
		ApiException ex = rejected(0, 20, sort);
		assertThat(ex.getErrors()).singleElement().satisfies(e -> {
			assertThat(e.field()).isEqualTo("sort");
			assertThat(e.code()).isEqualTo("not_allowed");
		});
	}

	@Test
	void reportsEveryBadParameterAtOnce() {
		assertThat(rejected(-1, 99, "nope").getErrors()).extracting(e -> e.field())
			.containsExactlyInAnyOrder("page", "size", "sort");
	}

	@Test
	void envelopeCarriesItemsAndPageInfo() {
		Page<Integer> page = new PageImpl<>(List.of(1, 2, 3), PageRequest.of(1, 3), 8);
		PageResponse<String> response = PageResponse.of(page, i -> "item-" + i);
		assertThat(response.items()).containsExactly("item-1", "item-2", "item-3");
		assertThat(response.page()).isEqualTo(new PageResponse.PageInfo(1, 3, 8, 3));
	}

}
