import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  computed,
  inject
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatMenuModule } from '@angular/material/menu';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import {
  PatientArchiveDialogComponent,
  PatientArchiveDialogResult
} from '../components/patient-archive-dialog.component';
import { format, parseISO } from 'date-fns';
import { fr } from 'date-fns/locale';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { AuthService } from '@core/auth/auth.service';
import { PatientDto, BloodGroup, Civility, Gender } from '@api/models/patient.model';
import { ageFromBirthDate } from '@shared/utils/date.utils';
import { PatientFacade } from '../patient.facade';
import { PatientStatusBadgeComponent } from '../components/patient-status-badge.component';

const CIVILITY_LABEL: Record<Civility, string> = {
  M: 'Monsieur',
  MME: 'Madame',
  MLLE: 'Mademoiselle',
  DR: 'Docteur',
  AUTRE: 'Autre'
};

const GENDER_LABEL: Record<Gender, string> = {
  MALE: 'Homme',
  FEMALE: 'Femme'
};

const BLOOD_GROUP_LABEL: Record<BloodGroup, string> = {
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

/**
 * Page détail patient — vue lecture seule des informations.
 *
 * <p><b>Architecture</b> :
 * <ul>
 *   <li>L'ID du patient vient de la route ({@code /patients/:id})</li>
 *   <li>{@link PatientFacade#loadDetail} déclenche la requête HTTP</li>
 *   <li>Le template lit {@code facade.detail()} (signal) → loading/error/success</li>
 *   <li>{@link PatientFacade#clearDetail} appelé en {@code ngOnDestroy} pour
 *       éviter d'afficher brièvement l'ancien patient lors de la prochaine
 *       navigation</li>
 * </ul>
 *
 * <p><b>Actions</b> :
 * <ul>
 *   <li>"Modifier" → {@code /patients/:id/edit} (composant formulaire en 9.4d)</li>
 *   <li>"Archiver" → ouvrira un dialog en 9.4e (ADMIN uniquement)</li>
 *   <li>"Retour" → bouton vers {@code /patients}</li>
 * </ul>
 */
@Component({
  selector: 'tm-patient-detail-page',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    PageHeaderComponent,
    PatientStatusBadgeComponent,
    MatMenuModule,
    MatButtonModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="patient-detail-page">
      @if (facade.detail().loading) {
        <div class="loading-state" aria-busy="true">
          <span class="material-icons-round spin">progress_activity</span>
          <p>Chargement du dossier patient…</p>
        </div>
      } @else if (facade.detail().error) {
        <div class="error-state">
          <span class="material-icons-round">error_outline</span>
          <h2>{{ facade.detail().error }}</h2>
          <button type="button" class="btn btn-outline" (click)="goBackToList()">
            Retour à la liste
          </button>
        </div>
      } @else {
        <!-- Nouvelle chaîne primary @if (séparée du chain précédent) → 'as p' autorisé -->
        @if (facade.detail().patient; as p) {
        <!-- En-tête : avatar, identité, statut, actions -->
        <header class="detail-header">
          <button
            type="button"
            class="back-button"
            (click)="goBackToList()"
            aria-label="Retour à la liste"
          >
            <span class="material-icons-round">arrow_back</span>
          </button>

          <div class="patient-identity">
            <div class="patient-avatar">{{ initialsOf(p) }}</div>
            <div class="identity-text">
              <h1 class="patient-name">
                {{ p.lastName | uppercase }} {{ p.firstName }}
                @if (ageOf(p); as age) {
                  <span class="patient-age">, {{ age }} ans</span>
                }
              </h1>
              <p class="patient-mrn">N° dossier : {{ p.medicalRecordNumber }}</p>
              <div class="status-row">
                <tm-patient-status-badge [status]="p.status" />
              </div>
            </div>
          </div>

          <div class="header-actions">
            @if (canEdit()) {
              <button
                type="button"
                class="btn btn-primary"
                (click)="goToEdit(p.id)"
              >
                <span class="material-icons-round">edit</span>
                Modifier
              </button>
            }
            @if (canArchive() || canEdit()) {
              <button
                mat-icon-button
                [matMenuTriggerFor]="actionsMenu"
                aria-label="Plus d'actions"
              >
                <span class="material-icons-round">more_vert</span>
              </button>
              <mat-menu #actionsMenu="matMenu">
                @if (canViewMedicalRecord()) {
                  <button mat-menu-item (click)="goToMedicalRecord(p.id)">
                    <span class="material-icons-round">medical_information</span>
                    <span>Dossier médical</span>
                  </button>
                }
                <button mat-menu-item (click)="goToNewConsultation(p.id)">
                  <span class="material-icons-round">add</span>
                  <span>Nouvelle consultation</span>
                </button>
                @if (canArchive() && p.status !== 'ARCHIVED') {
                  <button mat-menu-item (click)="onArchiveClick(p)">
                    <span class="material-icons-round">archive</span>
                    <span>Archiver le dossier</span>
                  </button>
                }
              </mat-menu>
            }
          </div>
        </header>

        <!-- Sections en cards -->
        <section class="detail-sections">
          <!-- Identité -->
          <article class="card detail-card">
            <h2 class="card-title">
              <span class="material-icons-round">person</span>
              Identité
            </h2>
            <dl class="info-grid">
              <dt>Civilité</dt>
              <dd>{{ civilityLabel(p.civility) }}</dd>

              <dt>Nom</dt>
              <dd>{{ p.lastName }}</dd>

              <dt>Prénom</dt>
              <dd>{{ p.firstName }}</dd>

              <dt>Date de naissance</dt>
              <dd>{{ formatDate(p.birthDate) }}@if (ageOf(p); as age) { ({{ age }} ans)}</dd>

              <dt>Genre</dt>
              <dd>{{ genderLabel(p.gender) }}</dd>
            </dl>
          </article>

          <!-- Contact -->
          <article class="card detail-card">
            <h2 class="card-title">
              <span class="material-icons-round">contact_phone</span>
              Contact
            </h2>
            <dl class="info-grid">
              <dt>Téléphone</dt>
              <dd>{{ p.phone || '—' }}</dd>

              <dt>Email</dt>
              <dd>
                @if (p.email) {
                  <a [href]="'mailto:' + p.email">{{ p.email }}</a>
                } @else {
                  —
                }
              </dd>
            </dl>
          </article>

          <!-- Adresse -->
          <article class="card detail-card">
            <h2 class="card-title">
              <span class="material-icons-round">place</span>
              Adresse
            </h2>
            @if (hasAddress(p)) {
              <p class="address-block">
                @if (p.addressLine1) { {{ p.addressLine1 }}<br /> }
                @if (p.addressLine2) { {{ p.addressLine2 }}<br /> }
                @if (p.postalCode || p.city) {
                  {{ p.postalCode || '' }} {{ p.city || '' }}
                  @if (p.country) { , {{ p.country }} }
                }
              </p>
            } @else {
              <p class="text-muted no-content">Aucune adresse renseignée</p>
            }
          </article>

          <!-- Médical -->
          <article class="card detail-card">
            <h2 class="card-title">
              <span class="material-icons-round">favorite</span>
              Médical
            </h2>
            <dl class="info-grid">
              <dt>Groupe sanguin</dt>
              <dd>{{ p.bloodGroup ? bloodGroupLabel(p.bloodGroup) : '—' }}</dd>

              <dt>Allergies</dt>
              <dd>
                @if (p.allergies) {
                  <span class="allergies-warning">
                    <span class="material-icons-round">warning</span>
                    {{ p.allergies }}
                  </span>
                } @else {
                  —
                }
              </dd>
            </dl>
          </article>

          <!-- Contact d'urgence -->
          <article class="card detail-card">
            <h2 class="card-title">
              <span class="material-icons-round">emergency</span>
              Contact d'urgence
            </h2>
            <dl class="info-grid">
              <dt>Nom</dt>
              <dd>{{ p.emergencyContactName || '—' }}</dd>

              <dt>Téléphone</dt>
              <dd>{{ p.emergencyContactPhone || '—' }}</dd>
            </dl>
          </article>
        </section>

        <!-- Footer audit -->
        <footer class="detail-footer text-muted">
          <span>
            Créé le {{ formatDateTime(p.createdAt) }}
            @if (p.createdBy) { par {{ p.createdBy }} }
          </span>
          <span>·</span>
          <span>
            Modifié le {{ formatDateTime(p.updatedAt) }}
            @if (p.updatedBy) { par {{ p.updatedBy }} }
          </span>
        </footer>
        }
      }
    </div>
  `,
  styles: [
    `
      .patient-detail-page {
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
      .error-state h2 {
        font-size: 18px;
        margin: 0;
      }
      .spin {
        animation: tm-spin 0.9s linear infinite;
      }
      @keyframes tm-spin {
        from { transform: rotate(0deg); }
        to { transform: rotate(360deg); }
      }

      /* En-tête */
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
        transition: background 0.15s;
        flex-shrink: 0;
      }
      .back-button:hover {
        background: rgba(0, 0, 0, 0.04);
      }
      .patient-identity {
        display: flex;
        gap: 16px;
        align-items: center;
        flex: 1;
        min-width: 0;
      }
      .patient-avatar {
        width: 64px;
        height: 64px;
        border-radius: 50%;
        background: var(--color-primary, #2963b0);
        color: #fff;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 24px;
        font-weight: 700;
        flex-shrink: 0;
      }
      .identity-text {
        min-width: 0;
      }
      .patient-name {
        font-size: 22px;
        font-weight: 700;
        margin: 0;
        line-height: 1.2;
      }
      .patient-age {
        font-weight: 400;
        color: var(--color-text-muted);
        font-size: 18px;
      }
      .patient-mrn {
        font-family: var(--font-mono, ui-monospace, monospace);
        font-size: 13px;
        color: var(--color-text-muted);
        margin: 4px 0;
      }
      .status-row {
        margin-top: 4px;
      }
      .header-actions {
        display: flex;
        align-items: center;
        gap: 8px;
        flex-shrink: 0;
      }

      /* Sections en grid 2 colonnes */
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
      .card-title {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 15px;
        font-weight: 600;
        margin: 0 0 16px;
        color: var(--color-text);
      }
      .card-title .material-icons-round {
        font-size: 20px;
        color: var(--color-primary, #2963b0);
      }

      .info-grid {
        display: grid;
        grid-template-columns: max-content 1fr;
        gap: 8px 16px;
        margin: 0;
      }
      .info-grid dt {
        font-size: 13px;
        color: var(--color-text-muted);
        font-weight: 500;
      }
      .info-grid dd {
        margin: 0;
        font-size: 14px;
        word-break: break-word;
      }
      .info-grid dd a {
        color: var(--color-primary, #2963b0);
        text-decoration: none;
      }
      .info-grid dd a:hover {
        text-decoration: underline;
      }

      .address-block {
        margin: 0;
        line-height: 1.6;
        font-size: 14px;
      }

      .allergies-warning {
        display: inline-flex;
        align-items: flex-start;
        gap: 6px;
        color: #b91c1c;
        font-weight: 500;
      }
      .allergies-warning .material-icons-round {
        font-size: 18px;
        flex-shrink: 0;
        margin-top: 1px;
      }

      .text-muted {
        color: var(--color-text-muted);
      }
      .no-content {
        font-style: italic;
      }

      /* Footer audit */
      .detail-footer {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        font-size: 12px;
        padding: 8px 4px;
      }

      ::ng-deep .mat-mdc-menu-item .material-icons-round {
        margin-right: 8px;
        font-size: 18px;
      }
    `
  ]
})
export class PatientDetailPageComponent implements OnInit, OnDestroy {
  protected readonly facade = inject(PatientFacade);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  protected readonly canEdit = computed(() =>
    this.auth.hasAnyRole(['ADMIN', 'DOCTOR', 'RECEPTIONIST'])
  );
  protected readonly canArchive = computed(() => this.auth.hasAnyRole(['ADMIN']));
  protected readonly canViewMedicalRecord = computed(() =>
    this.auth.hasAnyRole(['ADMIN', 'DOCTOR'])
  );

  ngOnInit(): void {
    // L'ID est dans le paramètre route — converti en number (le backend
    // exige un long, le service.ts convertit côté API).
    const idParam = this.route.snapshot.paramMap.get('id');
    const id = idParam ? Number(idParam) : NaN;
    if (Number.isNaN(id) || id <= 0) {
      // ID invalide → retour liste
      void this.router.navigate(['/patients']);
      return;
    }
    this.facade.loadDetail(id);
  }

  ngOnDestroy(): void {
    // Vider le détail évite de "flasher" l'ancien patient en attendant le
    // chargement du nouveau lors d'une navigation directe entre détails.
    this.facade.clearDetail();
  }

  protected goBackToList(): void {
    void this.router.navigate(['/patients']);
  }

  protected goToEdit(id: number): void {
    void this.router.navigate(['/patients', id, 'edit']);
  }

  protected goToNewConsultation(patientId: number): void {
    void this.router.navigate(['/consultations/new'], {
      queryParams: { patientId }
    });
  }

  protected goToMedicalRecord(patientId: number): void {
    void this.router.navigate(['/patients', patientId, 'medical-record']);
  }

  protected onArchiveClick(p: PatientDto): void {
    this.dialog
      .open<PatientArchiveDialogComponent, { patient: PatientDto }, PatientArchiveDialogResult>(
        PatientArchiveDialogComponent,
        {
          data: { patient: p },
          width: '480px',
          // disableClose : on ne peut fermer que par les boutons (pas par
          // ESC ou clic-extérieur) pour éviter les annulations involontaires
          // pendant l'envoi de la requête.
          disableClose: false
        }
      )
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          // facade.archive() a déjà mis à jour facade.detail() en interne
          // (le statut passe à ARCHIVED) — la page se ré-affiche automatiquement
          // grâce aux signals. Pas besoin de refetch.
        }
      });
  }

  protected initialsOf(p: PatientDto): string {
    const f = p.firstName.charAt(0);
    const l = p.lastName.charAt(0);
    return (f + l).toUpperCase() || '?';
  }

  protected ageOf(p: PatientDto): number | null {
    return ageFromBirthDate(p.birthDate);
  }

  protected hasAddress(p: PatientDto): boolean {
    return !!(p.addressLine1 || p.addressLine2 || p.postalCode || p.city || p.country);
  }

  protected civilityLabel(c: Civility): string {
    return CIVILITY_LABEL[c] ?? c;
  }

  protected genderLabel(g: Gender): string {
    return GENDER_LABEL[g] ?? g;
  }

  protected bloodGroupLabel(b: BloodGroup): string {
    return BLOOD_GROUP_LABEL[b] ?? b;
  }

  protected formatDate(iso: string | null | undefined): string {
    if (!iso) return '—';
    try {
      return format(parseISO(iso), 'dd MMMM yyyy', { locale: fr });
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
