package com.example.property_core.vendor;

import com.fasterxml.jackson.annotation.JsonSetter;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UpdateVendorRequest {
    @Size(max = 120)
    private String name;
    @Size(max = 100)
    private String serviceType;
    @Email
    @Size(max = 320)
    private String email;
    @Size(max = 40)
    private String phone;
    @NotNull
    private Boolean active;
    private boolean nameProvided;
    private boolean serviceTypeProvided;
    private boolean emailProvided;
    private boolean phoneProvided;
    private boolean activeProvided;

    @JsonSetter
    public void setName(String name) {
        this.name = name;
        nameProvided = true;
    }

    @JsonSetter
    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
        serviceTypeProvided = true;
    }

    @JsonSetter
    public void setEmail(String email) {
        this.email = email;
        emailProvided = true;
    }

    @JsonSetter
    public void setPhone(String phone) {
        this.phone = phone;
        phoneProvided = true;
    }

    @JsonSetter
    public void setActive(Boolean active) {
        this.active = active;
        activeProvided = true;
    }

    public String name() { return name; }
    public String serviceType() { return serviceType; }
    public String email() { return email; }
    public String phone() { return phone; }
    public Boolean active() { return active; }
    public boolean nameProvided() { return nameProvided; }
    public boolean serviceTypeProvided() { return serviceTypeProvided; }
    public boolean emailProvided() { return emailProvided; }
    public boolean phoneProvided() { return phoneProvided; }
    public boolean activeProvided() { return activeProvided; }

    public boolean isEmpty() {
        return !nameProvided && !serviceTypeProvided && !emailProvided && !phoneProvided && !activeProvided;
    }

    @AssertTrue(message = "Patch must contain valid fields")
    public boolean isValidPatch() {
        return !isEmpty()
                && (!nameProvided || name != null && !name.isBlank() && name.length() <= 120)
                && (!serviceTypeProvided
                        || serviceType != null && !serviceType.isBlank() && serviceType.length() <= 100)
                && (!activeProvided || active != null);
    }
}
