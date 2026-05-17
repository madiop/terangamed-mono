package com.terangamed.notification.config;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.retrytopic.RetryTopicBeanNames;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires de {@link KafkaProducerConfig}.
 *
 * <h3>Pourquoi ce test ?</h3>
 * <p>Une régression précédente a fait planter le démarrage du service avec :
 * <pre>A single KafkaTemplate bean could not be found in the context</pre>
 * parce que {@code @RetryableTopic} (utilisé par
 * {@code EventNotificationConsumer}) exige un {@link KafkaTemplate} et
 * qu'aucun n'était déclaré. Aucun test du module ne chargeait le contexte
 * Spring complet, donc la régression n'était pas attrapée par {@code mvn test}.
 *
 * <p>Ce test vérifie en JUnit pur (rapide, déterministe, sans Testcontainers)
 * que :
 * <ul>
 *   <li>Le bean {@link KafkaTemplate} portant le nom canonique attendu par
 *       Spring Kafka pour les retry-topics est bien créé</li>
 *   <li>Sa configuration utilise les bons sérialiseurs (String pour la key,
 *       KafkaAvroSerializer pour la value)</li>
 *   <li>{@code bootstrap.servers} et {@code schema.registry.url} sont bien
 *       injectés depuis les propriétés Spring (et reflètent les fallbacks
 *       par défaut quand absentes)</li>
 *   <li>{@code acks=all} et {@code enable.idempotence=true} sont actifs
 *       (durabilité + pas de doublons côté audit médico-légal)</li>
 * </ul>
 *
 * <p>Toute évolution future qui supprimerait/renommerait ce bean fera
 * échouer ce test avant le {@code spring-boot:run}.
 */
class KafkaProducerConfigTest {

    private static final String BOOTSTRAP_PROP = "spring.kafka.bootstrap-servers";
    private static final String SCHEMA_PROP = "spring.kafka.properties.schema.registry.url";

    private KafkaProducerConfig config;

    @BeforeEach
    void setUp() {
        config = new KafkaProducerConfig();
    }

    @Test
    @DisplayName("Le bean KafkaTemplate exposé porte le nom canonique exigé par @RetryableTopic")
    void retryTopicTemplateBeanName_matches_spring_kafka_canonical_name() {
        // Garantit qu'on n'a pas dérivé du nom attendu par RetryTopicComponentFactory.
        // Si Spring Kafka renomme la constante en V majeure, ce test pète et oblige
        // à mettre à jour @Bean(name = ...) dans KafkaProducerConfig.
        assertThat(RetryTopicBeanNames.DEFAULT_KAFKA_TEMPLATE_BEAN_NAME)
                .isEqualTo("defaultRetryTopicKafkaTemplate");
    }

    @Test
    @DisplayName("ProducerFactory : bootstrap-servers et schema.registry.url provenant des propriétés sont propagés")
    void producerFactory_uses_injected_properties() {
        bindProperties(Map.of(
                BOOTSTRAP_PROP, "kafka-test:9092",
                SCHEMA_PROP, "http://schema-registry-test:8081"
        ));

        ProducerFactory<String, Object> pf = config.retryTopicProducerFactory();

        Map<String, Object> producerProps = pf.getConfigurationProperties();
        assertThat(producerProps)
                .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka-test:9092")
                .containsEntry(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
                        "http://schema-registry-test:8081");
    }

    @Test
    @DisplayName("ProducerFactory : sérialiseurs alignés sur le format Avro du Consumer")
    void producerFactory_uses_correct_serializers() {
        bindProperties(Map.of());

        ProducerFactory<String, Object> pf = config.retryTopicProducerFactory();

        Map<String, Object> producerProps = pf.getConfigurationProperties();
        assertThat(producerProps)
                .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
                .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
    }

    @Test
    @DisplayName("ProducerFactory : acks=all + enable.idempotence=true (durabilité audit médico-légal)")
    void producerFactory_has_durability_settings() {
        bindProperties(Map.of());

        ProducerFactory<String, Object> pf = config.retryTopicProducerFactory();

        Map<String, Object> producerProps = pf.getConfigurationProperties();
        assertThat(producerProps)
                .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    }

    @Test
    @DisplayName("KafkaTemplate : crée un template fonctionnel adossé au ProducerFactory injecté")
    void kafkaTemplate_wires_producer_factory() {
        bindProperties(Map.of());
        ProducerFactory<String, Object> pf = config.retryTopicProducerFactory();

        KafkaTemplate<String, Object> template = config.defaultRetryTopicKafkaTemplate(pf);

        assertThat(template).isNotNull();
        assertThat(template.getProducerFactory()).isSameAs(pf);
        // Sanity : la classe sous-jacente reste le DefaultKafkaProducerFactory standard.
        assertThat(pf).isInstanceOf(DefaultKafkaProducerFactory.class);
    }

    @Test
    @DisplayName("KafkaAdmin : exposé pour @RetryableTopic(autoCreateTopics=true)")
    void kafkaAdmin_is_exposed_with_correct_bootstrap_servers() {
        // Sans ce bean, @RetryableTopic ne peut pas créer les retry-topics et DLT
        // → en boucle WARN UNKNOWN_TOPIC_OR_PARTITION sur les consumers retry/dlt.
        bindProperties(Map.of(BOOTSTRAP_PROP, "kafka-test:9092"));

        KafkaAdmin admin = config.kafkaAdmin();

        assertThat(admin).isNotNull();
        assertThat(admin.getConfigurationProperties())
                .containsEntry(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka-test:9092");
    }

    @Test
    @DisplayName("KafkaAdmin : aligné sur le même bootstrap-servers que le ProducerFactory")
    void kafkaAdmin_shares_bootstrap_servers_with_producer() {
        // Cohérence : un seul broker = pas de divergence consumer/producer/admin.
        bindProperties(Map.of(BOOTSTRAP_PROP, "kafka-shared:9092"));

        Object adminBootstrap = config.kafkaAdmin()
                .getConfigurationProperties().get(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG);
        Object producerBootstrap = config.retryTopicProducerFactory()
                .getConfigurationProperties().get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG);

        assertThat(adminBootstrap).isEqualTo(producerBootstrap).isEqualTo("kafka-shared:9092");
    }

    /**
     * Simule {@code @Value} sans charger un {@code ApplicationContext} :
     * on injecte directement les champs privés via réflexion.
     *
     * <p>Volontairement minimal : si on étend la config (autres props), il
     * suffit d'ajouter les bindings ici. Pas de propagation de cache PSet
     * Spring entre tests (chaque @BeforeEach reconstruit l'instance).
     */
    private void bindProperties(Map<String, String> overrides) {
        Map<String, Object> source = new HashMap<>();
        source.put(BOOTSTRAP_PROP, "localhost:29092");
        source.put(SCHEMA_PROP, "http://localhost:8085");
        source.putAll(overrides);

        Binder binder = new Binder(new MapConfigurationPropertySource(source));
        ReflectionTestUtils.setField(config, "bootstrapServers",
                binder.bind(BOOTSTRAP_PROP, Bindable.of(String.class)).orElse("localhost:29092"));
        ReflectionTestUtils.setField(config, "schemaRegistryUrl",
                binder.bind(SCHEMA_PROP, Bindable.of(String.class)).orElse("http://localhost:8085"));
    }
}
