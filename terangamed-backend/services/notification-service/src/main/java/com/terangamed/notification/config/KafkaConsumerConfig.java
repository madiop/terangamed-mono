package com.terangamed.notification.config;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration du consumer Kafka pour notification-service.
 *
 * <h3>Mode "Generic Avro"</h3>
 * On utilise {@code GenericRecord} plutôt que {@code SpecificRecord} car
 * notification-service ne dépend PAS des classes Avro générées par les
 * autres services (couplage évité, déploiement indépendant). Le payload
 * est lu de manière générique puis sérialisé en JSON via {@code toString()}.
 *
 * <h3>Tolérance aux erreurs</h3>
 * Le {@link DefaultErrorHandler} avec {@code addNotRetryableExceptions} sur
 * {@link SerializationException} permet d'envoyer directement les messages
 * mal-formés vers la DLT sans bloquer le consumer (sinon : poison pill).
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:29092}")
    private String bootstrapServers;

    @Value("${spring.kafka.properties.schema.registry.url:http://localhost:8085}")
    private String schemaRegistryUrl;

    @Bean
    public ConsumerFactory<String, GenericRecord> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "notification-service");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // Schema Registry — désérialisation générique (pas SpecificRecord)
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, false);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, GenericRecord> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, GenericRecord> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        // Manual ack — le consumer commit après persistance réussie
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // Error handler par défaut — les SerializationException ne sont pas retry-ables
        // (pas la peine de retry un message corrompu). Elles partent direct en DLT.
        DefaultErrorHandler errorHandler = new DefaultErrorHandler();
        errorHandler.addNotRetryableExceptions(SerializationException.class);
        factory.setCommonErrorHandler(errorHandler);

        // Concurrency=3 → un thread par partition (3 partitions par topic)
        factory.setConcurrency(3);
        return factory;
    }
}
