package com.example.property_core.common;

import java.util.List;
import java.util.function.Function;

public record Page<T>(
        List<T> content,
        int number,
        int size,
        long totalElements,
        int totalPages) {
    public <R> Page<R> map(Function<T, R> mapper) {
        return new Page<>(content.stream().map(mapper).toList(), number, size, totalElements, totalPages);
    }
}
