import { CalendarEvent } from 'angular-calendar';
import { AppointmentDto, AppointmentStatus } from '@api/models/appointment.model';

/**
 * Couleurs Angular Calendar (foreground = bord/texte, background = fond bloc).
 * Alignées sur la palette TerangaMed (badges).
 */
const STATUS_COLORS: Record<AppointmentStatus, { primary: string; secondary: string }> = {
  PLANNED:   { primary: '#3b82f6', secondary: '#dbeafe' },
  CONFIRMED: { primary: '#10b981', secondary: '#d1fae5' },
  COMPLETED: { primary: '#64748b', secondary: '#e2e8f0' },
  CANCELLED: { primary: '#ef4444', secondary: '#fee2e2' },
  NO_SHOW:   { primary: '#f59e0b', secondary: '#fef3c7' }
};

/**
 * Convertit un {@link AppointmentDto} en {@link CalendarEvent} pour
 * angular-calendar. Le payload original est préservé dans {@code meta}
 * pour que les handlers (click, drag) puissent y accéder.
 */
export function appointmentToCalendarEvent(
  appointment: AppointmentDto
): CalendarEvent<AppointmentDto> {
  const colors = STATUS_COLORS[appointment.status];
  return {
    id: appointment.id,
    title: appointment.patientNameSnapshot,
    start: new Date(appointment.startTime),
    end: new Date(appointment.endTime),
    color: colors,
    cssClass: `tm-event tm-event--${appointment.status.toLowerCase()}`,
    draggable: false,
    resizable: { beforeStart: false, afterEnd: false },
    meta: appointment
  };
}

/** Libellé FR d'un statut RDV — usage tooltip / légende. */
export function appointmentStatusLabel(status: AppointmentStatus): string {
  switch (status) {
    case 'PLANNED':   return 'Planifié';
    case 'CONFIRMED': return 'Confirmé';
    case 'COMPLETED': return 'Terminé';
    case 'CANCELLED': return 'Annulé';
    case 'NO_SHOW':   return 'Absent';
  }
}
