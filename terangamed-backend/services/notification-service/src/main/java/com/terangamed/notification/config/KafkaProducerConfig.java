package com.terangamed.notification.config;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.retrytopic.RetryTopicBeanNames;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration du producer Kafka du notification-service.
 *
 * <h3>Pourquoi un producer dans un service "consumer pur" ?</h3>
 * <p>{@link com.terangamed.notification.consumer.EventNotificationConsumer}
 * utilise {@link org.springframework.kafka.annotation.RetryableTopic} sur ses
 * 4 listeners. Cette annotation déclenche, en cas d'erreur de traitement,
 * la republication du message vers un <em>retry-topic</em> (un par tentative)
 * puis vers le <em>Dead Letter Topic</em> (DLT). Cette republication exige
 * un {@link KafkaTemplate} disponible dans le contexte Spring.
 *
 * <p>Sans cette configuration, on observe au démarrage :
 * <pre>
 *   Error creating bean with name 'eventNotificationConsumer' :
 *   A single KafkaTemplate bean could not be found in the context;
 *   a single instance must exist, or one specifically named
 *   defaultRetryTopicKafkaTemplate
 * </pre>
 *
 * <h3>Choix de typage : {@code KafkaTemplate<String, Object>}</h3>
 * <p>Le {@code value} sérialisé sera typiquement un {@link org.apache.avro.generic.GenericRecord}
 * (cohérent avec la désérialisation côté {@link KafkaConsumerConfig}). On
 * type en {@code Object} pour rester souple : le {@link KafkaAvroSerializer}
 * de Confluent sait gérer aussi bien {@code GenericRecord} que
 * {@code SpecificRecord}, ce qui évite tout couplage avec un schéma précis.
 *
 * <h3>Nom du bean : {@code defaultRetryTopicKafkaTemplate}</h3>
 * <p>C'est le nom canonique attendu par
 * {@link org.springframework.kafka.retrytopic.RetryTopicComponentFactory}
 * (constante {@link RetryTopicBeanNames#DEFAULT_KAFKA_TEMPLATE_BEAN_NAME}).
 * Le nommage explicite lève toute ambiguïté si un autre {@code KafkaTemplate}
 * était introduit plus tard dans le contexte.
 *
 * <h3>Cohérence de configuration</h3>
 * <p>Les propriétés {@code bootstrap-servers} et {@code schema.registry.url}
 * sont lues sur les <em>mêmes clés</em> que celles utilisées par
 * {@link KafkaConsumerConfig} pour garantir un alignement strict
 * consumer/producer (broker + Schema Registry communs).
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:29092}")
    private String bootstrapServers;

    @Value("${spring.kafka.properties.schema.registry.url:http://localhost:8085}")
    private String schemaRegistryUrl;

    /**
     * Factory utilisée exclusivement pour la republication des messages
     * vers les retry-topics et le DLT (chemin {@code @RetryableTopic}).
     *
     * <p>Réglages :
     * <ul>
     *   <li>{@code acks=all} : on garantit la durabilité des republications
     *       (un message perdu = audit incomplet, inacceptable côté médico-légal)</li>
     *   <li>{@code enable.idempotence=true} : pas de doublons en cas de
     *       retry réseau côté broker</li>
     *   <li>{@code KafkaAvroSerializer} : même format que les producers métier
     *       (patient/doctor/appointment/medical-record), donc les retry-topics
     *       peuvent être consommés à l'identique des topics primaires si besoin</li>
     * </ul>
     */
    @Bean
    public ProducerFactory<String, Object> retryTopicProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        // Schema Registry — même URL que côté consumer
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * Bean exigé par {@code @RetryableTopic} pour publier vers les
     * retry-topics et le DLT.
     *
     * <p>Le nom {@value RetryTopicBeanNames#DEFAULT_KAFKA_TEMPLATE_BEAN_NAME}
     * est imposé par Spring Kafka (cf. {@code RetryTopicComponentFactory}) ;
     * ne pas renommer sans aussi adapter
     * {@link org.springframework.kafka.annotation.RetryableTopic#kafkaTemplate()}
     * sur tous les listeners concernés.
     */
    @Bean(name = RetryTopicBeanNames.DEFAULT_KAFKA_TEMPLATE_BEAN_NAME)
    public KafkaTemplate<String, Object> defaultRetryTopicKafkaTemplate(
            ProducerFactory<String, Object> retryTopicProducerFactory) {
        return new KafkaTemplate<>(retryTopicProducerFactory);
    }

    /**
     * Bean {@link KafkaAdmin} requis par {@code @RetryableTopic(autoCreateTopics="true")}.
     *
     * <h3>Pourquoi explicite ?</h3>
     * <p>Spring Boot {@code KafkaAutoConfiguration} crée un {@code KafkaAdmin}
     * automatiquement <strong>seulement si</strong> les propriétés
     * {@code spring.kafka.*} sont alignées sur le format attendu par
     * {@code KafkaProperties}. Or notification-service lit ses paramètres
     * Kafka directement via {@link Value @Value} (cf. {@code bootstrapServers}
     * et {@code schemaRegistryUrl}) — l'auto-config peut donc échouer
     * silencieusement.
     *
     * <p>En déclarant explicitement le bean, on garantit que
     * {@link org.springframework.kafka.retrytopic.RetryTopicConfigurer} dispose
     * d'un {@code AdminClient} pour créer les retry-topics et le DLT au
     * démarrage du contexte Spring (avant que les listeners ne se mettent
     * à poll). Sans ce bean, on observe en boucle :
     * <pre>UNKNOWN_TOPIC_OR_PARTITION : terangamed.&lt;X&gt;.events-retry-1000</pre>
     *
     * <h3>Réutilisation du {@code bootstrapServers}</h3>
     * <p>Volontaire : un seul point de configuration broker pour tout le
     * service (cohérence consumer / producer / admin).
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> props = new HashMap<>();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(props);
    }
}
