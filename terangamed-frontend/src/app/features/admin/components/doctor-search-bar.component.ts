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
import { MatButtonModule } from '@angular/material/button';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import {
  DoctorSearchCriteria,
  DoctorStatus,
  Specialty
} from '@api/models/doctor.model';

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

/**
 * Barre de recherche + filtres avancés (collapsibles) pour la liste médecins.
 *
 * <h3>Comportement</h3>
 * <ul>
 *   <li>Recherche sur {@code lastName} avec debounce 300ms</li>
 *   <li>Filtres avancés visibles via toggle "Filtres" — émettent à chaque changement</li>
 *   <li>{@code initialCriteria} permet de pré-remplir depuis les query params (URL stateful)</li>
 * </ul>
 *
 * <p>Les changements émettent {@link DoctorSearchCriteria} consolidé via
 * {@code criteriaChange}. Le parent reste maître de la logique d'appel API.
 */
@Component({
  selector: 'tm-doctor-search-bar',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form [formGroup]="form" class="search-bar">
      <div class="search-row">
        <mat-form-field appearance="outline" class="search-input">
          <mat-label>Rechercher par nom</mat-label>
          <input matInput formControlName="lastName" placeholder="Sow, Diop…" />
          <span matSuffix class="material-icons-round">search</span>
        </mat-form-field>

        <button
          mat-stroked-button
          type="button"
          class="filters-toggle"
          (click)="toggleFilters()"
        >
          <span class="material-icons-round">tune</span>
          Filtres
          @if (activeFiltersCount() > 0) {
            <span class="filters-count">{{ activeFiltersCount() }}</span>
          }
        </button>

        @if (hasAnyFilter()) {
          <button mat-button type="button" (click)="clearAll()">
            <span class="material-icons-round">clear</span>
            Réinitialiser
          </button>
        }
      </div>

      @if (filtersOpen) {
        <div class="filters-grid">
          <mat-form-field appearance="outline">
            <mat-label>Statut</mat-label>
            <mat-select formControlName="status">
              <mat-option [value]="''">Tous</mat-option>
              <mat-option value="ACTIVE">En activité</mat-option>
              <mat-option value="ON_LEAVE">En congé</mat-option>
              <mat-option value="RETIRED">Retraité</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Spécialité</mat-label>
            <mat-select formControlName="specialty">
              <mat-option [value]="''">Toutes</mat-option>
              @for (key of specialtyKeys; track key) {
                <mat-option [value]="key">{{ specialtyLabel(key) }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>N° licence</mat-label>
            <input matInput formControlName="licenseNumber" placeholder="MED-XXXXXX" />
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Email</mat-label>
            <input matInput formControlName="email" placeholder="exemple@terangamed.sn" />
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Exp. min. (années)</mat-label>
            <input
              matInput
              type="number"
              min="0"
              max="70"
              formControlName="minYearsOfExperience"
              placeholder="0"
            />
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Tarif max. (XOF)</mat-label>
            <input
              matInput
              type="number"
              min="0"
              step="1000"
              formControlName="maxConsultationFee"
              placeholder="50000"
            />
          </mat-form-field>
        </div>
      }
    </form>
  `,
  styles: [
    `
      .search-bar {
        display: flex;
        flex-direction: column;
        gap: 12px;
        background: var(--color-surface);
        padding: 16px;
        border-radius: var(--radius);
        box-shadow: var(--shadow);
      }
      .search-row {
        display: flex;
        align-items: center;
        gap: 12px;
        flex-wrap: wrap;
      }
      .search-input {
        flex: 1 1 300px;
      }
      .filters-toggle {
        display: inline-flex;
        align-items: center;
        gap: 6px;
      }
      .filters-toggle .material-icons-round {
        font-size: 18px;
      }
      .filters-count {
        background: var(--color-primary, #2963b0);
        color: #fff;
        border-radius: 10px;
        padding: 1px 8px;
        font-size: 11px;
        font-weight: 700;
      }
      .filters-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
        gap: 12px;
      }
      ::ng-deep .search-bar .mat-mdc-form-field {
        font-size: 14px;
      }
    `
  ]
})
export class DoctorSearchBarComponent implements OnInit {
  /**
   * Critères initiaux — utilisés au mount pour pré-remplir le formulaire
   * depuis les query params de l'URL (state-aware).
   */
  @Input() initialCriteria: DoctorSearchCriteria = {};

  /** Émis à chaque changement (avec debounce 300ms). */
  @Output() criteriaChange = new EventEmitter<DoctorSearchCriteria>();

  protected filtersOpen = false;
  protected readonly specialtyKeys: Specialty[] = [
    'GENERAL_MEDICINE',
    'CARDIOLOGY',
    'DERMATOLOGY',
    'PEDIATRICS',
    'GYNECOLOGY',
    'DENTISTRY',
    'OPHTHALMOLOGY',
    'PSYCHIATRY',
    'ORTHOPEDICS',
    'OTHER'
  ];

  protected readonly form: FormGroup;

  private readonly fb = inject(FormBuilder);

  constructor() {
    this.form = this.fb.group({
      lastName: [''],
      status: [''],
      specialty: [''],
      licenseNumber: [''],
      email: [''],
      minYearsOfExperience: [null as number | null],
      maxConsultationFee: [null as number | null]
    });
  }

  ngOnInit(): void {
    this.form.patchValue(
      {
        lastName: this.initialCriteria.lastName ?? '',
        status: this.initialCriteria.status ?? '',
        specialty: this.initialCriteria.specialty ?? '',
        licenseNumber: this.initialCriteria.licenseNumber ?? '',
        email: this.initialCriteria.email ?? '',
        minYearsOfExperience: this.initialCriteria.minYearsOfExperience ?? null,
        maxConsultationFee: this.initialCriteria.maxConsultationFee ?? null
      },
      { emitEvent: false }
    );

    if (this.activeFiltersCount() > 0) {
      this.filtersOpen = true;
    }

    this.form.valueChanges
      .pipe(
        debounceTime(300),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b))
      )
      .subscribe((value) => {
        this.criteriaChange.emit(this.toCriteria(value));
      });
  }

  toggleFilters(): void {
    this.filtersOpen = !this.filtersOpen;
  }

  clearAll(): void {
    this.form.reset(
      {
        lastName: '',
        status: '',
        specialty: '',
        licenseNumber: '',
        email: '',
        minYearsOfExperience: null,
        maxConsultationFee: null
      },
      { emitEvent: true }
    );
  }

  protected specialtyLabel(s: Specialty): string {
    return SPECIALTY_LABEL[s] ?? s;
  }

  /**
   * Compte les filtres actifs (hors champ recherche) — affiché sur le badge
   * du bouton "Filtres".
   */
  protected activeFiltersCount(): number {
    const v = this.form.value;
    let n = 0;
    if (v.status) n++;
    if (v.specialty) n++;
    if (v.licenseNumber) n++;
    if (v.email) n++;
    if (v.minYearsOfExperience != null && v.minYearsOfExperience !== '') n++;
    if (v.maxConsultationFee != null && v.maxConsultationFee !== '') n++;
    return n;
  }

  protected hasAnyFilter(): boolean {
    return !!this.form.value.lastName || this.activeFiltersCount() > 0;
  }

  /**
   * Convertit la valeur du formulaire en {@link DoctorSearchCriteria} en
   * filtrant les chaînes vides et en convertissant les number nullables.
   * Le backend interprète les params absents comme "pas de filtre"
   * (cohérent avec DoctorSpecifications côté Spring Data).
   */
  private toCriteria(value: Record<string, unknown>): DoctorSearchCriteria {
    const out: DoctorSearchCriteria = {};
    if (value['lastName']) out.lastName = String(value['lastName']).trim();
    if (value['status']) out.status = value['status'] as DoctorStatus;
    if (value['specialty']) out.specialty = value['specialty'] as Specialty;
    if (value['licenseNumber']) out.licenseNumber = String(value['licenseNumber']).trim();
    if (value['email']) out.email = String(value['email']).trim();

    const minExp = value['minYearsOfExperience'];
    if (minExp != null && minExp !== '' && !Number.isNaN(Number(minExp))) {
      out.minYearsOfExperience = Number(minExp);
    }
    const maxFee = value['maxConsultationFee'];
    if (maxFee != null && maxFee !== '' && !Number.isNaN(Number(maxFee))) {
      out.maxConsultationFee = Number(maxFee);
    }
    return out;
  }
}
