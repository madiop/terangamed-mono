package com.terangamed.configserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles({"test", "native"})
@SpringBootTest
class SecurityConfigTest {

    @Autowired
    SecurityFilterChain filterChain;

    @Test
    void security_filter_chain_should_be_registered() {
        assertThat(filterChain).isNotNull();
    }
}
