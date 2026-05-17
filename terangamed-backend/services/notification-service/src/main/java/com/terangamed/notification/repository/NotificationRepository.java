package com.terangamed.notification.repository;

import com.terangamed.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long>,
        JpaSpecificationExecutor<Notification> {

    /** Idempotence — vérifie si un event a déjà été reçu (avant l'INSERT). */
    boolean existsByEventId(UUID eventId);
}
