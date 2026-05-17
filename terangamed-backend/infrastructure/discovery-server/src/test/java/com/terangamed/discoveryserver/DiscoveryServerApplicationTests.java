package com.terangamed.discoveryserver;

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
 * Tests d'intégration : démarre Eureka sur un port aléatoire et vérifie :
 * <ul>
 *   <li>Le contexte Spring se charge sans erreur</li>
 *   <li>{@code /actuator/health} est public</li>
 *   <li>{@code /eureka/apps} exige une authentification</li>
 *   <li>Avec credentials valides, le registry est accessible et retourne du XML</li>
 * </ul>
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiscoveryServerApplicationTests {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void context_loads() {
        // Le démarrage de @EnableEurekaServer + Spring Security ne doit pas échouer
    }

    @Test
    void health_endpoint_should_be_public() {
        ResponseEntity<Map> resp = restTemplate
                .getForEntity("http://localhost:" + port + "/actuator/health", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("status");
    }

    @Test
    void eureka_apps_should_require_authentication() {
        ResponseEntity<String> resp = restTemplate
                .getForEntity("http://localhost:" + port + "/eureka/apps", String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void eureka_apps_with_valid_credentials_should_return_registry() {
        ResponseEntity<String> resp = restTemplate
                .withBasicAuth("testuser", "testpass")
                .getForEntity("http://localhost:" + port + "/eureka/apps", String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("applications");
    }

    @Test
    void eureka_dashboard_should_require_authentication() {
        ResponseEntity<String> resp = restTemplate
                .getForEntity("http://localhost:" + port + "/", String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void eureka_dashboard_with_valid_credentials_should_be_accessible() {
        ResponseEntity<String> resp = restTemplate
                .withBasicAuth("testuser", "testpass")
                .getForEntity("http://localhost:" + port + "/", String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void should_reject_invalid_credentials() {
        ResponseEntity<String> resp = restTemplate
                .withBasicAuth("wrong", "wrong")
                .getForEntity("http://localhost:" + port + "/eureka/apps", String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
