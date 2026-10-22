package com.example.property_core.property;

import java.util.UUID;

public class PropertyNotFoundException extends RuntimeException {
    public PropertyNotFoundException(UUID id) {
        super("Property not found with id: " + id);
    }

    public PropertyNotFoundException(String message) {
        super(message);
    }
}
