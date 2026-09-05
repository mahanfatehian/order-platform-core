package com.orderprocessing.storeservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LIMIT/OFFSET over a sort column that is not unique has no defined order inside a group of equal values, so a
 * page boundary landing inside such a group lets one plan repeat a row that another plan already returned. The
 * id tiebreaker makes every page deterministic.
 */
class StoreControllerPaginationTest {
    @Test
    void everySortEndsWithTheUniqueTiebreaker() {
        for (String property : List.of("createdAt", "name", "price")) {
            for (Sort.Direction direction : Sort.Direction.values()) {
                PageRequest request = StoreController.pageRequest(0, 20, property + "," + direction.name().toLowerCase());
                List<Sort.Order> orders = request.getSort().toList();

                assertThat(orders)
                        .describedAs("sort for %s,%s", property, direction)
                        .hasSizeGreaterThanOrEqualTo(2);
                assertThat(orders.get(orders.size() - 1).getProperty())
                        .describedAs("last sort component for %s,%s must be the unique id", property, direction)
                        .isEqualTo("id");
            }
        }
    }

    @Test
    void anUnknownSortStillCarriesTheTiebreaker() {
        List<Sort.Order> orders = StoreController.pageRequest(0, 20, "notAColumn,desc").getSort().toList();

        assertThat(orders.get(orders.size() - 1).getProperty()).isEqualTo("id");
    }
}
