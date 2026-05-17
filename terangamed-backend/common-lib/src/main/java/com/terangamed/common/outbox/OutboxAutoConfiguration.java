package com.terangamed.common.outbox;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.HashMap;
import java.util.Map;

/**
 * Auto-configuration Outbox + Kafka producer pour bytes.
 *
 * <p>Activée automatiquement quand un service importe common-lib AVEC les
 * dépendances Kafka + Avro + Confluent dans son POM. Sinon, le bytecode reste
 * compilable (deps optionnelles dans common-lib) mais les beans ne sont pas
 * enregistrés — l'app démarre sans Kafka.
 *
 * <h3>Beans exposés</h3>
 * <ul>
 *   <li>{@link OutboxProperties} (préfixe {@code terangamed.outbox})</li>
 *   <li>{@link KafkaAvroSerializer} configuré avec l'URL Schema Registry</li>
 *   <li>{@code KafkaTemplate<String, byte[]>} dédié à l'outbox relay</li>
 * </ul>
 *
 * <h3>Conditions d'activation</h3>
 * Le scheduler n'est activé que si {@code terangamed.outbox.enabled=true} (défaut).
 * On peut désactiver via {@code terangamed.outbox.enabled=false} en test.
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass({KafkaTemplate.class, KafkaAvroSerializer.class})
@ConditionalOnProperty(prefix = "terangamed.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
// Note : on définit OutboxProperties via un @Bean explicite ci-dessous plutôt
// que via @EnableConfigurationProperties — pour avoir un nom de bean prévisible
// ('outboxProperties') référençable depuis la SpEL @Scheduled de OutboxEventRelay.
// @EnableConfigurationProperties enregistrerait le bean sous le nom
// 'terangamed.outbox-com.terangamed.common.outbox.OutboxProperties'.
@EnableScheduling
@EnableKafka
// @AutoConfigurationPackage ajoute le package outbox à la liste utilisée par
// JpaRepositoriesAutoConfiguration et HibernateJpaAutoConfiguration. Cela permet
// à la fois la détection de l'entité OutboxEvent ET du repository OutboxEventRepository
// SANS écraser le scan du service consommateur (contrairement à @EnableJpaRepositories
// qui REMPLACE la config par défaut).
@AutoConfigurationPackage(basePackageClasses = OutboxEvent.class)
@Import({OutboxEventPublisher.class, OutboxEventRelay.class})
public class OutboxAutoConfiguration {

    /**
     * Bean {@link OutboxProperties} avec un nom prévisible ('outboxProperties')
     * pour permettre la SpEL {@code #{@outboxProperties...}} dans les
     * {@code @Scheduled}. Voir commentaire en tête de classe.
     */
    @Bean
    @ConfigurationProperties(prefix = "terangamed.outbox")
    public OutboxProperties outboxProperties() {
        return new OutboxProperties();
    }

    @Value("${spring.kafka.bootstrap-servers:localhost:29092}")
    private String bootstrapServers;

    @Value("${spring.kafka.properties.schema.registry.url:http://localhost:8085}")
    private String schemaRegistryUrl;

    /**
     * Serializer Avro Confluent partagé. Configuré avec l'URL Schema Registry —
     * il enregistre automatiquement les schémas la première fois qu'il les voit.
     *
     * <p><b>Subject naming = TopicRecordNameStrategy</b> :
     * Plusieurs types d'events Avro (ex: {@code PatientCreated}, {@code PatientUpdated},
     * {@code PatientArchived}) sont publiés sur le même topic
     * {@code terangamed.patient.events}. Avec la {@code TopicNameStrategy} par
     * défaut, ils partageraient le subject {@code <topic>-value} et Schema Registry
     * imposerait BACKWARD compatibility entre eux → conflit garanti
     * (NAME_MISMATCH sur les noms de records différents).
     *
     * <p>{@code TopicRecordNameStrategy} crée un subject par couple (topic, record) :
     * <ul>
     *   <li>{@code terangamed.patient.events-com.terangamed.patient.event.PatientCreated}</li>
     *   <li>{@code terangamed.patient.events-com.terangamed.patient.event.PatientUpdated}</li>
     *   <li>etc.</li>
     * </ul>
     * Chaque event évolue indépendamment, la compatibilité reste vérifiée pour
     * chaque type au sein de son propre subject.
     */
    @Bean
    @ConditionalOnMissingBean
    public KafkaAvroSerializer kafkaAvroSerializer() {
        KafkaAvroSerializer serializer = new KafkaAvroSerializer();
        Map<String, Object> config = new HashMap<>();
        config.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        config.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, true);
        config.put(KafkaAvroSerializerConfig.VALUE_SUBJECT_NAME_STRATEGY,
                TopicRecordNameStrategy.class.getName());
        serializer.configure(config, false); // false = value (pas key)
        return serializer;
    }

    /**
     * KafkaTemplate dédié au relay : value = byte[] déjà sérialisé en Avro.
     * On contourne le {@code KafkaAvroSerializer} normal car le payload arrive
     * déjà encodé depuis l'outbox.
     */
    @Bean(name = "outboxBytesKafkaTemplate")
    @ConditionalOnMissingBean(name = "outboxBytesKafkaTemplate")
    public KafkaTemplate<String, byte[]> outboxBytesKafkaTemplate() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 20);
        ProducerFactory<String, byte[]> pf = new DefaultKafkaProducerFactory<>(props);
        return new KafkaTemplate<>(pf);
    }
}
