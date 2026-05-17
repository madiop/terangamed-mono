import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnInit,
  forwardRef,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ControlValueAccessor,
  FormControl,
  FormsModule,
  NG_VALUE_ACCESSOR,
  ReactiveFormsModule
} from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { PatientApi } from '@api/patient.api';
import { PatientDto } from '@api/models/patient.model';

/**
 * Picker patient — mat-select chargé au mount avec les patients ACTIFS.
 *
 * <p>Implémente {@link ControlValueAccessor} pour s'utiliser directement dans
 * un Reactive Form via {@code formControlName}.
 *
 * <p><b>Pragmatique pour le V1</b> : pour quelques dizaines de patients, un
 * mat-select suffit. À remplacer par un mat-autocomplete avec recherche
 * serveur quand le volume dépasse ~200 patients (problème de payload + UX).
 */
@Component({
  selector: 'tm-patient-picker',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatSelectModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => PatientPickerComponent),
      multi: true
    }
  ],
  template: `
    <mat-form-field appearance="outline" class="full-width">
      <mat-label>{{ label }}{{ required ? ' *' : '' }}</mat-label>
      <mat-select
        [formControl]="ctrl"
        [disabled]="disabled()"
        (selectionChange)="onSelectionChange($event.value)"
      >
        @for (p of patients(); track p.id) {
          <mat-option [value]="p.id">
            {{ p.lastName | uppercase }} {{ p.firstName }}
            <span class="mrn-suffix">— {{ p.medicalRecordNumber }}</span>
          </mat-option>
        }
      </mat-select>
      @if (loading()) {
        <mat-hint>Chargement…</mat-hint>
      }
      @if (showError) {
        <mat-error>Patient obligatoire</mat-error>
      }
    </mat-form-field>
  `,
  styles: [
    `
      .full-width {
        width: 100%;
      }
      .mrn-suffix {
        color: var(--color-text-muted);
        font-size: 12px;
      }
    `
  ]
})
export class PatientPickerComponent implements ControlValueAccessor, OnInit {
  @Input() label = 'Patient';
  @Input() required = false;
  @Input() showError = false;

  private readonly api = inject(PatientApi);

  protected readonly patients = signal<ReadonlyArray<PatientDto>>([]);
  protected readonly loading = signal(false);
  protected readonly disabled = signal(false);
  protected readonly ctrl = new FormControl<number | null>(null);

  private onChange: (value: number | null) => void = () => {};
  private onTouched: () => void = () => {};

  ngOnInit(): void {
    this.loadPatients();
  }

  private loadPatients(): void {
    this.loading.set(true);
    // Charge tous les patients ACTIFS, limite 500 — devrait suffire en V1.
    // À remplacer par un autocomplete avec recherche au-delà.
    this.api
      .search({ status: 'ACTIVE' }, { page: 0, size: 500, sort: 'lastName,asc' })
      .subscribe({
        next: (page) => {
          this.patients.set(page.content);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          // Pas de message UI — le mat-select reste vide, suffisamment explicite
        }
      });
  }

  protected onSelectionChange(value: number | null): void {
    this.onChange(value);
    this.onTouched();
  }

  // ─── ControlValueAccessor ───

  writeValue(value: number | null): void {
    this.ctrl.setValue(value, { emitEvent: false });
  }

  registerOnChange(fn: (value: number | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
    if (isDisabled) {
      this.ctrl.disable({ emitEvent: false });
    } else {
      this.ctrl.enable({ emitEvent: false });
    }
  }
}
