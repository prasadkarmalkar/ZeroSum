package com.zerosum.zerosum.common.web;

import java.util.List;
import java.util.function.Function;

/**
 * One page of results, in a JSON shape the API owns, instead of serializing Spring's {@code Page},
 * whose JSON layout is an internal detail that can change between Spring versions.
 *
 * @param content       the items on this page
 * @param page          the zero-based page number
 * @param size          the page size actually used (after capping)
 * @param totalElements the number of items across all pages
 * @param totalPages    the number of pages; 0 when there are no items
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public PageResponse {
		content = List.copyOf(content);
	}

	public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
		int totalPages = (int) ((totalElements + size - 1) / size);
		return new PageResponse<>(content, page, size, totalElements, totalPages);
	}

	public <R> PageResponse<R> map(Function<T, R> mapper) {
		return new PageResponse<>(content.stream().map(mapper).toList(), page, size, totalElements, totalPages);
	}

}
