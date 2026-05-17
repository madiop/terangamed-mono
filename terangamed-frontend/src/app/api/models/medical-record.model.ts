// Modèles TS — miroir medical-record-service.

export type BloodType =
  | 'A_POS' | 'A_NEG'
  | 'B_POS' | 'B_NEG'
  | 'AB_POS' | 'AB_NEG'
  | 'O_POS' | 'O_NEG'
  | 'UNKNOWN';

export type AntecedentType =
  | 'ALLERGY'
  | 'MEDICAL_CONDITION'
  | 'SURGERY'
  | 'MEDICATION'
  | 'FAMILY';

export type MedicationRoute =
  | 'ORAL'
  | 'INJECTION'
  | 'TOPICAL'
  | 'INHALATION'
  | 'OPHTHALMIC'
  | 'NASAL'
  | 'RECTAL'
  | 'OTHER';

// ───────────── MedicalRecord ─────────────
export interface MedicalRecordDto {
  readonly id: number;
  readonly patientId: number;
  readonly bloodType?: BloodType | null;
  readonly allergiesSummary?: string | null;
  readonly notes?: string | null;
  readonly softDeleted: boolean;
  readonly deletedAt?: string | null;
  readonly deletedBy?: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly createdBy?: string | null;
  readonly updatedBy?: string | null;
  readonly version: number;
}

export interface CreateMedicalRecordRequest {
  patientId: number;
  bloodType?: BloodType;
  allergiesSummary?: string;
  notes?: string;
}

export interface UpdateMedicalRecordRequest {
  bloodType?: BloodType;
  allergiesSummary?: string;
  notes?: string;
}

// ───────────── Antécédent ─────────────
export interface AntecedentDto {
  readonly id: number;
  readonly medicalRecordId: number;
  readonly type: AntecedentType;
  readonly title: string;
  readonly description?: string | null;
  readonly onsetDate?: string | null;
  readonly active: boolean;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly createdBy?: string | null;
  readonly updatedBy?: string | null;
  readonly version: number;
}

export interface CreateAntecedentRequest {
  medicalRecordId: number;
  type: AntecedentType;
  title: string;
  description?: string;
  /** ISO date YYYY-MM-DD — passé ou présent. */
  onsetDate?: string;
  /** Défaut serveur : true. */
  active?: boolean;
}

/** Tout champ omis ou null = laissé inchangé côté serveur (partial update). */
export interface UpdateAntecedentRequest {
  type?: AntecedentType;
  title?: string;
  description?: string;
  onsetDate?: string;
  active?: boolean;
}

// ───────────── VitalSigns (JSONB) ─────────────
/** Signes vitaux structurés — POJO côté backend, miroir TS ici. */
export interface VitalSignsDto {
  weightKg?: number | null;
  heightCm?: number | null;
  temperatureCelsius?: number | null;
  heartRateBpm?: number | null;
  respiratoryRateBpm?: number | null;
  bloodPressureSystolic?: number | null;
  bloodPressureDiastolic?: number | null;
  oxygenSaturationPercent?: number | null;
  bloodGlucoseMgDl?: number | null;
  notes?: string | null;
  /** Calculé serveur-side. Read-only depuis le backend. */
  readonly bmi?: number | null;
}

// ───────────── Consultation ─────────────
export interface ConsultationDto {
  readonly id: number;
  readonly medicalRecordId: number;
  readonly doctorId: number;
  readonly appointmentId?: number | null;
  readonly consultationDate: string;
  readonly motif: string;
  readonly vitalSigns?: VitalSignsDto | null;
  readonly examenCliniqueNotes?: string | null;
  readonly diagnostic?: string | null;
  readonly observations?: string | null;
  readonly recommandations?: string | null;
  readonly nextAppointmentSuggested?: string | null;
  readonly signed: boolean;
  readonly signedAt?: string | null;
  readonly signedBy?: string | null;
  readonly softDeleted: boolean;
  readonly deletedAt?: string | null;
  readonly deletedBy?: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly createdBy?: string | null;
  readonly updatedBy?: string | null;
  readonly version: number;
}

export interface CreateConsultationRequest {
  medicalRecordId: number;
  appointmentId?: number;
  consultationDate: string;
  motif: string;
  vitalSigns?: VitalSignsDto;
  examenCliniqueNotes?: string;
  diagnostic?: string;
  observations?: string;
  recommandations?: string;
  nextAppointmentSuggested?: string;
}

export interface UpdateConsultationRequest {
  consultationDate?: string;
  motif?: string;
  vitalSigns?: VitalSignsDto;
  examenCliniqueNotes?: string;
  diagnostic?: string;
  observations?: string;
  recommandations?: string;
  nextAppointmentSuggested?: string;
}

export interface ConsultationSearchCriteria {
  patientId?: number;
  doctorId?: number;
  fromDate?: string;
  toDate?: string;
  signed?: boolean;
  keyword?: string;
}

// ───────────── Prescription ─────────────
export interface PrescriptionLineDto {
  readonly id: number;
  readonly prescriptionId: number;
  readonly medicationName: string;
  readonly dosage?: string | null;
  readonly frequency?: string | null;
  readonly duration?: string | null;
  readonly route?: MedicationRoute | null;
  readonly instructions?: string | null;
  readonly quantity?: number | null;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly version: number;
}

export interface PrescriptionDto {
  readonly id: number;
  readonly prescriptionNumber: string;
  readonly consultationId: number;
  readonly issuedAt: string;
  readonly validUntil?: string | null;
  readonly generalInstructions?: string | null;
  readonly lines: readonly PrescriptionLineDto[];
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly createdBy?: string | null;
  readonly updatedBy?: string | null;
  readonly version: number;
}

/** Création d'une ligne — utilisé inline lors du POST prescription ou en ajout via /lines. */
export interface CreatePrescriptionLineRequest {
  medicationName: string;
  dosage?: string;
  frequency?: string;
  duration?: string;
  route?: MedicationRoute;
  instructions?: string;
  /** Nombre de boîtes prescrites. */
  quantity?: number;
}

export interface UpdatePrescriptionLineRequest {
  medicationName?: string;
  dosage?: string;
  frequency?: string;
  duration?: string;
  route?: MedicationRoute;
  instructions?: string;
  quantity?: number;
}

/**
 * Création d'une ordonnance — au moins une ligne médicament obligatoire.
 * Le {@code consultationId} est passé dans le path, pas dans le body.
 */
export interface CreatePrescriptionRequest {
  /** ISO date — défaut serveur : J+3 mois. */
  validUntil?: string;
  generalInstructions?: string;
  lines: CreatePrescriptionLineRequest[];
}

export interface UpdatePrescriptionRequest {
  validUntil?: string;
  generalInstructions?: string;
}
