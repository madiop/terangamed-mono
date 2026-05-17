package com.terangamed.notification.service;

import com.terangamed.notification.entity.Notification;
import com.terangamed.notification.entity.NotificationStatus;
import com.terangamed.notification.mapper.NotificationMapper;
import com.terangamed.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository repository;

    NotificationMapper mapper;
    NotificationService service;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(NotificationMapper.class);
        service = new NotificationService(repository, mapper);
    }

    @Test
    void ingest_persists_notification_with_received_status() {
        UUID eventId = UUID.randomUUID();
        when(repository.existsByEventId(eventId)).thenReturn(false);
        when(repository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean newlyPersisted = service.ingest(eventId, "terangamed.patient.events",
                "patient.created", "Patient", "42", "{\"patientId\":42}");

        assertThat(newlyPersisted).isTrue();
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getSourceTopic()).isEqualTo("terangamed.patient.events");
        assertThat(saved.getEventType()).isEqualTo("patient.created");
        assertThat(saved.getAggregateType()).isEqualTo("Patient");
        assertThat(saved.getAggregateId()).isEqualTo("42");
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.RECEIVED);
        assertThat(saved.getReceivedAt()).isNotNull();
    }

    @Test
    void ingest_skips_when_event_id_already_known() {
        UUID eventId = UUID.randomUUID();
        when(repository.existsByEventId(eventId)).thenReturn(true);

        boolean result = service.ingest(eventId, "topic", "type", "Aggr", "1", null);

        assertThat(result).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void ingest_handles_unique_constraint_race_condition() {
        UUID eventId = UUID.randomUUID();
        when(repository.existsByEventId(eventId)).thenReturn(false);
        when(repository.save(any(Notification.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        boolean result = service.ingest(eventId, "topic", "type", "Aggr", "1", null);

        // Race condition acceptée — l'autre thread/replica a déjà inséré
        assertThat(result).isFalse();
    }
}
