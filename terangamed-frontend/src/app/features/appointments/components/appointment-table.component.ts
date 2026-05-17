import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatMenuModule } from '@angular/material/menu';
import { MatButtonModule } from '@angular/material/button';
import { MatSortModule, Sort } from '@angular/material/sort';
import { format, parseISO } from 'date-fns';
import { fr } from 'date-fns/locale';
import { AppointmentDto } from '@api/models/appointment.model';
import { AppointmentStatusBadgeComponent } from './appointment-status-badge.component';

/**
 * Permissions UI passées par le parent — déterminent quelles actions sont
 * affichées dans le menu contextuel de chaque ligne.
 */
export interface AppointmentTablePermissions {
  readonly canEdit: boolean;
  readonly canConfirm: boolean;
  readonly canComplete: boolean;
  readonly canCancel: boolean;
  readonly canMarkNoShow: boolean;
}

/**
 * Tableau Material des rendez-vous (composant pur — input/output).
 *
 * <p>Affiche : Date/heure, Patient, Médecin, Durée, Motif, Statut, Actions.
 *
 * <p>Click sur la ligne (hors menu) → événement {@code rowClick} pour navigation
 * vers le détail. Le menu (3 points) propose les actions disponibles selon
 * le statut courant ET les permissions reçues.
 */
@Component({
  selector: 'tm-appointment-table',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatMenuModule,
    MatButtonModule,
    MatSortModule,
    AppointmentStatusBadgeComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="table-wrapper">
      <table
        mat-table
        [dataSource]="appointments"
        matSort
        (matSortChange)="onSort($event)"
        class="appointment-table"
      >
        <!-- Date / Heure -->
        <ng-container matColumnDef="startTime">
          <th mat-header-cell *matHeaderCellDef mat-sort-header="startTime">Date / Heure</th>
          <td mat-cell *matCellDef="let a" class="datetime-cell">
            <div class="datetime-day">{{ formatDay(a.startTime) }}</div>
            <div class="datetime-time">{{ formatTime(a.startTime) }}</div>
          </td>
        </ng-container>

        <!-- Patient -->
        <ng-container matColumnDef="patient">
          <th mat-header-cell *matHeaderCellDef>Patient</th>
          <td mat-cell *matCellDef="let a">{{ a.patientNameSnapshot }}</td>
        </ng-container>

        <!-- Médecin -->
        <ng-container matColumnDef="doctor">
          <th mat-header-cell *matHeaderCellDef>Médecin</th>
          <td mat-cell *matCellDef="let a">{{ a.doctorNameSnapshot }}</td>
        </ng-container>

        <!-- Durée -->
        <ng-container matColumnDef="duration">
          <th mat-header-cell *matHeaderCellDef>Durée</th>
          <td mat-cell *matCellDef="let a">{{ a.durationMinutes }} min</td>
        </ng-container>

        <!-- Motif -->
        <ng-container matColumnDef="reason">
          <th mat-header-cell *matHeaderCellDef>Motif</th>
          <td mat-cell *matCellDef="let a" class="reason-cell">
            {{ a.reason || '—' }}
          </td>
        </ng-container>

        <!-- Statut -->
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Statut</th>
          <td mat-cell *matCellDef="let a">
            <tm-appointment-status-badge [status]="a.status" />
          </td>
        </ng-container>

        <!-- Actions -->
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef class="actions-col"></th>
          <td mat-cell *matCellDef="let a" class="actions-col" (click)="$event.stopPropagation()">
            <button
              mat-icon-button
              [matMenuTriggerFor]="rowMenu"
              [matMenuTriggerData]="{ appointment: a }"
              aria-label="Actions"
            >
              <span class="material-icons-round">more_vert</span>
            </button>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr
          mat-row
          *matRowDef="let a; columns: displayedColumns"
          class="appointment-row"
          (click)="rowClick.emit(a)"
        ></tr>

        <!-- Empty state -->
        <tr class="empty-row" *matNoDataRow>
          <td [attr.colspan]="displayedColumns.length" class="empty-cell">
            @if (loading) {
              <span class="material-icons-round spin">progress_activity</span>
              <span>Chargement…</span>
            } @else {
              <span class="material-icons-round">event_busy</span>
              <span>Aucun rendez-vous ne correspond aux critères.</span>
            }
          </td>
        </tr>
      </table>
    </div>

    <mat-menu #rowMenu="matMenu">
      <ng-template matMenuContent let-appointment="appointment">
        @if (canDoActionFor(appointment, 'edit')) {
          <button mat-menu-item (click)="editClick.emit(appointment)">
            <span class="material-icons-round">edit</span>
            <span>Modifier</span>
          </button>
        }
        @if (canDoActionFor(appointment, 'confirm')) {
          <button mat-menu-item (click)="confirmClick.emit(appointment)">
            <span class="material-icons-round">check_circle</span>
            <span>Confirmer</span>
          </button>
        }
        @if (canDoActionFor(appointment, 'complete')) {
          <button mat-menu-item (click)="completeClick.emit(appointment)">
            <span class="material-icons-round">task_alt</span>
            <span>Terminer (consultation)</span>
          </button>
        }
        @if (canDoActionFor(appointment, 'noShow')) {
          <button mat-menu-item (click)="noShowClick.emit(appointment)">
            <span class="material-icons-round">person_off</span>
            <span>Marquer absent</span>
          </button>
        }
        @if (canDoActionFor(appointment, 'cancel')) {
          <button mat-menu-item (click)="cancelClick.emit(appointment)">
            <span class="material-icons-round">cancel</span>
            <span>Annuler</span>
          </button>
        }
        @if (!hasAnyAction(appointment)) {
          <button mat-menu-item disabled>
            <span>Aucune action disponible</span>
          </button>
        }
      </ng-template>
    </mat-menu>
  `,
  styles: [
    `
      .table-wrapper {
        background: var(--color-surface);
        border-radius: var(--radius);
        box-shadow: var(--shadow);
        overflow: hidden;
      }
      .appointment-table {
        width: 100%;
      }
      .appointment-row {
        cursor: pointer;
        transition: background 0.15s;
      }
      .appointment-row:hover {
        background: rgba(41, 99, 176, 0.04);
      }
      .datetime-cell {
        line-height: 1.3;
        padding: 8px 16px;
      }
      .datetime-day {
        font-weight: 600;
      }
      .datetime-time {
        font-size: 13px;
        color: var(--color-text-muted);
      }
      .reason-cell {
        max-width: 280px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .actions-col {
        width: 56px;
        text-align: right;
      }
      .empty-cell {
        padding: 32px;
        text-align: center;
        color: var(--color-text-muted);
      }
      .empty-cell .material-icons-round {
        display: block;
        margin: 0 auto 8px;
        font-size: 32px;
        opacity: 0.5;
      }
      .spin {
        animation: tm-spin 0.9s linear infinite;
      }
      @keyframes tm-spin {
        from { transform: rotate(0deg); }
        to { transform: rotate(360deg); }
      }
      ::ng-deep .mat-mdc-menu-item .material-icons-round {
        margin-right: 8px;
        font-size: 18px;
      }
    `
  ]
})
export class AppointmentTableComponent {
  @Input() appointments: ReadonlyArray<AppointmentDto> = [];
  @Input() loading = false;
  @Input() permissions: AppointmentTablePermissions = {
    canEdit: false,
    canConfirm: false,
    canComplete: false,
    canCancel: false,
    canMarkNoShow: false
  };

  @Output() rowClick = new EventEmitter<AppointmentDto>();
  @Output() editClick = new EventEmitter<AppointmentDto>();
  @Output() confirmClick = new EventEmitter<AppointmentDto>();
  @Output() completeClick = new EventEmitter<AppointmentDto>();
  @Output() cancelClick = new EventEmitter<AppointmentDto>();
  @Output() noShowClick = new EventEmitter<AppointmentDto>();
  @Output() sortChange = new EventEmitter<Sort>();

  protected readonly displayedColumns = [
    'startTime',
    'patient',
    'doctor',
    'duration',
    'reason',
    'status',
    'actions'
  ];

  protected formatDay(iso: string): string {
    try {
      return format(parseISO(iso), 'EEE d MMM', { locale: fr });
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

  /**
   * Combine les permissions reçues avec les transitions valides selon le
   * statut courant. Ex: on ne peut "Confirmer" que depuis PLANNED.
   */
  protected canDoActionFor(
    appointment: AppointmentDto,
    action: 'edit' | 'confirm' | 'complete' | 'cancel' | 'noShow'
  ): boolean {
    const status = appointment.status;
    const isFinal = status === 'COMPLETED' || status === 'CANCELLED' || status === 'NO_SHOW';

    if (action === 'edit') {
      return this.permissions.canEdit && !isFinal;
    }
    if (action === 'confirm') {
      return this.permissions.canConfirm && status === 'PLANNED';
    }
    if (action === 'complete') {
      return this.permissions.canComplete && status === 'CONFIRMED';
    }
    if (action === 'cancel') {
      return this.permissions.canCancel && !isFinal;
    }
    if (action === 'noShow') {
      return this.permissions.canMarkNoShow && status === 'CONFIRMED';
    }
    return false;
  }

  protected hasAnyAction(appointment: AppointmentDto): boolean {
    return (
      this.canDoActionFor(appointment, 'edit') ||
      this.canDoActionFor(appointment, 'confirm') ||
      this.canDoActionFor(appointment, 'complete') ||
      this.canDoActionFor(appointment, 'cancel') ||
      this.canDoActionFor(appointment, 'noShow')
    );
  }

  protected onSort(sort: Sort): void {
    this.sortChange.emit(sort);
  }
}
