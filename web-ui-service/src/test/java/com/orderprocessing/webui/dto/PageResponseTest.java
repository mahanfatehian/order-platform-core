package com.orderprocessing.webui.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The page number in a PageResponse is whatever the backend echoed. Every paged endpoint bounds it below at zero
 * with @Min(0) and none bounds it above, so a hand-written request can put Integer.MAX_VALUE in there.
 */
class PageResponseTest {
    @Test
    void aPageBeyondTheEndOffersNoNextLink() {
        PageResponse<String> page = new PageResponse<>(List.of(), Integer.MAX_VALUE, 20, 3, 1, false, true);

        assertThat(page.hasNext())
                .describedAs("page + 1 wraps negative, and a negative is below any page count")
                .isFalse();
    }

    @Test
    void anOrdinaryPageStillPagesForward() {
        PageResponse<String> page = new PageResponse<>(List.of("a"), 0, 20, 45, 3, true, false);

        assertThat(page.hasNext()).isTrue();
        assertThat(page.hasPrevious()).isFalse();
    }

    @Test
    void theLastPageStopsPagingForward() {
        PageResponse<String> page = new PageResponse<>(List.of("a"), 2, 20, 45, 3, false, true);

        assertThat(page.hasNext()).isFalse();
        assertThat(page.hasPrevious()).isTrue();
    }

    @Test
    void anEmptyResultOffersNeitherDirection() {
        PageResponse<String> page = PageResponse.empty(0, 20);

        assertThat(page.hasNext()).isFalse();
        assertThat(page.hasPrevious()).isFalse();
    }
}
