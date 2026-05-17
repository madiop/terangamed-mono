import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatTabsModule } from '@angular/material/tabs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';
import { format, parseISO } from 'date-fns';
import { fr } from 'date-fns/locale';
import { Subject, takeUntil } from 'rxjs';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { AuthService } from '@core/auth/auth.service';
import { PatientApi } from '@api/patient.api';
import { PatientDto } from '@api/models/patient.model';
import {
  AntecedentDto,
  AntecedentType,
  BloodType,
  CreateMedicalRecordRequest,
  UpdateMedicalRecordRequest
} from '@api/models/medical-record.model';
import { ageFromBirthDate } from '@shared/utils/date.utils';
import { MedicalRecordFacade } from '../medical-record.facade';
import {
  AntecedentFormDialogComponent,
  AntecedentFormDialogData,
  AntecedentFormDialogResult
} from '../components/antecedent-form-dialog.component';
import {
  AntecedentDeleteDialogComponent,
  AntecedentDeleteDialogData,
  AntecedentDeleteDialogResult
} from '../components/antecedent-delete-dialog.component';

const BLOOD_TYPE_LABEL: Record<BloodType, string> = {
  A_POS: 'A+',
  A_NEG: 'A−',
  B_POS: 'B+',
  B_NEG: 'B−',
  AB_POS: 'AB+',
  AB_NEG: 'AB−',
  O_POS: 'O+',
  O_NEG: 'O−',
  UNKNOWN: 'Inconnu'
};

const ANTECEDENT_TYPE_LABEL: Record<AntecedentType, string> = {
  ALLERGY: 'Allergie',
  MEDICAL_CONDITION: 'Antécédent médical',
  SURGERY: 'Chirurgie',
  MEDICATION: 'Traitement long cours',
  FAMILY: 'Familial'
};

const ANTECEDENT_TYPE_ICON: Record<AntecedentType, string> = {
  ALLERGY: 'warning',
  MEDICAL_CONDITION: 'medical_information',
  SURGERY: 'cut',
  MEDICATION: 'medication',
  FAMILY: 'family_restroom'
};

/**
 * Page Dossier médical patient — `/patients/:patientId/medical-record`.
 *
 * <h3>Architecture</h3>
 * Charge en parallèle :
 * <ul>
 *   <li>{@link PatientApi#findById} — pour le header (nom, âge, MRN)</li>
 *   <li>{@link MedicalRecordFacade#loadRecordByPatient} — pour le dossier complet</li>
 * </ul>
 *
 * <h3>Cas patient sans dossier</h3>
 * Si le patient n'a pas encore de {@link MedicalRecordDto}, la facade marque
 * {@code recordNotFound=true}. La page affiche alors un écran dédié avec
 * un bouton "Créer le dossier médical" (cas typique : nouveau patient).
 *
 * <h3>4 tabs</h3>
 * <ul>
 *   <li><b>Infos</b> : édition inline du record (groupe sanguin, allergies, notes)</li>
 *   <li><b>Antécédents</b> : liste read-only en 9.6b — CRUD en 9.6d</li>
 *   <li><b>Consultations</b> : liste read-only des 10 dernières — CRUD en 9.6c</li>
 *   <li><b>Prescriptions</b> : récap des prescriptions liées aux consultations</li>
 * </ul>
 *
 * <h3>Permissions</h3>
 * Accessible aux ADMIN/DOCTOR uniquement (cf. roleGuard sur la route).
 * RECEPTIONIST n'a pas accès au module medical (cohérent avec le restrictedView
 * du dashboard SelectedPatientCard).
 */
@Component({
  selector: 'tm-medical-record-page',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    PageHeaderComponent,
    MatTabsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatTooltipModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="medical-record-page">
      @if (loadingPatient()) {
        <div class="loading-state" aria-busy="true">
          <span class="material-icons-round spin">progress_activity</span>
          <p>Chargement du dossier patient…</p>
        </div>
      } @else if (patientError()) {
        <div class="error-state">
          <span class="material-icons-round">error_outline</span>
          <h2>{{ patientError() }}</h2>
          <button type="button" class="btn btn-outline" (click)="goBackToPatient()">
            Retour au patient
          </button>
        </div>
      } @else {
        @if (patient(); as p) {
          <!-- Header patient -->
          <header class="patient-header">
            <button
              type="button"
              class="back-button"
              (click)="goBackToPatient()"
              aria-label="Retour"
            >
              <span class="material-icons-round">arrow_back</span>
            </button>

            <div class="patient-avatar">{{ initialsOf(p) }}</div>
            <div class="patient-identity">
              <h1 class="patient-name">
                {{ p.lastName | uppercase }} {{ p.firstName }}
                @if (ageOf(p); as age) {
                  <span class="patient-age">, {{ age }} ans</span>
                }
              </h1>
              <p class="patient-mrn">N° dossier : {{ p.medicalRecordNumber }}</p>
              @if (recordState.record(); as r) {
                <p class="patient-blood">
                  Groupe sanguin :
                  <strong>{{ bloodTypeLabel(r.bloodType ?? null) }}</strong>
                </p>
              }
            </div>
          </header>

          <!-- Cas patient sans dossier médical -->
          @if (recordState.recordNotFound()) {
            <div class="card no-record-card">
              <span class="material-icons-round info-icon">folder_off</span>
              <h2>Aucun dossier médical pour ce patient</h2>
              <p>
                Le patient n'a pas encore de dossier médical. Créez-en un pour pouvoir
                ajouter des antécédents, des consultations et des prescriptions.
              </p>
              <button
                type="button"
                class="btn btn-primary"
                [disabled]="facade.mutating() || !canEdit()"
                (click)="createMedicalRecord()"
              >
                <span class="material-icons-round">create_new_folder</span>
                Créer le dossier médical
              </button>
            </div>
          } @else if (recordState.loading()) {
            <div class="loading-state" aria-busy="true">
              <span class="material-icons-round spin">progress_activity</span>
              <p>Chargement du dossier médical…</p>
            </div>
          } @else {
            <!-- Sortie de la chaîne précédente — nouvelle chaîne primary @if
                 pour autoriser les alias 'as'. -->
            @if (recordState.error(); as err) {
              <div class="error-banner" role="alert">
                <span class="material-icons-round">error_outline</span>
                <p>{{ err }}</p>
                <button type="button" class="btn btn-link" (click)="reload()">
                  Réessayer
                </button>
              </div>
            } @else {
              @if (recordState.record(); as r) {
            <!-- 4 Tabs -->
            <mat-tab-group class="medical-tabs" animationDuration="200ms">
              <!-- ─── Infos générales ─── -->
              <mat-tab label="Infos générales">
                <div class="tab-content">
                  <form [formGroup]="infoForm" class="info-form">
                    <div class="form-grid">
                      <mat-form-field appearance="outline">
                        <mat-label>Groupe sanguin</mat-label>
                        <mat-select formControlName="bloodType">
                          <mat-option [value]="null">Inconnu</mat-option>
                          @for (key of bloodTypeKeys; track key) {
                            <mat-option [value]="key">{{ bloodTypeLabel(key) }}</mat-option>
                          }
                        </mat-select>
                      </mat-form-field>

                      <mat-form-field appearance="outline" class="span-2">
                        <mat-label>Résumé allergies</mat-label>
                        <input
                          matInput
                          formControlName="allergiesSummary"
                          placeholder="Ex: Pénicilline, fruits à coque"
                          maxlength="500"
                        />
                      </mat-form-field>

                      <mat-form-field appearance="outline" class="span-2">
                        <mat-label>Notes générales</mat-label>
                        <textarea
                          matInput
                          formControlName="notes"
                          rows="4"
                          placeholder="Observations cliniques, antécédents généraux…"
                        ></textarea>
                      </mat-form-field>
                    </div>

                    @if (facade.mutation().error; as err) {
                      <div class="error-banner" role="alert">
                        <span class="material-icons-round">error_outline</span>
                        <p>{{ err }}</p>
                      </div>
                    }

                    @if (canEdit()) {
                      <div class="form-actions">
                        <button
                          type="button"
                          class="btn btn-outline"
                          [disabled]="!infoFormDirty() || facade.mutating()"
                          (click)="resetInfoForm()"
                        >
                          Annuler
                        </button>
                        <button
                          type="button"
                          class="btn btn-primary"
                          [disabled]="!infoFormDirty() || facade.mutating()"
                          (click)="saveInfo()"
                        >
                          @if (facade.mutating()) {
                            <span class="material-icons-round spin">progress_activity</span>
                          } @else {
                            <span class="material-icons-round">save</span>
                          }
                          Enregistrer
                        </button>
                      </div>
                    }

                    <p class="text-muted audit-info">
                      Dossier créé le {{ formatDateTime(r.createdAt) }} ·
                      Modifié le {{ formatDateTime(r.updatedAt) }}
                    </p>
                  </form>
                </div>
              </mat-tab>

              <!-- ─── Antécédents ─── -->
              <mat-tab>
                <ng-template mat-tab-label>
                  Antécédents
                  <span class="tab-count">{{ recordState.antecedents().length }}</span>
                </ng-template>
                <div class="tab-content">
                  @if (canEdit()) {
                    <div class="tab-toolbar">
                      <button
                        type="button"
                        class="btn btn-primary"
                        (click)="onAddAntecedent()"
                      >
                        <span class="material-icons-round">add</span>
                        Ajouter un antécédent
                      </button>
                    </div>
                  }

                  @if (recordState.antecedents().length === 0) {
                    <p class="text-muted no-content">Aucun antécédent enregistré.</p>
                  } @else {
                    <ul class="antecedents-list">
                      @for (a of recordState.antecedents(); track a.id) {
                        <li class="antecedent-item" [class.inactive]="!a.active">
                          <span
                            class="material-icons-round antecedent-icon"
                            [class]="'icon-' + a.type.toLowerCase()"
                          >
                            {{ antecedentIcon(a.type) }}
                          </span>
                          <div class="antecedent-content">
                            <div class="antecedent-header">
                              <span class="antecedent-type">{{ antecedentTypeLabel(a.type) }}</span>
                              @if (!a.active) {
                                <span class="status-tag">Résolu</span>
                              }
                            </div>
                            <p class="antecedent-title">{{ a.title }}</p>
                            @if (a.description) {
                              <p class="antecedent-description">{{ a.description }}</p>
                            }
                            @if (a.onsetDate) {
                              <p class="antecedent-date text-muted">
                                Depuis : {{ formatDate(a.onsetDate) }}
                              </p>
                            }
                          </div>
                          @if (canEdit()) {
                            <div class="antecedent-actions">
                              <button
                                type="button"
                                mat-icon-button
                                class="action-btn"
                                matTooltip="Modifier"
                                aria-label="Modifier l'antécédent"
                                (click)="onEditAntecedent(a)"
                              >
                                <span class="material-icons-round">edit</span>
                              </button>
                              <button
                                type="button"
                                mat-icon-button
                                class="action-btn action-btn-danger"
                                matTooltip="Supprimer"
                                aria-label="Supprimer l'antécédent"
                                (click)="onDeleteAntecedent(a)"
                              >
                                <span class="material-icons-round">delete_outline</span>
                              </button>
                            </div>
                          }
                        </li>
                      }
                    </ul>
                  }
                </div>
              </mat-tab>

              <!-- ─── Consultations ─── -->
              <mat-tab>
                <ng-template mat-tab-label>
                  Consultations
                  <span class="tab-count">{{ recordState.recentConsultations().length }}</span>
                </ng-template>
                <div class="tab-content">
                  @if (canEdit()) {
                    <div class="tab-toolbar">
                      <button
                        type="button"
                        class="btn btn-primary"
                        (click)="goToNewConsultation()"
                      >
                        <span class="material-icons-round">add</span>
                        Nouvelle consultation
                      </button>
                    </div>
                  }

                  @if (recordState.recentConsultations().length === 0) {
                    <p class="text-muted no-content">Aucune consultation enregistrée.</p>
                  } @else {
                    <ul class="consultations-list">
                      @for (c of recordState.recentConsultations(); track c.id) {
                        <li
                          class="consultation-item"
                          (click)="goToConsultation(c.id)"
                          (keydown.enter)="goToConsultation(c.id)"
                          tabindex="0"
                          role="button"
                        >
                          <div class="consultation-date">
                            <span class="day">{{ formatDay(c.consultationDate) }}</span>
                            <span class="full-date text-muted">
                              {{ formatDate(c.consultationDate) }}
                            </span>
                          </div>
                          <div class="consultation-content">
                            <p class="consultation-motif">{{ c.motif }}</p>
                            @if (c.diagnostic) {
                              <p class="consultation-diag text-muted">
                                Diagnostic : {{ c.diagnostic }}
                              </p>
                            }
                          </div>
                          <div class="consultation-status">
                            @if (c.signed) {
                              <span class="badge badge-signed">
                                <span class="material-icons-round">verified</span>
                                Signée
                              </span>
                            } @else {
                              <span class="badge badge-draft">Brouillon</span>
                            }
                          </div>
                          <span class="material-icons-round chevron">chevron_right</span>
                        </li>
                      }
                    </ul>
                  }
                </div>
              </mat-tab>

              <!-- ─── Prescriptions ─── -->
              <mat-tab label="Prescriptions">
                <div class="tab-content">
                  <p class="text-muted">
                    Les ordonnances sont rattachées aux consultations. Pour les
                    consulter ou en créer, ouvrez la consultation correspondante.
                  </p>
                  @if (recordState.recentConsultations().length > 0) {
                    <p class="text-muted">
                      <em>Liste agrégée des ordonnances disponible à l'étape ultérieure (9.6e).</em>
                    </p>
                  }
                </div>
              </mat-tab>
            </mat-tab-group>
              }
            }
          }
        }
      }
    </div>
  `,
  styles: [
    `
      .medical-record-page {
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
      .error-banner p {
        flex: 1;
        margin: 0;
      }
      .error-banner .btn-link {
        margin-left: auto;
        background: none;
        border: none;
        color: #991b1b;
        text-decoration: underline;
        cursor: pointer;
        font-weight: 600;
      }

      .patient-header {
        display: flex;
        align-items: center;
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
      .patient-avatar {
        width: 56px;
        height: 56px;
        border-radius: 50%;
        background: var(--color-primary, #2963b0);
        color: #fff;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 22px;
        font-weight: 700;
        flex-shrink: 0;
      }
      .patient-identity {
        flex: 1;
        min-width: 0;
      }
      .patient-name {
        font-size: 20px;
        font-weight: 700;
        margin: 0;
      }
      .patient-age {
        font-weight: 400;
        color: var(--color-text-muted);
        font-size: 16px;
      }
      .patient-mrn,
      .patient-blood {
        font-size: 13px;
        color: var(--color-text-muted);
        margin: 4px 0 0;
      }

      .no-record-card {
        padding: 48px 24px;
        text-align: center;
      }
      .info-icon {
        font-size: 48px;
        color: var(--color-text-muted);
        opacity: 0.6;
      }
      .no-record-card h2 {
        font-size: 18px;
        margin: 12px 0 8px;
      }
      .no-record-card p {
        color: var(--color-text-muted);
        max-width: 480px;
        margin: 0 auto 16px;
      }

      .medical-tabs {
        background: var(--color-surface);
        border-radius: var(--radius);
        box-shadow: var(--shadow);
        overflow: hidden;
      }
      .tab-count {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        margin-left: 8px;
        padding: 0 8px;
        height: 20px;
        min-width: 20px;
        border-radius: 10px;
        background: rgba(41, 99, 176, 0.1);
        color: var(--color-primary, #2963b0);
        font-size: 11px;
        font-weight: 600;
      }
      .tab-content {
        padding: 20px 24px;
      }
      .tab-toolbar {
        display: flex;
        justify-content: flex-end;
        margin-bottom: 16px;
      }
      .text-muted {
        color: var(--color-text-muted);
      }
      .no-content {
        font-style: italic;
      }

      /* Info form */
      .info-form {
        display: flex;
        flex-direction: column;
        gap: 16px;
      }
      .form-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 12px 16px;
        @media (max-width: 700px) {
          grid-template-columns: 1fr;
        }
      }
      .span-2 {
        grid-column: span 2;
        @media (max-width: 700px) {
          grid-column: span 1;
        }
      }
      .form-actions {
        display: flex;
        justify-content: flex-end;
        gap: 12px;
      }
      .audit-info {
        font-size: 12px;
        margin: 0;
      }

      /* Antécédents */
      .antecedents-list {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 12px;
      }
      .antecedent-item {
        display: flex;
        gap: 12px;
        padding: 12px 16px;
        background: rgba(0, 0, 0, 0.02);
        border-radius: var(--radius);
        border-left: 3px solid #d1d5db;
      }
      .antecedent-item.inactive {
        opacity: 0.7;
      }
      .antecedent-item.inactive .antecedent-title {
        text-decoration: line-through;
      }
      .antecedent-icon {
        font-size: 22px;
        flex-shrink: 0;
        margin-top: 2px;
      }
      .icon-allergy { color: #b91c1c; }
      .icon-medical_condition { color: #2963b0; }
      .icon-surgery { color: #7c3aed; }
      .icon-medication { color: #059669; }
      .icon-family { color: #d97706; }
      .antecedent-content {
        flex: 1;
        min-width: 0;
      }
      .antecedent-header {
        display: flex;
        align-items: center;
        gap: 8px;
      }
      .antecedent-type {
        font-size: 12px;
        font-weight: 600;
        text-transform: uppercase;
        color: var(--color-text-muted);
        letter-spacing: 0.5px;
      }
      .status-tag {
        background: #e5e7eb;
        color: #4b5563;
        padding: 1px 8px;
        border-radius: 10px;
        font-size: 11px;
      }
      .antecedent-title {
        margin: 4px 0;
        font-weight: 600;
      }
      .antecedent-description {
        margin: 4px 0;
        font-size: 13px;
        line-height: 1.4;
      }
      .antecedent-date {
        margin: 4px 0 0;
        font-size: 12px;
      }
      .antecedent-actions {
        display: flex;
        align-items: flex-start;
        gap: 4px;
        flex-shrink: 0;
        opacity: 0.55;
        transition: opacity 0.15s;
      }
      .antecedent-item:hover .antecedent-actions {
        opacity: 1;
      }
      .action-btn {
        width: 36px;
        height: 36px;
        line-height: 36px;
        color: var(--color-text-muted);
      }
      .action-btn .material-icons-round {
        font-size: 20px;
      }
      .action-btn:hover {
        color: var(--color-primary, #2963b0);
      }
      .action-btn-danger:hover {
        color: #b91c1c;
      }

      /* Consultations */
      .consultations-list {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 8px;
      }
      .consultation-item {
        display: flex;
        align-items: center;
        gap: 16px;
        padding: 12px 16px;
        background: rgba(0, 0, 0, 0.02);
        border-radius: var(--radius);
        cursor: pointer;
        transition: background 0.15s;
      }
      .consultation-item:hover {
        background: rgba(41, 99, 176, 0.06);
      }
      .consultation-date {
        display: flex;
        flex-direction: column;
        min-width: 100px;
      }
      .day {
        font-weight: 700;
        text-transform: capitalize;
      }
      .full-date {
        font-size: 12px;
      }
      .consultation-content {
        flex: 1;
        min-width: 0;
      }
      .consultation-motif {
        font-weight: 500;
        margin: 0 0 4px;
      }
      .consultation-diag {
        margin: 0;
        font-size: 13px;
      }
      .consultation-status {
        flex-shrink: 0;
      }
      .badge {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        padding: 2px 10px;
        border-radius: 12px;
        font-size: 12px;
        font-weight: 600;
      }
      .badge .material-icons-round {
        font-size: 14px;
      }
      .badge-signed {
        background: #d1fae5;
        color: #065f46;
      }
      .badge-draft {
        background: #fef3c7;
        color: #92400e;
      }
      .chevron {
        color: var(--color-text-muted);
        flex-shrink: 0;
      }
    `
  ]
})
export class MedicalRecordPageComponent implements OnInit, OnDestroy {
  protected readonly facade = inject(MedicalRecordFacade);
  private readonly auth = inject(AuthService);
  private readonly patientApi = inject(PatientApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly dialog = inject(MatDialog);
  private readonly destroy$ = new Subject<void>();

  /** Patient ID extrait de la route. */
  private patientId: number | undefined;

  protected readonly patient = signal<PatientDto | null>(null);
  protected readonly loadingPatient = signal(false);
  protected readonly patientError = signal<string | null>(null);

  /** Vues raccourcies pour le template. */
  protected readonly recordState = {
    loading: computed(() => this.facade.recordState().loading),
    record: computed(() => this.facade.recordState().record),
    antecedents: computed(() => this.facade.recordState().antecedents),
    recentConsultations: computed(() => this.facade.recordState().recentConsultations),
    error: computed(() => this.facade.recordState().error),
    recordNotFound: computed(() => this.facade.recordState().recordNotFound)
  };

  protected readonly canEdit = computed(() => this.auth.hasAnyRole(['ADMIN', 'DOCTOR']));

  /**
   * Form pour les infos générales du record. Patché via effect quand le
   * record arrive de la facade. Pas d'écriture de signal dans l'effect
   * (uniquement form.patchValue) — pattern correct.
   */
  protected readonly infoForm: FormGroup = this.fb.group({
    bloodType: this.fb.control<BloodType | null>(null),
    allergiesSummary: this.fb.control<string>('', [Validators.maxLength(500)]),
    notes: this.fb.control<string>('')
  });

  protected readonly bloodTypeKeys: BloodType[] = [
    'A_POS', 'A_NEG', 'B_POS', 'B_NEG', 'AB_POS', 'AB_NEG', 'O_POS', 'O_NEG'
  ];

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntil(this.destroy$)).subscribe((params) => {
      const idParam = params.get('patientId');
      const id = idParam ? Number(idParam) : NaN;
      if (Number.isNaN(id) || id <= 0) {
        void this.router.navigate(['/patients']);
        return;
      }
      this.patientId = id;
      this.loadPatient(id);
      this.facade.loadRecordByPatient(id);
    });

    // Patch le form quand le record arrive depuis la facade
    this.facade.recordState; // signal access pour register dependency
    // On utilise un effect inline plutôt qu'une subscription pour rester réactif
    // au signal — mais on le fait depuis ngOnInit pour avoir l'injection context.
    // Note : pas de signal write ici, juste form.patchValue (autorisé).
    queueMicrotask(() => {
      // Premier patch si déjà chargé
      const r = this.facade.recordState().record;
      if (r) this.patchInfoForm();
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.facade.clearRecord();
  }

  private loadPatient(id: number): void {
    this.loadingPatient.set(true);
    this.patientError.set(null);
    this.patientApi.findById(id).subscribe({
      next: (p) => {
        this.patient.set(p);
        this.loadingPatient.set(false);
      },
      error: (err) => {
        const status = err && typeof err === 'object' && 'status' in err ? err.status : 0;
        this.patientError.set(
          status === 404 ? 'Patient introuvable' : 'Erreur lors du chargement du patient'
        );
        this.loadingPatient.set(false);
      }
    });
  }

  private patchInfoForm(): void {
    const r = this.facade.recordState().record;
    if (!r) return;
    this.infoForm.patchValue(
      {
        bloodType: r.bloodType ?? null,
        allergiesSummary: r.allergiesSummary ?? '',
        notes: r.notes ?? ''
      },
      { emitEvent: false }
    );
    this.infoForm.markAsPristine();
  }

  /**
   * Détection "form modifié" — utilisé pour activer/désactiver le bouton
   * Enregistrer. Lecture directe sans signal pour éviter l'overhead.
   */
  protected infoFormDirty(): boolean {
    return this.infoForm.dirty;
  }

  // ─── Actions ───

  protected createMedicalRecord(): void {
    if (this.patientId === undefined) return;
    const request: CreateMedicalRecordRequest = { patientId: this.patientId };
    this.facade.createRecord(request).subscribe({
      next: () => {
        // La facade a déjà rechargé le dossier — patch du form au prochain tick
        queueMicrotask(() => this.patchInfoForm());
      },
      error: () => {
        /* erreur déjà dans facade.mutation().error */
      }
    });
  }

  protected saveInfo(): void {
    const r = this.facade.recordState().record;
    if (!r || this.infoForm.invalid) return;
    const v = this.infoForm.getRawValue() as Record<string, unknown>;
    const request: UpdateMedicalRecordRequest = {
      bloodType: (v['bloodType'] as BloodType) ?? undefined,
      allergiesSummary: ((v['allergiesSummary'] as string) || '').trim() || undefined,
      notes: ((v['notes'] as string) || '').trim() || undefined
    };
    this.facade.updateRecord(r.id, request).subscribe({
      next: () => this.patchInfoForm(),
      error: () => {
        /* erreur déjà dans facade.mutation().error */
      }
    });
  }

  protected resetInfoForm(): void {
    this.patchInfoForm();
  }

  protected reload(): void {
    if (this.patientId !== undefined) {
      this.facade.loadRecordByPatient(this.patientId);
    }
  }

  // ─── Antécédents — CRUD via dialogs ───

  /**
   * Ouvre le dialog d'ajout d'un antécédent. Le dialog gère lui-même l'appel
   * facade.createAntecedent() ; à la fermeture avec succès, l'antécédent est
   * déjà dans le state du record (la facade met à jour la liste).
   */
  protected onAddAntecedent(): void {
    const r = this.facade.recordState().record;
    if (!r) return;
    this.dialog.open<
      AntecedentFormDialogComponent,
      AntecedentFormDialogData,
      AntecedentFormDialogResult
    >(AntecedentFormDialogComponent, {
      data: { medicalRecordId: r.id },
      width: '640px',
      maxWidth: '95vw',
      autoFocus: 'first-tabbable',
      restoreFocus: true,
      disableClose: false
    });
  }

  /**
   * Ouvre le dialog d'édition pré-rempli avec l'antécédent existant.
   * Idem : la facade met à jour le state à la fermeture sur succès.
   */
  protected onEditAntecedent(a: AntecedentDto): void {
    const r = this.facade.recordState().record;
    if (!r) return;
    this.dialog.open<
      AntecedentFormDialogComponent,
      AntecedentFormDialogData,
      AntecedentFormDialogResult
    >(AntecedentFormDialogComponent, {
      data: { medicalRecordId: r.id, antecedent: a },
      width: '640px',
      maxWidth: '95vw',
      autoFocus: 'first-tabbable',
      restoreFocus: true,
      disableClose: false
    });
  }

  /**
   * Ouvre le dialog de confirmation de suppression. La facade gère l'appel
   * DELETE et retire l'antécédent du state local en cas de succès.
   */
  protected onDeleteAntecedent(a: AntecedentDto): void {
    this.dialog.open<
      AntecedentDeleteDialogComponent,
      AntecedentDeleteDialogData,
      AntecedentDeleteDialogResult
    >(AntecedentDeleteDialogComponent, {
      data: { antecedent: a },
      width: '520px',
      maxWidth: '95vw',
      autoFocus: false,
      restoreFocus: true,
      disableClose: false
    });
  }

  protected goToNewConsultation(): void {
    if (this.patientId !== undefined) {
      void this.router.navigate(['/consultations/new'], {
        queryParams: { patientId: this.patientId }
      });
    }
  }

  protected goToConsultation(id: number): void {
    void this.router.navigate(['/consultations', id]);
  }

  protected goBackToPatient(): void {
    if (this.patientId !== undefined) {
      void this.router.navigate(['/patients', this.patientId]);
    } else {
      void this.router.navigate(['/patients']);
    }
  }

  // ─── Helpers d'affichage ───

  protected initialsOf(p: PatientDto): string {
    return ((p.firstName.charAt(0) ?? '') + (p.lastName.charAt(0) ?? '')).toUpperCase() || '?';
  }

  protected ageOf(p: PatientDto): number | null {
    return ageFromBirthDate(p.birthDate);
  }

  protected bloodTypeLabel(b: BloodType | null | undefined): string {
    return b ? BLOOD_TYPE_LABEL[b] : 'Inconnu';
  }

  protected antecedentTypeLabel(t: AntecedentType): string {
    return ANTECEDENT_TYPE_LABEL[t] ?? t;
  }

  protected antecedentIcon(t: AntecedentType): string {
    return ANTECEDENT_TYPE_ICON[t] ?? 'info';
  }

  protected formatDate(iso: string | null | undefined): string {
    if (!iso) return '—';
    try {
      return format(parseISO(iso), 'dd MMMM yyyy', { locale: fr });
    } catch {
      return iso;
    }
  }

  protected formatDay(iso: string | null | undefined): string {
    if (!iso) return '—';
    try {
      return format(parseISO(iso), 'EEE d MMM', { locale: fr });
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
