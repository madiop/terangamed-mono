package com.terangamed.configserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration : démarre le Config Server complet sur un port aléatoire
 * et vérifie qu'il sert correctement les configurations centralisées
 * du backend natif ({@code config-repo/}).
 */
@ActiveProfiles({"test", "native"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfigServerApplicationTests {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void context_loads() {
        // Test simple : le contexte Spring démarre sans erreur
    }

    @Test
    void health_endpoint_should_be_public() {
        ResponseEntity<Map> resp = restTemplate
                .getForEntity("http://localhost:" + port + "/actuator/health", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("status");
    }

    @Test
    void config_endpoint_should_require_authentication() {
        ResponseEntity<String> resp = restTemplate
                .getForEntity("http://localhost:" + port + "/application/default", String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void should_serve_common_application_yml() {
        ResponseEntity<Map> resp = restTemplate
                .withBasicAuth("testuser", "testpass")
                .getForEntity("http://localhost:" + port + "/application/default", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("name")).isEqualTo("application");
        assertThat(resp.getBody()).containsKey("propertySources");
    }

    @Test
    void should_serve_docker_profile_overrides() {
        ResponseEntity<Map> resp = restTemplate
                .withBasicAuth("testuser", "testpass")
                .getForEntity("http://localhost:" + port + "/application/docker", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("profiles")).asString().contains("docker");
    }

    @Test
    void should_reject_invalid_credentials() {
        ResponseEntity<String> resp = restTemplate
                .withBasicAuth("wrong", "wrong")
                .getForEntity("http://localhost:" + port + "/application/default", String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
