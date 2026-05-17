// Modèles TS — miroir des records backend appointment-service.

export type AppointmentStatus = 'PLANNED' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';

export interface AppointmentDto {
  readonly id: number;
  readonly patientId: number;
  readonly doctorId: number;
  /** Snapshot du nom du patient au moment du RDV (eventual consistency). */
  readonly patientNameSnapshot: string;
  readonly doctorNameSnapshot: string;
  /** ISO timestamp UTC. */
  readonly startTime: string;
  readonly endTime: string;
  readonly durationMinutes: number;
  readonly reason?: string | null;
  readonly notes?: string | null;
  readonly status: AppointmentStatus;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly createdBy?: string | null;
  readonly updatedBy?: string | null;
  readonly version: number;
}

export interface CreateAppointmentRequest {
  patientId: number;
  doctorId: number;
  startTime: string;
  durationMinutes: number;
  reason?: string;
  notes?: string;
}

export interface UpdateAppointmentRequest {
  startTime?: string;
  durationMinutes?: number;
  reason?: string;
  notes?: string;
}

export interface AppointmentSearchCriteria {
  patientId?: number;
  doctorId?: number;
  status?: AppointmentStatus;
  /** ISO date — borne basse de startTime. */
  fromDate?: string;
  /** ISO date — borne haute de startTime. */
  toDate?: string;
}
