package com.orderprocessing.userservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUserStatusRequest {

    private Boolean enabled;
    private Boolean accountNonLocked;

    @JsonIgnore
    @AssertTrue(message = "At least one account status field is required")
    public boolean isStateProvided() {
        return enabled != null || accountNonLocked != null;
    }
}
