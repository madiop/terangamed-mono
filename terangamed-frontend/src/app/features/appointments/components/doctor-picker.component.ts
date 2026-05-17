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
import { DoctorApi } from '@api/doctor.api';
import { DoctorDto, Specialty } from '@api/models/doctor.model';

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
 * Picker médecin — mat-select chargé au mount avec les médecins ACTIFS.
 *
 * <p>Implémente {@link ControlValueAccessor} pour utilisation directe avec
 * {@code formControlName} en Reactive Forms.
 */
@Component({
  selector: 'tm-doctor-picker',
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
      useExisting: forwardRef(() => DoctorPickerComponent),
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
        @for (d of doctors(); track d.id) {
          <mat-option [value]="d.id">
            Dr {{ d.lastName | uppercase }} {{ d.firstName }}
            <span class="specialty-suffix">— {{ specialtyLabel(d.specialty) }}</span>
          </mat-option>
        }
      </mat-select>
      @if (loading()) {
        <mat-hint>Chargement…</mat-hint>
      }
      @if (showError) {
        <mat-error>Médecin obligatoire</mat-error>
      }
    </mat-form-field>
  `,
  styles: [
    `
      .full-width {
        width: 100%;
      }
      .specialty-suffix {
        color: var(--color-text-muted);
        font-size: 12px;
      }
    `
  ]
})
export class DoctorPickerComponent implements ControlValueAccessor, OnInit {
  @Input() label = 'Médecin';
  @Input() required = false;
  @Input() showError = false;

  private readonly api = inject(DoctorApi);

  protected readonly doctors = signal<ReadonlyArray<DoctorDto>>([]);
  protected readonly loading = signal(false);
  protected readonly disabled = signal(false);
  protected readonly ctrl = new FormControl<number | null>(null);

  private onChange: (value: number | null) => void = () => {};
  private onTouched: () => void = () => {};

  ngOnInit(): void {
    this.loadDoctors();
  }

  private loadDoctors(): void {
    this.loading.set(true);
    this.api.searchActive({}, { page: 0, size: 200, sort: 'lastName,asc' }).subscribe({
      next: (page) => {
        this.doctors.set(page.content);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }

  protected specialtyLabel(s: Specialty): string {
    return SPECIALTY_LABEL[s] ?? s;
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
