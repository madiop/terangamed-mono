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
import { PatientDto } from '@api/models/patient.model';
import { PatientStatusBadgeComponent } from './patient-status-badge.component';
import { ageFromBirthDate } from '@shared/utils/date.utils';

/**
 * Permissions UI passées par le parent — déterminent quelles actions sont
 * visibles dans le menu contextuel de chaque ligne.
 */
export interface PatientTablePermissions {
  readonly canEdit: boolean;
  readonly canArchive: boolean;
}

/**
 * Tableau Material des patients — composant pur (input/output, pas d'API).
 *
 * <p>Affiche : MRN, Nom complet, Âge, Téléphone, Ville, Statut, menu actions.
 * Click sur la ligne (hors menu) → événement {@code rowClick} pour navigation
 * vers le détail. Le menu (3 points) propose Modifier / Archiver selon les
 * permissions reçues.
 */
@Component({
  selector: 'tm-patient-table',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatMenuModule,
    MatButtonModule,
    MatSortModule,
    PatientStatusBadgeComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="table-wrapper">
      <table
        mat-table
        [dataSource]="patients"
        matSort
        (matSortChange)="onSort($event)"
        class="patient-table"
      >
        <!-- MRN -->
        <ng-container matColumnDef="mrn">
          <th mat-header-cell *matHeaderCellDef>N° dossier</th>
          <td mat-cell *matCellDef="let p" class="mrn-cell">{{ p.medicalRecordNumber }}</td>
        </ng-container>

        <!-- Nom complet -->
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef mat-sort-header="lastName">Nom complet</th>
          <td mat-cell *matCellDef="let p" class="name-cell">
            <div class="patient-name">{{ p.lastName | uppercase }} {{ p.firstName }}</div>
            <div class="patient-civility">{{ civilityLabel(p.civility) }}</div>
          </td>
        </ng-container>

        <!-- Âge -->
        <ng-container matColumnDef="age">
          <th mat-header-cell *matHeaderCellDef>Âge</th>
          <td mat-cell *matCellDef="let p">
            {{ ageOf(p) }}
          </td>
        </ng-container>

        <!-- Téléphone -->
        <ng-container matColumnDef="phone">
          <th mat-header-cell *matHeaderCellDef>Téléphone</th>
          <td mat-cell *matCellDef="let p">{{ p.phone || '—' }}</td>
        </ng-container>

        <!-- Ville -->
        <ng-container matColumnDef="city">
          <th mat-header-cell *matHeaderCellDef>Ville</th>
          <td mat-cell *matCellDef="let p">{{ p.city || '—' }}</td>
        </ng-container>

        <!-- Statut -->
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Statut</th>
          <td mat-cell *matCellDef="let p">
            <tm-patient-status-badge [status]="p.status" />
          </td>
        </ng-container>

        <!-- Actions -->
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef class="actions-col"></th>
          <td mat-cell *matCellDef="let p" class="actions-col" (click)="$event.stopPropagation()">
            <button
              mat-icon-button
              [matMenuTriggerFor]="rowMenu"
              [matMenuTriggerData]="{ patient: p }"
              aria-label="Actions"
            >
              <span class="material-icons-round">more_vert</span>
            </button>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr
          mat-row
          *matRowDef="let p; columns: displayedColumns"
          class="patient-row"
          (click)="rowClick.emit(p)"
        ></tr>

        <!-- Empty state -->
        <tr class="empty-row" *matNoDataRow>
          <td [attr.colspan]="displayedColumns.length" class="empty-cell">
            @if (loading) {
              <span class="material-icons-round spin">progress_activity</span>
              <span>Chargement…</span>
            } @else {
              <span class="material-icons-round">search_off</span>
              <span>Aucun patient ne correspond aux critères.</span>
            }
          </td>
        </tr>
      </table>
    </div>

    <!-- Menu contextuel partagé pour toutes les lignes -->
    <mat-menu #rowMenu="matMenu">
      <ng-template matMenuContent let-patient="patient">
        @if (permissions.canEdit) {
          <button mat-menu-item (click)="editClick.emit(patient)">
            <span class="material-icons-round">edit</span>
            <span>Modifier</span>
          </button>
        }
        @if (permissions.canArchive && patient.status !== 'ARCHIVED') {
          <button mat-menu-item (click)="archiveClick.emit(patient)">
            <span class="material-icons-round">archive</span>
            <span>Archiver</span>
          </button>
        }
        @if (!permissions.canEdit && !permissions.canArchive) {
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
      .patient-table {
        width: 100%;
      }
      .patient-row {
        cursor: pointer;
        transition: background 0.15s;
      }
      .patient-row:hover {
        background: rgba(41, 99, 176, 0.04);
      }
      .mrn-cell {
        font-family: var(--font-mono, ui-monospace, monospace);
        font-size: 13px;
        color: var(--color-text-muted);
      }
      .name-cell {
        line-height: 1.3;
        padding: 8px 16px;
      }
      .patient-name {
        font-weight: 600;
      }
      .patient-civility {
        font-size: 12px;
        color: var(--color-text-muted);
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
export class PatientTableComponent {
  @Input() patients: ReadonlyArray<PatientDto> = [];
  @Input() loading = false;
  @Input() permissions: PatientTablePermissions = { canEdit: false, canArchive: false };

  @Output() rowClick = new EventEmitter<PatientDto>();
  @Output() editClick = new EventEmitter<PatientDto>();
  @Output() archiveClick = new EventEmitter<PatientDto>();
  @Output() sortChange = new EventEmitter<Sort>();

  protected readonly displayedColumns = [
    'mrn',
    'name',
    'age',
    'phone',
    'city',
    'status',
    'actions'
  ];

  protected ageOf(p: PatientDto): string {
    const age = ageFromBirthDate(p.birthDate);
    return age !== null ? `${age} ans` : '—';
  }

  protected civilityLabel(civility: string): string {
    const labels: Record<string, string> = {
      M: 'Monsieur',
      MME: 'Madame',
      MLLE: 'Mademoiselle',
      DR: 'Docteur',
      AUTRE: 'Autre'
    };
    return labels[civility] ?? civility;
  }

  protected onSort(sort: Sort): void {
    this.sortChange.emit(sort);
  }
}
