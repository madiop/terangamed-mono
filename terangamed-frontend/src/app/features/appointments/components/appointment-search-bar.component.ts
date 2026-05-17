import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnInit,
  Output,
  inject
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatButtonModule } from '@angular/material/button';
import { provideDateFnsAdapter } from '@angular/material-date-fns-adapter';
import { MAT_DATE_LOCALE } from '@angular/material/core';
import { fr } from 'date-fns/locale';
import { addDays, endOfMonth, endOfWeek, startOfMonth, startOfWeek } from 'date-fns';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import {
  AppointmentSearchCriteria,
  AppointmentStatus
} from '@api/models/appointment.model';
import { DoctorDto } from '@api/models/doctor.model';
import { toLocalDateString } from '@shared/utils/date.utils';

type DatePreset = 'ALL' | 'TODAY' | 'WEEK' | 'MONTH' | 'CUSTOM';

/**
 * Barre de recherche + filtres avancés pour la liste des rendez-vous.
 *
 * <h3>Filtres exposés</h3>
 * <ul>
 *   <li>Statut (multi-select implicite via mat-select simple)</li>
 *   <li>Médecin (mat-select rempli par {@code activeDoctors} en input)</li>
 *   <li>Période — raccourcis Aujourd'hui / Cette semaine / Ce mois /
 *       Personnalisé (datepicker fromDate + toDate)</li>
 * </ul>
 *
 * <p>Émet {@code criteriaChange} avec un debounce 300ms sur les changements
 * de date custom — pas de débounce sur les selects (instantané).
 */
@Component({
  selector: 'tm-appointment-search-bar',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatButtonModule
  ],
  providers: [
    provideDateFnsAdapter(),
    { provide: MAT_DATE_LOCALE, useValue: fr }
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form [formGroup]="form" class="search-bar">
      <div class="filters-row">
        <!-- Préset période -->
        <mat-form-field appearance="outline">
          <mat-label>Période</mat-label>
          <mat-select formControlName="datePreset">
            <mat-option value="ALL">Tous</mat-option>
            <mat-option value="TODAY">Aujourd'hui</mat-option>
            <mat-option value="WEEK">Cette semaine</mat-option>
            <mat-option value="MONTH">Ce mois</mat-option>
            <mat-option value="CUSTOM">Personnalisé</mat-option>
          </mat-select>
        </mat-form-field>

        @if (form.get('datePreset')?.value === 'CUSTOM') {
          <mat-form-field appearance="outline">
            <mat-label>Du</mat-label>
            <input matInput [matDatepicker]="fromPicker" formControlName="fromDate" />
            <mat-datepicker-toggle matIconSuffix [for]="fromPicker" />
            <mat-datepicker #fromPicker />
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Au</mat-label>
            <input matInput [matDatepicker]="toPicker" formControlName="toDate" />
            <mat-datepicker-toggle matIconSuffix [for]="toPicker" />
            <mat-datepicker #toPicker />
          </mat-form-field>
        }

        <!-- Statut -->
        <mat-form-field appearance="outline">
          <mat-label>Statut</mat-label>
          <mat-select formControlName="status">
            <mat-option [value]="''">Tous</mat-option>
            <mat-option value="PLANNED">Planifié</mat-option>
            <mat-option value="CONFIRMED">Confirmé</mat-option>
            <mat-option value="COMPLETED">Terminé</mat-option>
            <mat-option value="CANCELLED">Annulé</mat-option>
            <mat-option value="NO_SHOW">Absent</mat-option>
          </mat-select>
        </mat-form-field>

        <!-- Médecin -->
        <mat-form-field appearance="outline" class="doctor-select">
          <mat-label>Médecin</mat-label>
          <mat-select formControlName="doctorId">
            <mat-option [value]="''">Tous</mat-option>
            @for (d of activeDoctors; track d.id) {
              <mat-option [value]="d.id">
                Dr {{ d.lastName | uppercase }} {{ d.firstName }}
              </mat-option>
            }
          </mat-select>
        </mat-form-field>

        @if (hasAnyFilter()) {
          <button mat-button type="button" (click)="clearAll()">
            <span class="material-icons-round">clear</span>
            Réinitialiser
          </button>
        }
      </div>
    </form>
  `,
  styles: [
    `
      .search-bar {
        background: var(--color-surface);
        padding: 16px;
        border-radius: var(--radius);
        box-shadow: var(--shadow);
      }
      .filters-row {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
        gap: 12px;
        align-items: center;
      }
      .doctor-select {
        grid-column: span 2;
        @media (max-width: 700px) {
          grid-column: span 1;
        }
      }
      ::ng-deep .search-bar .mat-mdc-form-field {
        font-size: 14px;
      }
    `
  ]
})
export class AppointmentSearchBarComponent implements OnInit {
  /** Critères initiaux — utilisés au mount pour pré-remplir depuis l'URL. */
  @Input() initialCriteria: AppointmentSearchCriteria = {};

  /** Liste des médecins ACTIFS pour le select — chargée par le parent. */
  @Input() activeDoctors: ReadonlyArray<DoctorDto> = [];

  @Output() criteriaChange = new EventEmitter<AppointmentSearchCriteria>();

  protected readonly form: FormGroup;

  private readonly fb = inject(FormBuilder);

  constructor() {
    this.form = this.fb.group({
      // Défaut "Tous" — sinon les seeds de RDV en dehors de la semaine
      // courante seraient invisibles au premier chargement.
      datePreset: ['ALL' as DatePreset],
      fromDate: [null as Date | null],
      toDate: [null as Date | null],
      status: [''],
      doctorId: ['']
    });
  }

  ngOnInit(): void {
    // Pré-remplit depuis les query params — détecte si les dates correspondent
    // à un préset connu, sinon CUSTOM.
    const preset = this.detectPreset(this.initialCriteria);
    this.form.patchValue(
      {
        datePreset: preset,
        fromDate: this.initialCriteria.fromDate
          ? new Date(this.initialCriteria.fromDate)
          : null,
        toDate: this.initialCriteria.toDate ? new Date(this.initialCriteria.toDate) : null,
        status: this.initialCriteria.status ?? '',
        doctorId: this.initialCriteria.doctorId ?? ''
      },
      { emitEvent: false }
    );

    this.form.valueChanges
      .pipe(
        debounceTime(300),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b))
      )
      .subscribe((value) => {
        this.criteriaChange.emit(this.toCriteria(value));
      });
  }

  protected hasAnyFilter(): boolean {
    const v = this.form.value;
    return !!(
      v.status ||
      v.doctorId ||
      (v.datePreset === 'CUSTOM' && (v.fromDate || v.toDate))
    );
  }

  protected clearAll(): void {
    this.form.reset(
      { datePreset: 'ALL', fromDate: null, toDate: null, status: '', doctorId: '' },
      { emitEvent: true }
    );
  }

  /**
   * Détermine le préset à appliquer en regardant si les dates correspondent
   * à un raccourci connu (today / week / month). Sinon CUSTOM.
   * Si aucune date n'est fournie → ALL (pas de filtre).
   */
  private detectPreset(c: AppointmentSearchCriteria): DatePreset {
    if (!c.fromDate && !c.toDate) return 'ALL';
    const today = toLocalDateString(new Date());
    if (c.fromDate === today && c.toDate === today) return 'TODAY';
    const monday = toLocalDateString(startOfWeek(new Date(), { weekStartsOn: 1 }));
    const sunday = toLocalDateString(addDays(new Date(monday), 6));
    if (c.fromDate === monday && c.toDate === sunday) return 'WEEK';
    const start = toLocalDateString(startOfMonth(new Date()));
    const end = toLocalDateString(endOfMonth(new Date()));
    if (c.fromDate === start && c.toDate === end) return 'MONTH';
    return 'CUSTOM';
  }

  /**
   * Convertit l'état du formulaire en {@link AppointmentSearchCriteria}.
   * Pour les présets, calcule fromDate/toDate à la volée selon la date du jour.
   */
  private toCriteria(value: Record<string, unknown>): AppointmentSearchCriteria {
    const out: AppointmentSearchCriteria = {};
    const preset = value['datePreset'] as DatePreset;
    const today = new Date();

    if (preset === 'TODAY') {
      const d = toLocalDateString(today);
      out.fromDate = d;
      out.toDate = d;
    } else if (preset === 'WEEK') {
      const monday = startOfWeek(today, { weekStartsOn: 1 });
      const sunday = endOfWeek(today, { weekStartsOn: 1 });
      out.fromDate = toLocalDateString(monday);
      out.toDate = toLocalDateString(sunday);
    } else if (preset === 'MONTH') {
      out.fromDate = toLocalDateString(startOfMonth(today));
      out.toDate = toLocalDateString(endOfMonth(today));
    } else if (preset === 'CUSTOM') {
      if (value['fromDate'] instanceof Date) out.fromDate = toLocalDateString(value['fromDate'] as Date);
      if (value['toDate'] instanceof Date) out.toDate = toLocalDateString(value['toDate'] as Date);
    }

    if (value['status']) out.status = value['status'] as AppointmentStatus;
    if (value['doctorId']) out.doctorId = Number(value['doctorId']);

    return out;
  }
}
