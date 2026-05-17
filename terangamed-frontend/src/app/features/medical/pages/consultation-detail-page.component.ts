import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  computed,
  inject
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { format, parseISO } from 'date-fns';
import { fr } from 'date-fns/locale';
import { AuthService } from '@core/auth/auth.service';
import {
  ConsultationDto,
  PrescriptionDto,
  VitalSignsDto
} from '@api/models/medical-record.model';
import { MedicalRecordFacade } from '../medical-record.facade';
import {
  ConsultationSignDialogComponent,
  ConsultationSignDialogData,
  ConsultationSignDialogResult
} from '../components/consultation-sign-dialog.component';
import {
  PrescriptionDeleteDialogComponent,
  PrescriptionDeleteDialogData,
  PrescriptionDeleteDialogResult
} from '../components/prescription-delete-dialog.component';

/**
 * Page détail d'une consultation — `/consultations/:id`.
 *
 * <h3>Workflow état</h3>
 * <pre>
 *   DRAFT (signed=false) ──sign()──▶ SIGNED (signed=true, IMMUTABLE)
 *           │
 *           └─ softDelete() (admin uniquement)
 * </pre>
 *
 * <h3>Sections affichées</h3>
 * <ul>
 *   <li>Header : date, signature status, patient (via medicalRecord)</li>
 *   <li>Motif</li>
 *   <li>Vital signs (si renseignés)</li>
 *   <li>Examen clinique</li>
 *   <li>Diagnostic</li>
 *   <li>Observations</li>
 *   <li>Recommandations</li>
 *   <li>Prochain RDV suggéré</li>
 *   <li>Prescription liée (si existe)</li>
 * </ul>
 *
 * <h3>Actions selon état</h3>
 * DRAFT : Modifier · Signer · Supprimer (admin)<br>
 * SIGNED : lecture seule + bouton "Voir l'ordonnance" si prescription
 */
@Component({
  selector: 'tm-consultation-detail-page',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="consultation-detail-page">
      @if (facade.consultationState().loading) {
        <div class="loading-state" aria-busy="true">
          <span class="material-icons-round spin">progress_activity</span>
          <p>Chargement de la consultation…</p>
        </div>
      } @else if (facade.consultationState().error) {
        <div class="error-state">
          <span class="material-icons-round">error_outline</span>
          <h2>{{ facade.consultationState().error }}</h2>
          <button type="button" class="btn btn-outline" (click)="goBack()">
            Retour
          </button>
        </div>
      } @else {
        @if (facade.consultationState().consultation; as c) {
          <!-- Header -->
          <header class="detail-header">
            <button
              type="button"
              class="back-button"
              (click)="goBack()"
              aria-label="Retour"
            >
              <span class="material-icons-round">arrow_back</span>
            </button>
            <div class="header-content">
              <h1 class="consultation-date">
                Consultation du {{ formatDate(c.consultationDate) }}
              </h1>
              <div class="header-meta">
                @if (c.signed) {
                  <span class="badge badge-signed">
                    <span class="material-icons-round">verified</span>
                    Signée le {{ formatDateTime(c.signedAt) }}
                    @if (c.signedBy) { par {{ c.signedBy }} }
                  </span>
                } @else {
                  <span class="badge badge-draft">
                    <span class="material-icons-round">edit_note</span>
                    Brouillon
                  </span>
                }
                @if (c.appointmentId) {
                  <button type="button" class="link-btn" (click)="goToAppointment(c.appointmentId!)">
                    <span class="material-icons-round">event</span>
                    Voir le rendez-vous lié
                  </button>
                }
              </div>
            </div>

            <div class="header-actions">
              @if (canEdit(c)) {
                <button
                  type="button"
                  class="btn btn-outline"
                  (click)="goToEdit(c.id)"
                >
                  <span class="material-icons-round">edit</span>
                  Modifier
                </button>
                <button
                  type="button"
                  class="btn btn-primary"
                  [disabled]="facade.mutating()"
                  (click)="onSignClick(c)"
                >
                  <span class="material-icons-round">verified</span>
                  Signer
                </button>
              }
            </div>
          </header>

          @if (facade.mutation().error; as err) {
            <div class="error-banner" role="alert">
              <span class="material-icons-round">error_outline</span>
              <p>{{ err }}</p>
            </div>
          }

          <!-- Sections -->
          <section class="detail-sections">
            <article class="card detail-card">
              <h2 class="card-title">
                <span class="material-icons-round">description</span>
                Motif
              </h2>
              <p class="card-content">{{ c.motif }}</p>
            </article>

            @if (hasAnyVitalSign(c.vitalSigns)) {
              <article class="card detail-card">
                <h2 class="card-title">
                  <span class="material-icons-round">monitor_heart</span>
                  Signes vitaux
                </h2>
                <dl class="vitals-grid">
                  @if (c.vitalSigns?.weightKg != null) {
                    <dt>Poids</dt><dd>{{ c.vitalSigns!.weightKg }} kg</dd>
                  }
                  @if (c.vitalSigns?.heightCm != null) {
                    <dt>Taille</dt><dd>{{ c.vitalSigns!.heightCm }} cm</dd>
                  }
                  @if (c.vitalSigns?.bmi != null) {
                    <dt>IMC</dt><dd>{{ c.vitalSigns!.bmi }}</dd>
                  }
                  @if (c.vitalSigns?.temperatureCelsius != null) {
                    <dt>Température</dt><dd>{{ c.vitalSigns!.temperatureCelsius }} °C</dd>
                  }
                  @if (c.vitalSigns?.heartRateBpm != null) {
                    <dt>Fréquence cardiaque</dt><dd>{{ c.vitalSigns!.heartRateBpm }} bpm</dd>
                  }
                  @if (c.vitalSigns?.respiratoryRateBpm != null) {
                    <dt>Fréquence respiratoire</dt><dd>{{ c.vitalSigns!.respiratoryRateBpm }} /min</dd>
                  }
                  @if (c.vitalSigns?.bloodPressureSystolic != null) {
                    <dt>Tension</dt>
                    <dd>
                      {{ c.vitalSigns!.bloodPressureSystolic }}/{{ c.vitalSigns!.bloodPressureDiastolic }} mmHg
                    </dd>
                  }
                  @if (c.vitalSigns?.oxygenSaturationPercent != null) {
                    <dt>Saturation O₂</dt><dd>{{ c.vitalSigns!.oxygenSaturationPercent }} %</dd>
                  }
                  @if (c.vitalSigns?.bloodGlucoseMgDl != null) {
                    <dt>Glycémie</dt><dd>{{ c.vitalSigns!.bloodGlucoseMgDl }} mg/dL</dd>
                  }
                </dl>
                @if (c.vitalSigns?.notes) {
                  <p class="vitals-notes text-muted">{{ c.vitalSigns!.notes }}</p>
                }
              </article>
            }

            @if (c.examenCliniqueNotes) {
              <article class="card detail-card">
                <h2 class="card-title">
                  <span class="material-icons-round">stethoscope</span>
                  Examen clinique
                </h2>
                <p class="card-content multiline">{{ c.examenCliniqueNotes }}</p>
              </article>
            }

            @if (c.diagnostic) {
              <article class="card detail-card diagnostic-card">
                <h2 class="card-title">
                  <span class="material-icons-round">medical_information</span>
                  Diagnostic
                </h2>
                <p class="card-content multiline">{{ c.diagnostic }}</p>
              </article>
            }

            @if (c.observations) {
              <article class="card detail-card">
                <h2 class="card-title">
                  <span class="material-icons-round">visibility</span>
                  Observations
                </h2>
                <p class="card-content multiline">{{ c.observations }}</p>
              </article>
            }

            @if (c.recommandations) {
              <article class="card detail-card">
                <h2 class="card-title">
                  <span class="material-icons-round">lightbulb</span>
                  Recommandations
                </h2>
                <p class="card-content multiline">{{ c.recommandations }}</p>
              </article>
            }

            @if (c.nextAppointmentSuggested) {
              <article class="card detail-card">
                <h2 class="card-title">
                  <span class="material-icons-round">event_upcoming</span>
                  Prochain rendez-vous suggéré
                </h2>
                <p class="card-content">{{ formatDate(c.nextAppointmentSuggested) }}</p>
              </article>
            }

            <!-- Prescription liée -->
            <article class="card detail-card prescription-card">
              <header class="card-header-row">
                <h2 class="card-title">
                  <span class="material-icons-round">receipt_long</span>
                  Ordonnance
                </h2>
                <div class="card-actions">
                  @if (facade.consultationState().prescription; as p) {
                    <!-- Téléchargement PDF — visible pour tous les rôles autorisés
                         (DOCTOR, ADMIN, PATIENT), y compris sur consultation signée -->
                    <button
                      type="button"
                      class="btn btn-outline btn-sm"
                      [disabled]="facade.isDownloadingPdf(p.id)"
                      (click)="onDownloadPdf(p)"
                      [attr.aria-label]="'Télécharger l\\'ordonnance ' + p.prescriptionNumber + ' au format PDF'"
                    >
                      @if (facade.isDownloadingPdf(p.id)) {
                        <span class="material-icons-round spin">progress_activity</span>
                        Téléchargement…
                      } @else {
                        <span class="material-icons-round">picture_as_pdf</span>
                        Télécharger PDF
                      }
                    </button>
                  }
                  <!-- Actions d'édition — DRAFT + canEdit uniquement -->
                  @if (canEdit(c)) {
                    @if (facade.consultationState().prescription; as p) {
                      <button
                        type="button"
                        class="btn btn-outline btn-sm"
                        [disabled]="facade.mutating()"
                        (click)="goToEditPrescription(c.id)"
                      >
                        <span class="material-icons-round">edit</span>
                        Modifier
                      </button>
                      <button
                        type="button"
                        class="btn btn-outline btn-sm btn-danger"
                        [disabled]="facade.mutating()"
                        (click)="onDeletePrescription(p)"
                      >
                        <span class="material-icons-round">delete_outline</span>
                        Supprimer
                      </button>
                    } @else {
                      <button
                        type="button"
                        class="btn btn-primary btn-sm"
                        (click)="goToCreatePrescription(c.id)"
                      >
                        <span class="material-icons-round">add</span>
                        Créer l'ordonnance
                      </button>
                    }
                  }
                </div>
              </header>

              @if (facade.pdfDownload().error; as pdfError) {
                <div class="pdf-error" role="alert">
                  <span class="material-icons-round">error_outline</span>
                  {{ pdfError }}
                  <button
                    type="button"
                    class="btn-icon-text"
                    (click)="dismissPdfError()"
                    aria-label="Fermer le message d'erreur"
                  >
                    <span class="material-icons-round">close</span>
                  </button>
                </div>
              }

              @if (facade.consultationState().prescription; as p) {
                <div class="prescription-summary">
                  <p>
                    <strong>{{ p.prescriptionNumber }}</strong> —
                    {{ p.lines.length }} ligne(s) médicament
                  </p>
                  <ul class="prescription-lines">
                    @for (line of p.lines; track line.id) {
                      <li>
                        <strong>{{ line.medicationName }}</strong>
                        @if (line.dosage) { — {{ line.dosage }} }
                        @if (line.frequency) { · {{ line.frequency }} }
                        @if (line.duration) { · {{ line.duration }} }
                      </li>
                    }
                  </ul>
                  @if (p.validUntil) {
                    <p class="text-muted">Valide jusqu'au {{ formatDate(p.validUntil) }}</p>
                  }
                  @if (p.generalInstructions) {
                    <p class="prescription-instructions">{{ p.generalInstructions }}</p>
                  }
                </div>
              } @else {
                <p class="text-muted no-content">Aucune ordonnance pour cette consultation.</p>
              }
            </article>
          </section>

          <!-- Footer audit -->
          <footer class="detail-footer text-muted">
            <span>
              Créée le {{ formatDateTime(c.createdAt) }}
              @if (c.createdBy) { par {{ c.createdBy }} }
            </span>
            <span>·</span>
            <span>
              Modifiée le {{ formatDateTime(c.updatedAt) }}
              @if (c.updatedBy) { par {{ c.updatedBy }} }
            </span>
          </footer>
        }
      }
    </div>
  `,
  styles: [
    `
      .consultation-detail-page {
        display: flex;
        flex-direction: column;
        gap: 16px;
      }

      .loading-state,
      .error-state {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 12px;
        padding: 48px 24px;
        background: var(--color-surface);
        border-radius: var(--radius);
        box-shadow: var(--shadow);
      }
      .loading-state .material-icons-round,
      .error-state .material-icons-round {
        font-size: 40px;
        color: var(--color-text-muted);
      }
      .error-state .material-icons-round {
        color: #ef4444;
      }
      .spin {
        animation: tm-spin 0.9s linear infinite;
      }
      @keyframes tm-spin {
        from { transform: rotate(0deg); }
        to { transform: rotate(360deg); }
      }

      .detail-header {
        display: flex;
        align-items: flex-start;
        gap: 16px;
        background: var(--color-surface);
        padding: 20px 24px;
        border-radius: var(--radius);
        box-shadow: var(--shadow);
      }
      .back-button {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 40px;
        height: 40px;
        border-radius: 50%;
        background: transparent;
        border: 1px solid var(--color-border, #e5e7eb);
        cursor: pointer;
        color: var(--color-text);
        flex-shrink: 0;
      }
      .back-button:hover {
        background: rgba(0, 0, 0, 0.04);
      }
      .header-content {
        flex: 1;
        min-width: 0;
      }
      .consultation-date {
        font-size: 22px;
        font-weight: 700;
        margin: 0;
        text-transform: capitalize;
      }
      .header-meta {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 12px;
        margin-top: 8px;
      }
      .badge {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 4px 12px;
        border-radius: 14px;
        font-size: 13px;
        font-weight: 600;
      }
      .badge .material-icons-round {
        font-size: 16px;
      }
      .badge-signed {
        background: #d1fae5;
        color: #065f46;
      }
      .badge-draft {
        background: #fef3c7;
        color: #92400e;
      }
      .link-btn {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        background: none;
        border: none;
        color: var(--color-primary, #2963b0);
        cursor: pointer;
        font-size: 13px;
        padding: 0;
      }
      .link-btn:hover {
        text-decoration: underline;
      }
      .header-actions {
        display: flex;
        gap: 8px;
        flex-shrink: 0;
      }

      .error-banner {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 12px 16px;
        background: #fee2e2;
        border-left: 4px solid #ef4444;
        border-radius: var(--radius);
        color: #991b1b;
      }

      .detail-sections {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 16px;
        @media (max-width: 900px) {
          grid-template-columns: 1fr;
        }
      }
      .detail-card {
        padding: 20px;
      }
      .diagnostic-card {
        grid-column: span 2;
        background: linear-gradient(0deg, rgba(41, 99, 176, 0.04), transparent);
        @media (max-width: 900px) {
          grid-column: span 1;
        }
      }
      .prescription-card {
        grid-column: span 2;
        @media (max-width: 900px) {
          grid-column: span 1;
        }
      }
      .pdf-error {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 10px 12px;
        margin: 8px 0 12px;
        border-radius: var(--radius);
        background: rgba(220, 53, 69, 0.08);
        color: var(--color-danger, #b02a37);
        font-size: 13px;
      }
      .pdf-error .material-icons-round {
        font-size: 18px;
      }
      .pdf-error .btn-icon-text {
        margin-left: auto;
        display: inline-flex;
        align-items: center;
        background: transparent;
        border: none;
        cursor: pointer;
        color: inherit;
        padding: 4px;
        border-radius: 4px;
      }
      .pdf-error .btn-icon-text:hover {
        background: rgba(0, 0, 0, 0.05);
      }
      .card-title {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 15px;
        font-weight: 600;
        margin: 0 0 12px;
        color: var(--color-text);
      }
      .card-title .material-icons-round {
        font-size: 20px;
        color: var(--color-primary, #2963b0);
      }
      .card-header-row {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        gap: 12px;
        margin-bottom: 4px;
      }
      .card-header-row .card-title {
        margin: 0;
      }
      .card-actions {
        display: flex;
        gap: 8px;
        flex-wrap: wrap;
      }
      .btn-sm {
        padding: 4px 12px;
        font-size: 13px;
        height: 32px;
        display: inline-flex;
        align-items: center;
        gap: 4px;
      }
      .btn-sm .material-icons-round {
        font-size: 16px;
      }
      .btn-danger {
        color: #b91c1c;
        border-color: rgba(185, 28, 28, 0.4);
      }
      .btn-danger:hover:not(:disabled) {
        background: #fef2f2;
        border-color: #b91c1c;
      }
      .card-content {
        margin: 0;
        font-size: 14px;
        line-height: 1.5;
      }
      .multiline {
        white-space: pre-wrap;
        word-break: break-word;
      }

      .vitals-grid {
        display: grid;
        grid-template-columns: max-content 1fr max-content 1fr;
        gap: 8px 16px;
        margin: 0;
        @media (max-width: 700px) {
          grid-template-columns: max-content 1fr;
        }
      }
      .vitals-grid dt {
        font-size: 13px;
        color: var(--color-text-muted);
        font-weight: 500;
      }
      .vitals-grid dd {
        margin: 0;
        font-size: 14px;
        font-weight: 500;
      }
      .vitals-notes {
        margin: 12px 0 0;
        font-size: 13px;
        font-style: italic;
      }

      .prescription-summary p {
        margin: 4px 0;
      }
      .prescription-lines {
        margin: 8px 0;
        padding-left: 24px;
      }
      .prescription-lines li {
        margin: 4px 0;
        font-size: 14px;
      }
      .prescription-instructions {
        margin-top: 8px;
        font-style: italic;
      }
      .text-muted {
        color: var(--color-text-muted);
      }
      .no-content {
        font-style: italic;
      }

      .detail-footer {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        font-size: 12px;
        padding: 8px 4px;
      }
    `
  ]
})
export class ConsultationDetailPageComponent implements OnInit, OnDestroy {
  protected readonly facade = inject(MedicalRecordFacade);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    const id = idParam ? Number(idParam) : NaN;
    if (Number.isNaN(id) || id <= 0) {
      void this.router.navigate(['/']);
      return;
    }
    this.facade.loadConsultationDetail(id);
  }

  ngOnDestroy(): void {
    this.facade.clearConsultation();
  }

  protected canEdit(c: ConsultationDto): boolean {
    return !c.signed && this.auth.hasAnyRole(['ADMIN', 'DOCTOR']);
  }

  protected onSignClick(c: ConsultationDto): void {
    this.dialog
      .open<
        ConsultationSignDialogComponent,
        ConsultationSignDialogData,
        ConsultationSignDialogResult
      >(ConsultationSignDialogComponent, {
        data: { consultation: c },
        width: '480px'
      });
  }

  protected goToEdit(id: number): void {
    void this.router.navigate(['/consultations', id, 'edit']);
  }

  // ─── Prescription — actions navigation + dialog suppression ───

  /**
   * Navigue vers la page d'édition prescription en mode CREATE.
   * Le composant cible ouvrira un form vierge car aucune prescription n'est liée.
   */
  protected goToCreatePrescription(consultationId: number): void {
    void this.router.navigate(['/consultations', consultationId, 'prescription']);
  }

  /**
   * Navigue vers la page d'édition prescription en mode EDIT.
   * Le composant cible détectera la prescription existante et pré-remplira le form.
   */
  protected goToEditPrescription(consultationId: number): void {
    void this.router.navigate(['/consultations', consultationId, 'prescription']);
  }

  /**
   * Déclenche le téléchargement PDF d'une ordonnance.
   * La facade gère le state {@code pdfDownload} (spinner localisé + message
   * d'erreur). L'observable est subscribe ici car le composant n'a besoin
   * d'aucun traitement post-succès (le download navigateur est déjà déclenché
   * dans le tap() de la facade).
   */
  protected onDownloadPdf(p: PrescriptionDto): void {
    this.facade.downloadPrescriptionPdf(p).subscribe({
      error: () => {
        /* L'erreur est déjà exposée dans facade.pdfDownload().error → la vue
           l'affiche via le bloc pdf-error ci-dessus. Le subscribe vide évite
           juste l'unhandled error rxjs. */
      }
    });
  }

  protected dismissPdfError(): void {
    this.facade.clearPdfDownloadError();
  }

  /**
   * Ouvre le dialog de confirmation de suppression d'ordonnance. Le dialog
   * gère lui-même l'appel facade.deletePrescription() ; à la fermeture sur
   * succès, la facade a déjà retiré la prescription du state local — la vue
   * se rafraîchit automatiquement.
   */
  protected onDeletePrescription(p: PrescriptionDto): void {
    this.dialog.open<
      PrescriptionDeleteDialogComponent,
      PrescriptionDeleteDialogData,
      PrescriptionDeleteDialogResult
    >(PrescriptionDeleteDialogComponent, {
      data: { prescription: p },
      width: '520px',
      maxWidth: '95vw',
      autoFocus: false,
      restoreFocus: true,
      disableClose: false
    });
  }

  protected goToAppointment(id: number): void {
    void this.router.navigate(['/appointments', id]);
  }

  protected goBack(): void {
    // Si on a un patient ID via le record, on peut revenir au dossier
    const c = this.facade.consultationState().consultation;
    if (c) {
      // Pour revenir au dossier médical, il faudrait connaître le patientId
      // Le DTO consultation n'a que medicalRecordId — on doit faire un round-trip
      // ou stocker le patientId dans la query string. Pour V1, retour à la liste
      // patients (le user peut re-cliquer).
    }
    void this.router.navigate(['/patients']);
  }

  // ─── Helpers d'affichage ───

  protected hasAnyVitalSign(v: VitalSignsDto | null | undefined): boolean {
    if (!v) return false;
    return (
      v.weightKg != null ||
      v.heightCm != null ||
      v.temperatureCelsius != null ||
      v.heartRateBpm != null ||
      v.respiratoryRateBpm != null ||
      v.bloodPressureSystolic != null ||
      v.bloodPressureDiastolic != null ||
      v.oxygenSaturationPercent != null ||
      v.bloodGlucoseMgDl != null ||
      !!v.notes
    );
  }

  protected formatDate(iso: string | null | undefined): string {
    if (!iso) return '—';
    try {
      return format(parseISO(iso), 'EEEE d MMMM yyyy', { locale: fr });
    } catch {
      return iso;
    }
  }

  protected formatDateTime(iso: string | null | undefined): string {
    if (!iso) return '—';
    try {
      return format(parseISO(iso), "dd MMM yyyy 'à' HH:mm", { locale: fr });
    } catch {
      return iso;
    }
  }
}
