package com.orderprocessing.userservice.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A JSON array may contain null, and a constraint on a container element only applies to the elements that are
 * there - @Pattern says nothing about a null one. Without @NotNull beside it the null passes validation and
 * reaches UserService.updateRoles, which calls strip() on every element.
 */
class UpdateUserRolesRequestValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void aNullRoleIsRejected() {
        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");
        roles.add(null);

        assertThat(validator.validate(request(roles)))
                .describedAs("a null role must be a validation failure, not a runtime crash")
                .isNotEmpty();
    }

    @Test
    void aMalformedRoleIsStillRejected() {
        assertThat(validator.validate(request(Set.of("not-a-role")))).isNotEmpty();
    }

    @Test
    void anEmptyRoleSetIsStillRejected() {
        assertThat(validator.validate(request(Set.of()))).isNotEmpty();
    }

    @Test
    void aWellFormedRoleSetIsAccepted() {
        assertThat(validator.validate(request(Set.of("ROLE_USER", "ROLE_ADMIN")))).isEmpty();
    }

    private UpdateUserRolesRequest request(Set<String> roles) {
        UpdateUserRolesRequest request = new UpdateUserRolesRequest();
        request.setRoles(roles);
        return request;
    }
}
