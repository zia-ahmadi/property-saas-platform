package com.example.property_core.common;

import com.example.property_core.exception.ApiException;

import org.springframework.http.HttpStatus;

public final class IfMatchVersion {
    private IfMatchVersion() {
    }

    public static long parse(String header) {
        if (header == null || !header.matches("\"(?:0|[1-9][0-9]*)\"")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "If-Match must be a quoted non-negative version");
        }
        try {
            return Long.parseLong(header.substring(1, header.length() - 1));
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "If-Match version is out of range");
        }
    }
}
