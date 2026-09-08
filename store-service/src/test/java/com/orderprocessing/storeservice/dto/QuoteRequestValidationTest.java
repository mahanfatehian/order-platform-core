package com.orderprocessing.storeservice.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A JSON array is allowed to carry a null element, and @Valid on a container element only cascades into the
 * elements that exist - it says nothing about the null ones. Without a @NotNull alongside it the request passes
 * validation and the null reaches the pricing code, which dereferences every element.
 */
class QuoteRequestValidationTest {
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
    void aNullItemIsRejected() {
        List<QuoteItemRequest> items = new ArrayList<>();
        items.add(new QuoteItemRequest(UUID.randomUUID(), 2));
        items.add(null);

        assertThat(validator.validate(new QuoteRequest(items)))
                .describedAs("a null quote item must be a validation failure, not a runtime crash")
                .isNotEmpty();
    }

    @Test
    void anItemMissingItsProductIdIsStillRejected() {
        QuoteRequest request = new QuoteRequest(List.of(new QuoteItemRequest(null, 2)));

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void aWellFormedQuoteIsAccepted() {
        QuoteRequest request = new QuoteRequest(List.of(new QuoteItemRequest(UUID.randomUUID(), 2)));

        assertThat(validator.validate(request)).isEmpty();
    }
}
