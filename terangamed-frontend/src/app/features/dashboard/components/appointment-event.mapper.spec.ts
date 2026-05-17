import {
  appointmentToCalendarEvent,
  appointmentStatusLabel
} from './appointment-event.mapper';
import { AppointmentDto, AppointmentStatus } from '@api/models/appointment.model';

function fakeAppt(overrides: Partial<AppointmentDto> = {}): AppointmentDto {
  return {
    id: 1,
    patientId: 10,
    doctorId: 20,
    patientNameSnapshot: 'Diop Fatou',
    doctorNameSnapshot: 'Dr Sall',
    startTime: '2026-04-15T10:00:00Z',
    endTime: '2026-04-15T10:30:00Z',
    durationMinutes: 30,
    reason: 'Contrôle',
    notes: null,
    status: 'PLANNED',
    createdAt: '2026-04-10T12:00:00Z',
    updatedAt: '2026-04-10T12:00:00Z',
    createdBy: 'system',
    updatedBy: 'system',
    version: 0,
    ...overrides
  };
}

describe('appointmentToCalendarEvent', () => {
  it('mappe les champs de base + meta préservé', () => {
    const a = fakeAppt();
    const e = appointmentToCalendarEvent(a);
    expect(e.id).toBe(1);
    expect(e.title).toBe('Diop Fatou');
    // toISOString() émet toujours les millisecondes (.000Z) — on compare via getTime()
    // pour rester insensible au format texte de la chaîne ISO source.
    expect(e.start.getTime()).toBe(new Date(a.startTime).getTime());
    expect(e.end?.getTime()).toBe(new Date(a.endTime).getTime());
    expect(e.meta).toBe(a);
  });

  it('attribue un schéma de couleurs par statut', () => {
    const planned = appointmentToCalendarEvent(fakeAppt({ status: 'PLANNED' }));
    const confirmed = appointmentToCalendarEvent(fakeAppt({ status: 'CONFIRMED' }));
    const completed = appointmentToCalendarEvent(fakeAppt({ status: 'COMPLETED' }));
    const cancelled = appointmentToCalendarEvent(fakeAppt({ status: 'CANCELLED' }));
    const noShow = appointmentToCalendarEvent(fakeAppt({ status: 'NO_SHOW' }));

    expect(planned.color?.primary).toBe('#3b82f6');
    expect(confirmed.color?.primary).toBe('#10b981');
    expect(completed.color?.primary).toBe('#64748b');
    expect(cancelled.color?.primary).toBe('#ef4444');
    expect(noShow.color?.primary).toBe('#f59e0b');
  });

  it('expose un cssClass selon le statut (lowercased)', () => {
    expect(appointmentToCalendarEvent(fakeAppt({ status: 'NO_SHOW' })).cssClass).toBe(
      'tm-event tm-event--no_show'
    );
  });

  it('events non-draggable et non-resizable (V1)', () => {
    const e = appointmentToCalendarEvent(fakeAppt());
    expect(e.draggable).toBe(false);
    expect(e.resizable?.beforeStart).toBe(false);
    expect(e.resizable?.afterEnd).toBe(false);
  });
});

describe('appointmentStatusLabel', () => {
  it.each<[AppointmentStatus, string]>([
    ['PLANNED', 'Planifié'],
    ['CONFIRMED', 'Confirmé'],
    ['COMPLETED', 'Terminé'],
    ['CANCELLED', 'Annulé'],
    ['NO_SHOW', 'Absent']
  ])('%s → %s', (status, label) => {
    expect(appointmentStatusLabel(status)).toBe(label);
  });
});
