// Modèles TS — miroir des records backend patient-service.

export type Civility = 'M' | 'MME' | 'MLLE' | 'DR' | 'AUTRE';
export type Gender = 'MALE' | 'FEMALE';
export type PatientStatus = 'ACTIVE' | 'INACTIVE' | 'ARCHIVED';

export type BloodGroup =
  | 'A_POS' | 'A_NEG'
  | 'B_POS' | 'B_NEG'
  | 'AB_POS' | 'AB_NEG'
  | 'O_POS' | 'O_NEG'
  | 'UNKNOWN';

/**
 * Représentation complète d'un patient (réponse GET).
 * Miroir de {@code PatientDto.java} (records Java).
 */
export interface PatientDto {
  readonly id: number;
  readonly medicalRecordNumber: string;
  readonly civility: Civility;
  readonly lastName: string;
  readonly firstName: string;
  readonly birthDate: string; // ISO date (LocalDate sérialisé)
  readonly gender: Gender;
  readonly phone?: string | null;
  readonly email?: string | null;
  readonly addressLine1?: string | null;
  readonly addressLine2?: string | null;
  readonly postalCode?: string | null;
  readonly city?: string | null;
  readonly country?: string | null;
  readonly bloodGroup?: BloodGroup | null;
  readonly allergies?: string | null;
  readonly emergencyContactName?: string | null;
  readonly emergencyContactPhone?: string | null;
  readonly status: PatientStatus;
  readonly createdAt: string; // ISO timestamp
  readonly updatedAt: string;
  readonly createdBy?: string | null;
  readonly updatedBy?: string | null;
  readonly version: number;
}

export interface CreatePatientRequest {
  civility: Civility;
  lastName: string;
  firstName: string;
  birthDate: string;
  gender: Gender;
  phone?: string;
  email?: string;
  addressLine1?: string;
  addressLine2?: string;
  postalCode?: string;
  city?: string;
  country?: string;
  bloodGroup?: BloodGroup;
  allergies?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
}

/** Tout champ omis ou null = laissé inchangé côté serveur (partial update). */
export interface UpdatePatientRequest {
  civility?: Civility;
  lastName?: string;
  firstName?: string;
  birthDate?: string;
  gender?: Gender;
  phone?: string;
  email?: string;
  addressLine1?: string;
  addressLine2?: string;
  postalCode?: string;
  city?: string;
  country?: string;
  bloodGroup?: BloodGroup;
  allergies?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  status?: PatientStatus;
}

export interface PatientSearchCriteria {
  /** Recherche LIKE %x% case-insensitive sur lastName. */
  lastName?: string;
  firstName?: string;
  medicalRecordNumber?: string;
  email?: string;
  phone?: string;
  city?: string;
  status?: PatientStatus;
  gender?: Gender;
  bloodGroup?: BloodGroup;
}
