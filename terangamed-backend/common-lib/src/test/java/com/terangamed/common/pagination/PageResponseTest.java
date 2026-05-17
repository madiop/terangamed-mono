package com.terangamed.common.pagination;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    void should_map_page_directly() {
        Page<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 2), 5);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.first()).isTrue();
        assertThat(response.last()).isFalse();
    }

    @Test
    void should_map_page_with_transformer() {
        Page<Integer> page = new PageImpl<>(List.of(1, 2, 3), PageRequest.of(0, 3), 3);

        PageResponse<String> response = PageResponse.from(page, i -> "n=" + i);

        assertThat(response.content()).containsExactly("n=1", "n=2", "n=3");
        assertThat(response.first()).isTrue();
        assertThat(response.last()).isTrue();
        assertThat(response.totalPages()).isEqualTo(1);
    }

    @Test
    void should_handle_last_page() {
        Page<String> page = new PageImpl<>(List.of("c"), PageRequest.of(2, 2), 5);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.first()).isFalse();
        assertThat(response.last()).isTrue();
        assertThat(response.page()).isEqualTo(2);
    }

    @Test
    void should_handle_empty_page() {
        Page<String> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }
}
