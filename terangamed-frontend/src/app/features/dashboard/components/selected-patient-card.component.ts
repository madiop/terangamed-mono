import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnChanges,
  SimpleChanges,
  computed,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { catchError, finalize, forkJoin, map, of } from 'rxjs';
import { format, parseISO } from 'date-fns';
import { fr } from 'date-fns/locale';
import { PatientApi } from '@api/patient.api';
import { MedicalRecordApi } from '@api/medical-record.api';
import { PatientDto } from '@api/models/patient.model';
import { ConsultationDto, MedicalRecordDto } from '@api/models/medical-record.model';
import { ageFromBirthDate } from '@shared/utils/date.utils';

/**
 * Carte "Dossier patient sélectionné" — affichée à droite du dashboard.
 *
 * <p>Charge en parallèle :
 * <ul>
 *   <li>{@code GET /api/patients/:id} — identité</li>
 *   <li>{@code GET /api/medical-records/by-patient/:id} — allergies, groupe sanguin</li>
 *   <li>{@code GET /api/consultations?patientId=:id&size=5} — historique récent</li>
 * </ul>
 *
 * <p>Si le patient n'a pas encore de dossier médical (404 sur le 2e endpoint),
 * on affiche quand même l'identité — c'est un cas valide pour un nouveau patient.
 */
@Component({
  selector: 'tm-selected-patient-card',
  standalone: true,
  imports: [CommonModule, MatTabsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './selected-patient-card.component.html',
  styleUrl: './selected-patient-card.component.scss'
})
export class SelectedPatientCardComponent implements OnChanges {
  @Input() patientId: number | null = null;

  /**
   * Mode "vue restreinte" — masque les onglets Historique / Prescriptions /
   * Documents et n'appelle pas medical-record-service. Utilisé en mode
   * RECEPTIONIST où le backend bloquerait ces endpoints (403).
   *
   * <p>Quand {@code true}, seules les informations d'identité du patient
   * (nom, contact, adresse, contact d'urgence) sont chargées et affichées.
   */
  @Input() restrictedView = false;

  private readonly patientApi = inject(PatientApi);
  private readonly medicalRecordApi = inject(MedicalRecordApi);
  private readonly router = inject(Router);

  readonly patient = signal<PatientDto | null>(null);
  readonly medicalRecord = signal<MedicalRecordDto | null>(null);
  readonly consultations = signal<readonly ConsultationDto[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly initials = computed(() => {
    const p = this.patient();
    if (!p) return '?';
    const f = p.firstName.charAt(0);
    const l = p.lastName.charAt(0);
    return (f + l).toUpperCase() || '?';
  });

  readonly age = computed(() => {
    const p = this.patient();
    return p ? ageFromBirthDate(p.birthDate) : null;
  });

  readonly latestConsultation = computed(() => {
    const list = this.consultations();
    return list.length > 0 ? list[0] : null;
  });

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['patientId']) {
      this.loadPatientData();
    }
  }

  private loadPatientData(): void {
    if (this.patientId === null) {
      this.patient.set(null);
      this.medicalRecord.set(null);
      this.consultations.set([]);
      this.error.set(null);
      return;
    }
    const id = this.patientId;
    this.loading.set(true);
    this.error.set(null);

    // Vue restreinte (RECEPTIONIST) : on charge UNIQUEMENT l'identité.
    // Pas d'appel medical-record-service (403 garanti côté backend).
    if (this.restrictedView) {
      this.medicalRecord.set(null);
      this.consultations.set([]);
      this.patientApi
        .findById(id)
        .pipe(finalize(() => this.loading.set(false)))
        .subscribe({
          next: (patient) => this.patient.set(patient),
          error: (err) => {
            this.error.set(this.translateError(err));
            this.patient.set(null);
          }
        });
      return;
    }

    // forkJoin nécessite des observables typés cohérents — on map chaque branche
    // vers son type final pour éviter les conflits de typage au catchError.
    const record$ = this.medicalRecordApi.findRecordByPatientId(id).pipe(
      // 404 légitime si patient nouveau sans dossier — on absorbe en null
      catchError(() => of<MedicalRecordDto | null>(null))
    );

    const consultations$ = this.medicalRecordApi
      .searchConsultations({ patientId: id }, { page: 0, size: 5, sort: 'consultationDate,desc' })
      .pipe(
        map((page) => page.content),
        catchError(() => of<readonly ConsultationDto[]>([]))
      );

    forkJoin({
      patient: this.patientApi.findById(id),
      record: record$,
      consultations: consultations$
    })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({ patient, record, consultations }) => {
          this.patient.set(patient);
          this.medicalRecord.set(record);
          this.consultations.set(consultations);
        },
        error: (err) => {
          this.error.set(this.translateError(err));
          this.patient.set(null);
        }
      });
  }

  formatDate(iso: string | null | undefined): string {
    if (!iso) return '—';
    try {
      return format(parseISO(iso), 'dd MMM yyyy', { locale: fr });
    } catch {
      return iso;
    }
  }

  /** Construit "ligne1, ville" en gérant proprement les valeurs absentes. */
  formatAddress(p: PatientDto): string {
    const parts = [p.addressLine1, p.city].filter((s): s is string => !!s && !!s.trim());
    return parts.length > 0 ? parts.join(', ') : '—';
  }

  formatEmergencyContact(p: PatientDto): string {
    if (!p.emergencyContactName) return '—';
    return p.emergencyContactPhone
      ? `${p.emergencyContactName} · ${p.emergencyContactPhone}`
      : p.emergencyContactName;
  }

  goToPatientDetail(): void {
    if (this.patientId !== null) {
      void this.router.navigate(['/patients', this.patientId]);
    }
  }

  goToNewConsultation(): void {
    if (this.patientId !== null) {
      void this.router.navigate(['/consultations/new'], {
        queryParams: { patientId: this.patientId }
      });
    }
  }

  private translateError(err: unknown): string {
    if (err && typeof err === 'object' && 'status' in err) {
      const status = (err as { status: number }).status;
      if (status === 404) return 'Patient introuvable';
      if (status === 403) return 'Accès refusé à ce dossier';
      if (status === 0) return 'Serveur injoignable';
    }
    return 'Erreur lors du chargement du dossier';
  }
}
