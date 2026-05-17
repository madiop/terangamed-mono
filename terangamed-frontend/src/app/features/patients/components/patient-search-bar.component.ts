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
  BloodGroup,
  Gender,
  PatientSearchCriteria,
  PatientStatus
} from '@api/models/patient.model';

/**
 * Barre de recherche + filtres avancés (collapsibles) pour la liste patients.
 *
 * <h3>Comportement</h3>
 * <ul>
 *   <li>Recherche sur {@code lastName} avec debounce 300ms</li>
 *   <li>Filtres avancés visibles via toggle "Filtres" — émettent à chaque changement</li>
 *   <li>{@code initialCriteria} permet de pré-remplir depuis les query params</li>
 * </ul>
 *
 * <p>Les changements émettent {@code PatientSearchCriteria} consolidé via
 * {@code criteriaChange}. Le parent reste maître de la logique d'appel API.
 */
@Component({
  selector: 'tm-patient-search-bar',
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
          <input matInput formControlName="lastName" placeholder="Diop, Sall…" />
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
              <mat-option value="ACTIVE">Actif</mat-option>
              <mat-option value="INACTIVE">Inactif</mat-option>
              <mat-option value="ARCHIVED">Archivé</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Genre</mat-label>
            <mat-select formControlName="gender">
              <mat-option [value]="''">Tous</mat-option>
              <mat-option value="MALE">Homme</mat-option>
              <mat-option value="FEMALE">Femme</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Groupe sanguin</mat-label>
            <mat-select formControlName="bloodGroup">
              <mat-option [value]="''">Tous</mat-option>
              <mat-option value="A_POS">A+</mat-option>
              <mat-option value="A_NEG">A−</mat-option>
              <mat-option value="B_POS">B+</mat-option>
              <mat-option value="B_NEG">B−</mat-option>
              <mat-option value="AB_POS">AB+</mat-option>
              <mat-option value="AB_NEG">AB−</mat-option>
              <mat-option value="O_POS">O+</mat-option>
              <mat-option value="O_NEG">O−</mat-option>
              <mat-option value="UNKNOWN">Inconnu</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline">
            <mat-label>Ville</mat-label>
            <input matInput formControlName="city" placeholder="Dakar, Thiès…" />
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
        grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
        gap: 12px;
      }
      ::ng-deep .search-bar .mat-mdc-form-field {
        font-size: 14px;
      }
    `
  ]
})
export class PatientSearchBarComponent implements OnInit {
  /**
   * Critères initiaux — utilisés au mount pour pré-remplir le formulaire
   * depuis les query params de l'URL (state-aware).
   */
  @Input() initialCriteria: PatientSearchCriteria = {};

  /** Émis à chaque changement (avec debounce 300ms sur la recherche texte). */
  @Output() criteriaChange = new EventEmitter<PatientSearchCriteria>();

  protected filtersOpen = false;

  protected readonly form: FormGroup;

  private readonly fb = inject(FormBuilder);

  constructor() {
    this.form = this.fb.group({
      lastName: [''],
      status: [''],
      gender: [''],
      bloodGroup: [''],
      city: ['']
    });
  }

  ngOnInit(): void {
    // Pré-remplit depuis les query params (sans déclencher d'émission immédiate)
    this.form.patchValue(
      {
        lastName: this.initialCriteria.lastName ?? '',
        status: this.initialCriteria.status ?? '',
        gender: this.initialCriteria.gender ?? '',
        bloodGroup: this.initialCriteria.bloodGroup ?? '',
        city: this.initialCriteria.city ?? ''
      },
      { emitEvent: false }
    );

    // Si des filtres sont présents au chargement, on ouvre le panneau
    this.filtersOpen = this.activeFiltersCount() > 0;

    // Émet les changements avec debounce — ça évite de spammer l'API
    // pendant que l'utilisateur tape dans le champ "lastName".
    this.form.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)))
      .subscribe((value) => {
        this.criteriaChange.emit(this.toCriteria(value));
      });
  }

  toggleFilters(): void {
    this.filtersOpen = !this.filtersOpen;
  }

  clearAll(): void {
    this.form.reset(
      { lastName: '', status: '', gender: '', bloodGroup: '', city: '' },
      { emitEvent: true }
    );
  }

  /**
   * Compte les filtres actifs (hors champ recherche) — affiché sur le badge
   * du bouton "Filtres".
   */
  protected activeFiltersCount(): number {
    const v = this.form.value;
    return ['status', 'gender', 'bloodGroup', 'city'].filter((k) => !!v[k]).length;
  }

  protected hasAnyFilter(): boolean {
    const v = this.form.value;
    return (
      !!v.lastName ||
      !!v.status ||
      !!v.gender ||
      !!v.bloodGroup ||
      !!v.city
    );
  }

  /**
   * Convertit la valeur du formulaire en {@link PatientSearchCriteria} en
   * filtrant les chaînes vides. Le backend interprète les params absents
   * comme "pas de filtre" (cohérent avec PatientSpecifications).
   */
  private toCriteria(value: Record<string, unknown>): PatientSearchCriteria {
    const out: PatientSearchCriteria = {};
    if (value['lastName']) out.lastName = String(value['lastName']).trim();
    if (value['status']) out.status = value['status'] as PatientStatus;
    if (value['gender']) out.gender = value['gender'] as Gender;
    if (value['bloodGroup']) out.bloodGroup = value['bloodGroup'] as BloodGroup;
    if (value['city']) out.city = String(value['city']).trim();
    return out;
  }
}
