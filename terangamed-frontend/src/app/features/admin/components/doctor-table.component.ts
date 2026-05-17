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
import { Currency, DoctorDto, Specialty } from '@api/models/doctor.model';
import { DoctorStatusBadgeComponent } from './doctor-status-badge.component';

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
 * Permissions UI passées par le parent — déterminent quelles actions sont
 * visibles dans le menu contextuel de chaque ligne. Le bouton Supprimer
 * (DELETE physique) est <b>volontairement absent</b> : la désactivation
 * passe par {@code retire} pour préserver l'historique audit.
 */
export interface DoctorTablePermissions {
  readonly canEdit: boolean;
  readonly canTransition: boolean;
}

/**
 * Tableau Material des médecins — composant pur (input/output, pas d'API).
 *
 * <p>Affiche : Avatar / Nom complet / Spécialité / N° licence / Statut / Tarif
 * / Années exp. / Menu actions. Click sur la ligne (hors menu) → événement
 * {@code rowClick} pour navigation vers le détail.
 *
 * <p>Le menu contextuel propose, selon le statut courant et les permissions :
 * Modifier · Mettre en congé · Retraiter · Réactiver. Les actions de transition
 * sont émises au parent qui ouvre les dialogs de confirmation correspondants
 * (en 9.7d).
 */
@Component({
  selector: 'tm-doctor-table',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatMenuModule,
    MatButtonModule,
    MatSortModule,
    DoctorStatusBadgeComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="table-wrapper">
      <table
        mat-table
        [dataSource]="doctors"
        matSort
        (matSortChange)="onSort($event)"
        class="doctor-table"
      >
        <!-- Avatar (initiales) -->
        <ng-container matColumnDef="avatar">
          <th mat-header-cell *matHeaderCellDef class="avatar-col"></th>
          <td mat-cell *matCellDef="let d" class="avatar-col">
            <div class="avatar">{{ initialsOf(d) }}</div>
          </td>
        </ng-container>

        <!-- Nom complet -->
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef mat-sort-header="lastName">Médecin</th>
          <td mat-cell *matCellDef="let d" class="name-cell">
            <div class="doctor-name">{{ d.lastName | uppercase }} {{ d.firstName }}</div>
            <div class="doctor-email">{{ d.email || '—' }}</div>
          </td>
        </ng-container>

        <!-- Spécialité -->
        <ng-container matColumnDef="specialty">
          <th mat-header-cell *matHeaderCellDef mat-sort-header="specialty">Spécialité</th>
          <td mat-cell *matCellDef="let d">{{ specialtyLabel(d.specialty) }}</td>
        </ng-container>

        <!-- N° licence -->
        <ng-container matColumnDef="license">
          <th mat-header-cell *matHeaderCellDef>N° licence</th>
          <td mat-cell *matCellDef="let d" class="license-cell">{{ d.licenseNumber }}</td>
        </ng-container>

        <!-- Statut -->
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Statut</th>
          <td mat-cell *matCellDef="let d">
            <tm-doctor-status-badge [status]="d.status" />
          </td>
        </ng-container>

        <!-- Tarif -->
        <ng-container matColumnDef="fee">
          <th mat-header-cell *matHeaderCellDef class="fee-col">Tarif</th>
          <td mat-cell *matCellDef="let d" class="fee-col">{{ formatFee(d) }}</td>
        </ng-container>

        <!-- Années expérience -->
        <ng-container matColumnDef="experience">
          <th mat-header-cell *matHeaderCellDef class="exp-col">Exp.</th>
          <td mat-cell *matCellDef="let d" class="exp-col">{{ formatExperience(d) }}</td>
        </ng-container>

        <!-- Actions -->
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef class="actions-col"></th>
          <td mat-cell *matCellDef="let d" class="actions-col" (click)="$event.stopPropagation()">
            <button
              mat-icon-button
              [matMenuTriggerFor]="rowMenu"
              [matMenuTriggerData]="{ doctor: d }"
              aria-label="Actions"
            >
              <span class="material-icons-round">more_vert</span>
            </button>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr
          mat-row
          *matRowDef="let d; columns: displayedColumns"
          class="doctor-row"
          (click)="rowClick.emit(d)"
        ></tr>

        <!-- Empty state -->
        <tr class="empty-row" *matNoDataRow>
          <td [attr.colspan]="displayedColumns.length" class="empty-cell">
            @if (loading) {
              <span class="material-icons-round spin">progress_activity</span>
              <span>Chargement…</span>
            } @else {
              <span class="material-icons-round">search_off</span>
              <span>Aucun médecin ne correspond aux critères.</span>
            }
          </td>
        </tr>
      </table>
    </div>

    <!-- Menu contextuel partagé pour toutes les lignes -->
    <mat-menu #rowMenu="matMenu">
      <ng-template matMenuContent let-doctor="doctor">
        @if (permissions.canEdit) {
          <button mat-menu-item (click)="editClick.emit(doctor)">
            <span class="material-icons-round">edit</span>
            <span>Modifier</span>
          </button>
        }
        @if (permissions.canTransition && doctor.status === 'ACTIVE') {
          <button mat-menu-item (click)="putOnLeaveClick.emit(doctor)">
            <span class="material-icons-round">beach_access</span>
            <span>Mettre en congé</span>
          </button>
          <button mat-menu-item (click)="retireClick.emit(doctor)">
            <span class="material-icons-round">elderly</span>
            <span>Retraiter</span>
          </button>
        }
        @if (permissions.canTransition && doctor.status === 'ON_LEAVE') {
          <button mat-menu-item (click)="reactivateClick.emit(doctor)">
            <span class="material-icons-round">play_circle</span>
            <span>Reprendre l'activité</span>
          </button>
          <button mat-menu-item (click)="retireClick.emit(doctor)">
            <span class="material-icons-round">elderly</span>
            <span>Retraiter</span>
          </button>
        }
        @if (permissions.canTransition && doctor.status === 'RETIRED') {
          <button mat-menu-item (click)="reactivateClick.emit(doctor)">
            <span class="material-icons-round">play_circle</span>
            <span>Réactiver</span>
          </button>
        }
        @if (!permissions.canEdit && !permissions.canTransition) {
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
      .doctor-table {
        width: 100%;
      }
      .doctor-row {
        cursor: pointer;
        transition: background 0.15s;
      }
      .doctor-row:hover {
        background: rgba(41, 99, 176, 0.04);
      }
      .avatar-col {
        width: 56px;
        padding-left: 16px;
        padding-right: 0;
      }
      .avatar {
        width: 36px;
        height: 36px;
        border-radius: 50%;
        background: var(--color-primary, #2963b0);
        color: #fff;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 13px;
        font-weight: 700;
      }
      .name-cell {
        line-height: 1.3;
        padding: 8px 16px;
      }
      .doctor-name {
        font-weight: 600;
      }
      .doctor-email {
        font-size: 12px;
        color: var(--color-text-muted);
      }
      .license-cell {
        font-family: var(--font-mono, ui-monospace, monospace);
        font-size: 13px;
        color: var(--color-text-muted);
      }
      .fee-col {
        text-align: right;
        white-space: nowrap;
      }
      .exp-col {
        width: 80px;
        text-align: center;
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
        animation: tm-doctor-tbl-spin 0.9s linear infinite;
      }
      @keyframes tm-doctor-tbl-spin {
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
export class DoctorTableComponent {
  @Input() doctors: ReadonlyArray<DoctorDto> = [];
  @Input() loading = false;
  @Input() permissions: DoctorTablePermissions = { canEdit: false, canTransition: false };

  @Output() rowClick = new EventEmitter<DoctorDto>();
  @Output() editClick = new EventEmitter<DoctorDto>();
  @Output() putOnLeaveClick = new EventEmitter<DoctorDto>();
  @Output() retireClick = new EventEmitter<DoctorDto>();
  @Output() reactivateClick = new EventEmitter<DoctorDto>();
  @Output() sortChange = new EventEmitter<Sort>();

  protected readonly displayedColumns = [
    'avatar',
    'name',
    'specialty',
    'license',
    'status',
    'fee',
    'experience',
    'actions'
  ];

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

  protected formatExperience(d: DoctorDto): string {
    return d.yearsOfExperience != null ? `${d.yearsOfExperience} ans` : '—';
  }

  protected onSort(sort: Sort): void {
    this.sortChange.emit(sort);
  }
}
