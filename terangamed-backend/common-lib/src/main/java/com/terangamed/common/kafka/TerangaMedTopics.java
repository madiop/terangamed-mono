package com.terangamed.common.kafka;

/**
 * Constantes des topics Kafka TerangaMed.
 *
 * <p>Évite les magic strings éparpillés dans le code. Les noms doivent matcher
 * exactement ceux créés par {@code docker/kafka-init} et déclarés dans
 * {@code config-repo/application.yml} sous {@code terangamed.kafka.topics.*}.
 *
 * <p>Convention : {@code terangamed.<bounded-context>.events}
 * — un topic par contexte métier, avec event-type en header pour le routing
 * côté consumer.
 */
public final class TerangaMedTopics {

    public static final String PATIENT_EVENTS = "terangamed.patient.events";
    public static final String DOCTOR_EVENTS = "terangamed.doctor.events";
    public static final String APPOINTMENT_EVENTS = "terangamed.appointment.events";
    public static final String MEDICAL_EVENTS = "terangamed.medical.events";

    private TerangaMedTopics() {
        throw new UnsupportedOperationException("Utility class — do not instantiate");
    }
}
