// Modèles TS — miroir des records backend doctor-service.

export type DoctorStatus = 'ACTIVE' | 'ON_LEAVE' | 'RETIRED';

export type Specialty =
  | 'GENERAL_MEDICINE'
  | 'CARDIOLOGY'
  | 'DERMATOLOGY'
  | 'PEDIATRICS'
  | 'GYNECOLOGY'
  | 'DENTISTRY'
  | 'OPHTHALMOLOGY'
  | 'PSYCHIATRY'
  | 'ORTHOPEDICS'
  | 'OTHER';

/** Devise — phase finance. */
export type Currency = 'XOF' | 'XAF' | 'EUR' | 'USD';

export interface DoctorDto {
  readonly id: number;
  readonly licenseNumber: string;
  readonly lastName: string;
  readonly firstName: string;
  readonly specialty: Specialty;
  readonly email?: string | null;
  readonly phone?: string | null;
  readonly officeAddress?: string | null;
  readonly yearsOfExperience?: number | null;
  readonly consultationFee?: number | null;
  readonly consultationFeeCurrency?: Currency | null;
  readonly bio?: string | null;
  readonly status: DoctorStatus;
  /** UUID Keycloak (claim sub) du compte lié, ou null si non lié. */
  readonly keycloakSubject?: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly createdBy?: string | null;
  readonly updatedBy?: string | null;
  readonly version: number;
}

export interface CreateDoctorRequest {
  lastName: string;
  firstName: string;
  specialty: Specialty;
  email?: string;
  phone?: string;
  officeAddress?: string;
  yearsOfExperience?: number;
  consultationFee?: number;
  consultationFeeCurrency?: Currency;
  bio?: string;
  /** UUID Keycloak du compte à lier au médecin (optionnel à la création). */
  keycloakSubject?: string;
}

export interface UpdateDoctorRequest {
  lastName?: string;
  firstName?: string;
  specialty?: Specialty;
  email?: string;
  phone?: string;
  officeAddress?: string;
  yearsOfExperience?: number;
  consultationFee?: number;
  consultationFeeCurrency?: Currency;
  bio?: string;
  status?: DoctorStatus;
  /** Liaison ou re-liaison à un compte Keycloak (partial-update). */
  keycloakSubject?: string;
}

export interface DoctorSearchCriteria {
  lastName?: string;
  firstName?: string;
  licenseNumber?: string;
  email?: string;
  specialty?: Specialty;
  status?: DoctorStatus;
  minYearsOfExperience?: number;
  maxConsultationFee?: number;
}
