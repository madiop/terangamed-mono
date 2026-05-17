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
import { Subject, takeUntil } from 'rxjs';
import { format, parseISO } from 'date-fns';
import { fr } from 'date-fns/locale';
import { Currency, DoctorDto, Specialty } from '@api/models/doctor.model';
import { DoctorFacade } from '../doctor.facade';
import { DoctorStatusBadgeComponent } from '../components/doctor-status-badge.component';
import {
  DoctorPutOnLeaveDialogComponent,
  DoctorPutOnLeaveDialogData,
  DoctorPutOnLeaveDialogResult
} from '../components/doctor-put-on-leave-dialog.component';
import {
  DoctorRetireDialogComponent,
  DoctorRetireDialogData,
  DoctorRetireDialogResult
} from '../components/doctor-retire-dialog.component';
import {
  DoctorReactivateDialogComponent,
  DoctorReactivateDialogData,
  DoctorReactivateDialogResult
} from '../components/doctor-reactivate-dialog.component';

const SPECIALTY_LABEL: Record<Specialty, string> = {
  GENERAL_MEDICINE: 'Médecine générale',
  CARDIOLOGY: 'Cardiologie',
  DERMATOLOGY: 'Dermatologie',
  PEDIATRICS: 'Pédiatrie',
  GYNECOLOGY: 'Gynécologie',
  DENTISTRY: 'Dentisterie',
  OPHTHALMOLOGY: 'Ophtalmologie',
  PSYCHIATRY: 'Psychiatrie',
  ORTHOPEDICS: 'Orthopédie',
  OTHER: 'Autre'
};

const CURRENCY_SYMBOL: Record<Currency, string> = {
  XOF: 'F CFA',
  XAF: 'F CFA',
  EUR: '€',
  USD: '$'
};

/**
 * Page détail médecin — `/admin/staff/:id`.
 *
 * <h3>Sections affichées</h3>
 * <ul>
 *   <li>Header : avatar, nom complet, spécialité, badge statut</li>
 *   <li>Actions de transition d'état (selon le statut courant)</li>
 *   <li>Infos professionnelles (licence, expérience, tarif)</li>
 *   <li>Coordonnées de contact</li>
 *   <li>Biographie (si renseignée)</li>
 *   <li>Footer audit</li>
 * </ul>
 *
 * <h3>Actions selon statut</h3>
 * <table>
 *   <tr><th>Statut courant</th><th>Actions disponibles</th></tr>
 *   <tr><td>{@code ACTIVE}</td><td>Modifier · Mettre en congé · Retraiter</td></tr>
 *   <tr><td>{@code ON_LEAVE}</td><td>Modifier · Reprendre l'activité · Retraiter</td></tr>
 *   <tr><td>{@code RETIRED}</td><td>Réactiver (sortie de retraite)</td></tr>
 * </table>
 *
 * <p>La <b>suppression physique</b> n'est volontairement pas exposée en UI
 * (cf. décision 9.7 — préserver l'historique audit).
 *
 * <h3>Permissions</h3>
 * Module entier ADMIN-only (cf. roleGuard sur {@code /admin}).
 */
@Component({
  selector: 'tm-doctor-detail-page',
  standalone: true,
  imports: [CommonModule, DoctorStatusBadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="doctor-detail-page">
      @if (facade.detail().loading) {
        <div class="loading-state" aria-busy="true">
          <span class="material-icons-round spin">progress_activity</span>
          <p>Chargement du médecin…</p>
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
        @if (facade.detail().doctor; as d) {
          <!-- ─── Header ─── -->
          <header class="detail-header">
            <button
              type="button"
              class="back-button"
              (click)="goBackToList()"
              aria-label="Retour à la liste"
            >
              <span class="material-icons-round">arrow_back</span>
            </button>

            <div class="avatar-large">{{ initialsOf(d) }}</div>

            <div class="header-content">
              <h1 class="doctor-name">
                Dr {{ d.lastName | uppercase }} {{ d.firstName }}
              </h1>
              <p class="doctor-specialty">{{ specialtyLabel(d.specialty) }}</p>
              <div class="header-meta">
                <tm-doctor-status-badge [status]="d.status" [showIcon]="true" />
                <span class="license-info">{{ d.licenseNumber }}</span>
              </div>
            </div>

            <div class="header-actions">
              @if (d.status !== 'RETIRED') {
                <button
                  type="button"
                  class="btn btn-outline"
                  (click)="goToEdit(d.id)"
                  [disabled]="facade.mutating()"
                >
                  <span class="material-icons-round">edit</span>
                  Modifier
                </button>
              }

              @if (d.status === 'ACTIVE') {
                <button
                  type="button"
                  class="btn btn-outline"
                  (click)="onPutOnLeave(d)"
                  [disabled]="facade.mutating()"
                >
                  <span class="material-icons-round">beach_access</span>
                  Mettre en congé
                </button>
                <button
                  type="button"
                  class="btn btn-outline"
                  (click)="onRetire(d)"
                  [disabled]="facade.mutating()"
                >
                  <span class="material-icons-round">elderly</span>
                  Retraiter
                </button>
              }

              @if (d.status === 'ON_LEAVE') {
                <button
                  type="button"
                  class="btn btn-primary"
                  (click)="onReactivate(d)"
                  [disabled]="facade.mutating()"
                >
                  <span class="material-icons-round">play_circle</span>
                  Reprendre l'activité
                </button>
                <button
                  type="button"
                  class="btn btn-outline"
                  (click)="onRetire(d)"
                  [disabled]="facade.mutating()"
                >
                  <span class="material-icons-round">elderly</span>
                  Retraiter
                </button>
              }

              @if (d.status === 'RETIRED') {
                <button
                  type="button"
                  class="btn btn-outline"
                  (click)="onReactivate(d)"
                  [disabled]="facade.mutating()"
                >
                  <span class="material-icons-round">play_circle</span>
                  Réactiver
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

          <!-- ─── Sections ─── -->
          <section class="detail-sections">
            <!-- Infos professionnelles -->
            <article class="card detail-card">
              <h2 class="card-title">
                <span class="material-icons-round">medical_services</span>
                Informations professionnelles
              </h2>
              <dl class="info-grid">
                <dt>N° licence</dt>
                <dd class="mono">{{ d.licenseNumber }}</dd>

                <dt>Spécialité</dt>
                <dd>{{ specialtyLabel(d.specialty) }}</dd>

                <dt>Années d'expérience</dt>
                <dd>{{ d.yearsOfExperience != null ? d.yearsOfExperience + ' ans' : '—' }}</dd>

                <dt>Tarif consultation</dt>
                <dd>{{ formatFee(d) }}</dd>
              </dl>
            </article>

            <!-- Contact -->
            <article class="card detail-card">
              <h2 class="card-title">
                <span class="material-icons-round">contact_phone</span>
                Coordonnées
              </h2>
              <dl class="info-grid">
                <dt>Email</dt>
                <dd>
                  @if (d.email) {
                    <a [href]="'mailto:' + d.email" class="link">{{ d.email }}</a>
                  } @else {
                    <span class="text-muted">—</span>
                  }
                </dd>

                <dt>Téléphone</dt>
                <dd>
                  @if (d.phone) {
                    <a [href]="'tel:' + d.phone" class="link">{{ d.phone }}</a>
                  } @else {
                    <span class="text-muted">—</span>
                  }
                </dd>

                <dt>Cabinet</dt>
                <dd>{{ d.officeAddress || '—' }}</dd>
              </dl>
            </article>

            <!-- Biographie -->
            @if (d.bio) {
              <article class="card detail-card bio-card">
                <h2 class="card-title">
                  <span class="material-icons-round">description</span>
                  Présentation
                </h2>
                <p class="bio-content multiline">{{ d.bio }}</p>
              </article>
            }
          </section>

          <!-- ─── Footer audit ─── -->
          <footer class="detail-footer text-muted">
            <span>
              Créé le {{ formatDateTime(d.createdAt) }}
              @if (d.createdBy) { par {{ d.createdBy }} }
            </span>
            <span>·</span>
            <span>
              Modifié le {{ formatDateTime(d.updatedAt) }}
              @if (d.updatedBy) { par {{ d.updatedBy }} }
            </span>
            <span>·</span>
            <span>Version {{ d.version }}</span>
          </footer>
        }
      }
    </div>
  `,
  styles: [
    `
      .doctor-detail-page {
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
        animation: tm-doctor-detail-spin 0.9s linear infinite;
      }
      @keyframes tm-doctor-detail-spin {
        from { transform: rotate(0deg); }
        to { transform: rotate(360deg); }
      }

      .detail-header {
        display: flex;
        align-items: center;
        gap: 16px;
        background: var(--color-surface);
        padding: 20px 24px;
        border-radius: var(--radius);
        box-shadow: var(--shadow);
        flex-wrap: wrap;
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
      .avatar-large {
        width: 64px;
        height: 64px;
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
      .header-content {
        flex: 1;
        min-width: 240px;
      }
      .doctor-name {
        font-size: 22px;
        font-weight: 700;
        margin: 0;
      }
      .doctor-specialty {
        font-size: 14px;
        color: var(--color-primary, #2963b0);
        font-weight: 500;
        margin: 2px 0;
      }
      .header-meta {
        display: flex;
        align-items: center;
        gap: 12px;
        margin-top: 6px;
        flex-wrap: wrap;
      }
      .license-info {
        font-family: var(--font-mono, ui-monospace, monospace);
        font-size: 12px;
        color: var(--color-text-muted);
      }
      .header-actions {
        display: flex;
        gap: 8px;
        flex-wrap: wrap;
      }
      .header-actions .btn {
        display: inline-flex;
        align-items: center;
        gap: 6px;
      }
      .header-actions .material-icons-round {
        font-size: 18px;
      }

      .error-banner {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 12px 16px;
        background: #fef2f2;
        border-left: 4px solid #ef4444;
        border-radius: var(--radius);
        color: #991b1b;
      }
      .error-banner p {
        flex: 1;
        margin: 0;
      }

      /* Sections */
      .detail-sections {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 16px;
      }
      @media (max-width: 900px) {
        .detail-sections {
          grid-template-columns: 1fr;
        }
      }
      .card {
        background: var(--color-surface);
        border-radius: var(--radius);
        box-shadow: var(--shadow);
        padding: 20px 24px;
      }
      .bio-card {
        grid-column: span 2;
      }
      @media (max-width: 900px) {
        .bio-card {
          grid-column: span 1;
        }
      }
      .card-title {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 15px;
        font-weight: 600;
        margin: 0 0 14px;
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
        font-weight: 500;
        word-break: break-word;
      }
      .mono {
        font-family: var(--font-mono, ui-monospace, monospace);
      }
      .link {
        color: var(--color-primary, #2963b0);
        text-decoration: none;
      }
      .link:hover {
        text-decoration: underline;
      }
      .text-muted {
        color: var(--color-text-muted);
      }

      .bio-content {
        margin: 0;
        font-size: 14px;
        line-height: 1.6;
      }
      .multiline {
        white-space: pre-wrap;
        word-break: break-word;
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
export class DoctorDetailPageComponent implements OnInit, OnDestroy {
  protected readonly facade = inject(DoctorFacade);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  private readonly destroy$ = new Subject<void>();

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntil(this.destroy$)).subscribe((params) => {
      const idParam = params.get('id');
      const id = idParam ? Number(idParam) : NaN;
      if (Number.isNaN(id) || id <= 0) {
        void this.router.navigate(['/admin/staff']);
        return;
      }
      this.facade.loadDetail(id);
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.facade.clearDetail();
  }

  // ─── Navigation ───

  protected goBackToList(): void {
    void this.router.navigate(['/admin/staff']);
  }

  protected goToEdit(id: number): void {
    void this.router.navigate(['/admin/staff', id, 'edit']);
  }

  // ─── Dialogs transitions d'état ───

  /**
   * Ouvre le dialog de mise en congé. La facade met à jour le statut dans
   * le détail à la fermeture sur succès — la vue se rafraîchit automatiquement.
   */
  protected onPutOnLeave(d: DoctorDto): void {
    this.dialog.open<
      DoctorPutOnLeaveDialogComponent,
      DoctorPutOnLeaveDialogData,
      DoctorPutOnLeaveDialogResult
    >(DoctorPutOnLeaveDialogComponent, {
      data: { doctor: d },
      width: '520px',
      maxWidth: '95vw',
      autoFocus: false,
      restoreFocus: true,
      disableClose: false
    });
  }

  protected onRetire(d: DoctorDto): void {
    this.dialog.open<
      DoctorRetireDialogComponent,
      DoctorRetireDialogData,
      DoctorRetireDialogResult
    >(DoctorRetireDialogComponent, {
      data: { doctor: d },
      width: '560px',
      maxWidth: '95vw',
      autoFocus: false,
      restoreFocus: true,
      disableClose: false
    });
  }

  protected onReactivate(d: DoctorDto): void {
    this.dialog.open<
      DoctorReactivateDialogComponent,
      DoctorReactivateDialogData,
      DoctorReactivateDialogResult
    >(DoctorReactivateDialogComponent, {
      data: { doctor: d },
      width: '560px',
      maxWidth: '95vw',
      autoFocus: false,
      restoreFocus: true,
      disableClose: false
    });
  }

  // ─── Helpers d'affichage ───

  protected initialsOf(d: DoctorDto): string {
    return ((d.firstName.charAt(0) ?? '') + (d.lastName.charAt(0) ?? '')).toUpperCase() || '?';
  }

  protected specialtyLabel(s: Specialty): string {
    return SPECIALTY_LABEL[s] ?? s;
  }

  protected formatFee(d: DoctorDto): string {
    if (d.consultationFee == null) return '—';
    const symbol = d.consultationFeeCurrency
      ? CURRENCY_SYMBOL[d.consultationFeeCurrency]
      : '';
    return `${d.consultationFee.toLocaleString('fr-FR')} ${symbol}`.trim();
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
