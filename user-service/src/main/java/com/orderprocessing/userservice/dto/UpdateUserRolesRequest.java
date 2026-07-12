package com.orderprocessing.userservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class UpdateUserRolesRequest {

    @NotEmpty
    private Set<@Pattern(regexp = "^ROLE_[A-Z][A-Z0-9_]*$", message = "Invalid role name") String> roles;
}
