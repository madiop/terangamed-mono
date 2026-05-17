package com.terangamed.notification.controller;

import com.terangamed.common.exception.ResourceNotFoundException;
import com.terangamed.common.pagination.PageResponse;
import com.terangamed.common.security.SecurityRoles;
import com.terangamed.notification.dto.NotificationDto;
import com.terangamed.notification.dto.NotificationSearchCriteria;
import com.terangamed.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Historique des events Kafka consommés (audit + debug)")
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    @PreAuthorize(SecurityRoles.HAS_ADMIN)
    @Operation(summary = "Liste paginée de l'historique des notifications (ADMIN uniquement)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page de notifications (vide si aucun match)"),
            @ApiResponse(responseCode = "401", description = "Non authentifié", content = @Content),
            @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis", content = @Content)
    })
    public PageResponse<NotificationDto> search(
            @ParameterObject @ModelAttribute NotificationSearchCriteria criteria,
            @ParameterObject Pageable pageable) {
        return service.search(criteria, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.HAS_ADMIN)
    @Operation(summary = "Détail d'une notification par son ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification trouvée"),
            @ApiResponse(responseCode = "401", description = "Non authentifié", content = @Content),
            @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis", content = @Content),
            @ApiResponse(responseCode = "404", description = "Notification inconnue", content = @Content)
    })
    public NotificationDto findById(@PathVariable Long id) {
        return service.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
    }
}
