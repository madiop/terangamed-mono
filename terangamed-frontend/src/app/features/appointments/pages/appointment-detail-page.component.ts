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
import { AppointmentDto, AppointmentStatus } from '@api/models/appointment.model';
import { AppointmentFacade } from '../appointment.facade';
import { AppointmentStatusBadgeComponent } from '../components/appointment-status-badge.component';
import {
  AppointmentConfirmActionDialogComponent,
  AppointmentConfirmActionDialogResult,
  AppointmentActionType,
  AppointmentConfirmActionDialogData
} from '../components/appointment-confirm-action-dialog.component';

/**
 * Page détail d'un rendez-vous + actions de transition d'état.
 *
 * <h3>Workflow état (cf. design 9.5)</h3>
 * <pre>
 *   PLANNED → confirm() → CONFIRMED → complete() → COMPLETED
 *      │                       │
 *      └─ cancel() ──┬─ noShow()
 *                    ▼
 *              CANCELLED / NO_SHOW
 * </pre>
 *
 * <h3>Actions par état</h3>
 * <ul>
 *   <li><b>PLANNED</b> : Confirmer · Modifier · Annuler</li>
 *   <li><b>CONFIRMED</b> : Terminer · Marquer absent · Annuler · Modifier</li>
 *   <li><b>COMPLETED / CANCELLED / NO_SHOW</b> : lecture seule</li>
 * </ul>
 *
 * <h3>Actions destructives</h3>
 * Annuler et Marquer absent passent par {@link AppointmentConfirmActionDialogComponent}
 * (dialog dédié qui gère l'appel facade + erreurs).
 *
 * <h3>Actions positives (Confirmer / Terminer)</h3>
 * Action immédiate sans dialog. La facade met à jour le statut local après
 * succès — l'UI se ré-affiche automatiquement.
 */
@Component({
  selector: 'tm-appointment-detail-page',
  standalone: true,
  imports: [CommonModule, AppointmentStatusBadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="appointment-detail-page">
      @if (facade.detail().loading) {
        <div class="loading-state" aria-busy="true">
          <span class="material-icons-round spin">progress_activity</span>
          <p>Chargement du rendez-vous…</p>
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
        @if (facade.detail().appointment; as a) {
          <!-- En-tête -->
          <header class="detail-header">
            <button
              type="button"
              class="back-button"
              (click)="goBackToList()"
              aria-label="Retour"
            >
              <span class="material-icons-round">arrow_back</span>
            </button>

            <div class="datetime-block">
              <div class="day-label">{{ formatDay(a.startTime) }}</div>
              <div class="time-label">
                {{ formatTime(a.startTime) }} – {{ formatTime(a.endTime) }}
                <span class="duration">({{ a.durationMinutes }} min)</span>
              </div>
              <div class="status-row">
                <tm-appointment-status-badge [status]="a.status" />
              </div>
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
                <span class="material-icons-round">person</span>
                Patient
              </h2>
              <div class="info-block">
                <div class="info-row">
                  <span class="info-label">Nom :</span>
                  <span class="info-value">{{ a.patientNameSnapshot }}</span>
                </div>
                <button
                  type="button"
                  class="btn btn-link"
                  (click)="goToPatient(a.patientId)"
                >
                  Voir le dossier patient →
                </button>
              </div>
            </article>

            <article class="card detail-card">
              <h2 class="card-title">
                <span class="material-icons-round">medical_services</span>
                Médecin
              </h2>
              <div class="info-block">
                <div class="info-row">
                  <span class="info-label">Nom :</span>
                  <span class="info-value">{{ a.doctorNameSnapshot }}</span>
                </div>
              </div>
            </article>

            <article class="card detail-card span-2">
              <h2 class="card-title">
                <span class="material-icons-round">description</span>
                Détails du rendez-vous
              </h2>
              <dl class="info-grid">
                <dt>Motif</dt>
                <dd>{{ a.reason || '—' }}</dd>

                <dt>Notes</dt>
                <dd class="notes-cell">{{ a.notes || '—' }}</dd>
              </dl>
            </article>
          </section>

          <!-- Actions disponibles -->
          @if (hasAnyAction(a)) {
            <section class="card actions-section">
              <h2 class="card-title">
                <span class="material-icons-round">play_circle</span>
                Actions disponibles
              </h2>
              <div class="actions-row">
                @if (canConfirm(a)) {
                  <button
                    type="button"
                    class="btn btn-success"
                    [disabled]="facade.mutating()"
                    (click)="onConfirm(a)"
                  >
                    <span class="material-icons-round">check_circle</span>
                    Confirmer
                  </button>
                }
                @if (canComplete(a)) {
                  <button
                    type="button"
                    class="btn btn-primary"
                    [disabled]="facade.mutating()"
                    (click)="onComplete(a)"
                  >
                    <span class="material-icons-round">task_alt</span>
                    Terminer (consultation)
                  </button>
                }
                @if (canEdit(a)) {
                  <button
                    type="button"
                    class="btn btn-outline"
                    (click)="goToEdit(a.id)"
                  >
                    <span class="material-icons-round">edit</span>
                    Modifier
                  </button>
                }
                @if (canMarkNoShow(a)) {
                  <button
                    type="button"
                    class="btn btn-warn-outline"
                    [disabled]="facade.mutating()"
                    (click)="onActionWithDialog(a, 'noShow')"
                  >
                    <span class="material-icons-round">person_off</span>
                    Marquer absent
                  </button>
                }
                @if (canCancel(a)) {
                  <button
                    type="button"
                    class="btn btn-warn-outline"
                    [disabled]="facade.mutating()"
                    (click)="onActionWithDialog(a, 'cancel')"
                  >
                    <span class="material-icons-round">cancel</span>
                    Annuler le rendez-vous
                  </button>
                }
              </div>
            </section>
          } @else {
            <!-- État final — pas d'action possible -->
            <section class="card final-state-info">
              <span class="material-icons-round">info</span>
              <p>Ce rendez-vous est dans un état final. Aucune action n'est possible.</p>
            </section>
          }

          <!-- Footer audit -->
          <footer class="detail-footer text-muted">
            <span>
              Créé le {{ formatDateTime(a.createdAt) }}
              @if (a.createdBy) { par {{ a.createdBy }} }
            </span>
            <span>·</span>
            <span>
              Modifié le {{ formatDateTime(a.updatedAt) }}
              @if (a.updatedBy) { par {{ a.updatedBy }} }
            </span>
          </footer>
        }
      }
    </div>
  `,
  styles: [
    `
      .appointment-detail-page {
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

      .datetime-block {
        flex: 1;
        min-width: 0;
      }
      .day-label {
        font-size: 22px;
        font-weight: 700;
        text-transform: capitalize;
      }
      .time-label {
        font-size: 16px;
        color: var(--color-text);
        margin-top: 4px;
      }
      .duration {
        color: var(--color-text-muted);
        font-size: 14px;
        margin-left: 6px;
      }
      .status-row {
        margin-top: 8px;
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
      .span-2 {
        grid-column: span 2;
        @media (max-width: 900px) {
          grid-column: span 1;
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

      .info-block {
        display: flex;
        flex-direction: column;
        gap: 8px;
      }
      .info-row {
        display: flex;
        gap: 8px;
        font-size: 14px;
      }
      .info-label {
        color: var(--color-text-muted);
        min-width: 60px;
      }
      .info-value {
        font-weight: 500;
      }
      .btn-link {
        background: none;
        border: none;
        padding: 0;
        color: var(--color-primary, #2963b0);
        cursor: pointer;
        font-size: 13px;
        font-weight: 500;
        text-align: left;
      }
      .btn-link:hover {
        text-decoration: underline;
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
      }
      .notes-cell {
        white-space: pre-wrap;
        word-break: break-word;
      }

      .actions-section {
        padding: 20px;
      }
      .actions-row {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
      }
      .btn-success {
        background: #10b981;
        color: #fff;
      }
      .btn-success:hover:not(:disabled) {
        background: #059669;
      }
      .btn-warn-outline {
        background: transparent;
        border: 1px solid #ef4444;
        color: #b91c1c;
      }
      .btn-warn-outline:hover:not(:disabled) {
        background: #fef2f2;
      }

      .final-state-info {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 16px 20px;
        color: var(--color-text-muted);
      }
      .final-state-info .material-icons-round {
        color: #6b7280;
      }
      .final-state-info p {
        margin: 0;
      }

      .text-muted {
        color: var(--color-text-muted);
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
export class AppointmentDetailPageComponent implements OnInit, OnDestroy {
  protected readonly facade = inject(AppointmentFacade);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  /** Cache pour éviter de recomputer la même chose dans plusieurs `canX()`. */
  protected readonly currentRoles = computed(() => this.auth.roles());

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    const id = idParam ? Number(idParam) : NaN;
    if (Number.isNaN(id) || id <= 0) {
      void this.router.navigate(['/appointments']);
      return;
    }
    this.facade.loadDetail(id);
  }

  ngOnDestroy(): void {
    this.facade.clearDetail();
  }

  // ─── Permissions par état + rôle ───

  protected canConfirm(a: AppointmentDto): boolean {
    return a.status === 'PLANNED' && this.auth.hasAnyRole(['ADMIN', 'DOCTOR', 'RECEPTIONIST']);
  }

  protected canComplete(a: AppointmentDto): boolean {
    return a.status === 'CONFIRMED' && this.auth.hasAnyRole(['ADMIN', 'DOCTOR']);
  }

  protected canEdit(a: AppointmentDto): boolean {
    return !this.isFinalStatus(a.status) && this.auth.hasAnyRole(['ADMIN', 'DOCTOR', 'RECEPTIONIST']);
  }

  protected canCancel(a: AppointmentDto): boolean {
    return !this.isFinalStatus(a.status) && this.auth.hasAnyRole(['ADMIN', 'DOCTOR', 'RECEPTIONIST']);
  }

  protected canMarkNoShow(a: AppointmentDto): boolean {
    return a.status === 'CONFIRMED' && this.auth.hasAnyRole(['ADMIN', 'DOCTOR', 'RECEPTIONIST']);
  }

  protected hasAnyAction(a: AppointmentDto): boolean {
    return (
      this.canConfirm(a) ||
      this.canComplete(a) ||
      this.canEdit(a) ||
      this.canCancel(a) ||
      this.canMarkNoShow(a)
    );
  }

  private isFinalStatus(s: AppointmentStatus): boolean {
    return s === 'COMPLETED' || s === 'CANCELLED' || s === 'NO_SHOW';
  }

  // ─── Actions ───

  /** Confirmer / Terminer — actions positives sans dialog. */
  protected onConfirm(a: AppointmentDto): void {
    this.facade.confirm(a.id).subscribe();
  }

  protected onComplete(a: AppointmentDto): void {
    this.facade.complete(a.id).subscribe();
  }

  /** Annuler / Marquer absent — actions destructives via dialog. */
  protected onActionWithDialog(a: AppointmentDto, action: AppointmentActionType): void {
    this.dialog
      .open<
        AppointmentConfirmActionDialogComponent,
        AppointmentConfirmActionDialogData,
        AppointmentConfirmActionDialogResult
      >(AppointmentConfirmActionDialogComponent, {
        data: { appointment: a, action },
        width: '480px'
      })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          // facade a déjà mis à jour le statut local — pas de refetch
        }
      });
  }

  // ─── Navigation ───

  protected goBackToList(): void {
    void this.router.navigate(['/appointments']);
  }

  protected goToEdit(id: number): void {
    void this.router.navigate(['/appointments', id, 'edit']);
  }

  protected goToPatient(patientId: number): void {
    void this.router.navigate(['/patients', patientId]);
  }

  // ─── Formatage ───

  protected formatDay(iso: string): string {
    try {
      return format(parseISO(iso), 'EEEE d MMMM yyyy', { locale: fr });
    } catch {
      return iso;
    }
  }

  protected formatTime(iso: string): string {
    try {
      return format(parseISO(iso), "HH'h'mm", { locale: fr });
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
