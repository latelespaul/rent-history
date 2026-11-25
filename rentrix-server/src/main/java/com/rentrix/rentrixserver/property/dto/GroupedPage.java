package com.rentrix.rentrixserver.property.dto;

import com.rentrix.rentrixserver.common.web.PageResponse;

import java.util.List;

/**
 * {@code GET /properties/grouped} (D-166): the D-59 envelope over groups ({@code page.totalItems} counts rows), plus
 * {@code totalHomes}, every matching home, for the results heading.
 */
public record GroupedPage(List<SearchItem> items, PageResponse.PageInfo page, long totalHomes) {
}
