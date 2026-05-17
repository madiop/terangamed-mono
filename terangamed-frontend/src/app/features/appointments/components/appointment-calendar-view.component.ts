import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
  computed,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { CalendarEvent, CalendarModule, CalendarView } from 'angular-calendar';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatButtonModule } from '@angular/material/button';
import { addDays, addMonths, addWeeks, format, subDays, subMonths, subWeeks } from 'date-fns';
import { fr } from 'date-fns/locale';
import { AppointmentDto } from '@api/models/appointment.model';

/**
 * Vue calendrier multi-vues (jour / semaine / mois) des rendez-vous.
 *
 * <p>Utilise {@code angular-calendar} déjà installé (version date-fns).
 * Les events sont passés en {@link CalendarEvent[]} prêts à afficher
 * (mappés par le parent via {@code appointmentToCalendarEvent}).
 *
 * <h3>Navigation</h3>
 * Boutons Précédent / Aujourd'hui / Suivant + bascule jour/semaine/mois.
 * Émet {@code rangeChange} avec {fromDate, toDate} pour que le parent
 * recharge les RDV correspondant à la période visible.
 *
 * <h3>Click event</h3>
 * Click sur un event → émet {@code eventClick} avec le DTO original
 * (récupéré via meta).
 */
@Component({
  selector: 'tm-appointment-calendar-view',
  standalone: true,
  imports: [CommonModule, CalendarModule, MatButtonToggleModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="calendar-wrapper">
      <header class="calendar-header">
        <div class="nav-controls">
          <button mat-icon-button type="button" (click)="navigatePrevious()" aria-label="Précédent">
            <span class="material-icons-round">chevron_left</span>
          </button>
          <button mat-stroked-button type="button" (click)="navigateToday()">Aujourd'hui</button>
          <button mat-icon-button type="button" (click)="navigateNext()" aria-label="Suivant">
            <span class="material-icons-round">chevron_right</span>
          </button>
        </div>

        <h2 class="period-label">{{ periodLabel() }}</h2>

        <mat-button-toggle-group
          [value]="view()"
          (change)="onViewChange($event.value)"
          class="view-toggle"
        >
          <mat-button-toggle [value]="CV.Day">Jour</mat-button-toggle>
          <mat-button-toggle [value]="CV.Week">Semaine</mat-button-toggle>
          <mat-button-toggle [value]="CV.Month">Mois</mat-button-toggle>
        </mat-button-toggle-group>
      </header>

      <div class="calendar-body" [class.calendar-month]="view() === CV.Month">
        @if (view() === CV.Day) {
          <mwl-calendar-day-view
            [viewDate]="viewDate()"
            [events]="events"
            [hourSegments]="2"
            [dayStartHour]="8"
            [dayEndHour]="19"
            [locale]="locale"
            (eventClicked)="onEventClicked($event.event)"
          />
        } @else if (view() === CV.Week) {
          <mwl-calendar-week-view
            [viewDate]="viewDate()"
            [events]="events"
            [hourSegments]="2"
            [dayStartHour]="8"
            [dayEndHour]="19"
            [weekStartsOn]="1"
            [locale]="locale"
            (eventClicked)="onEventClicked($event.event)"
          />
        } @else {
          <mwl-calendar-month-view
            [viewDate]="viewDate()"
            [events]="events"
            [weekStartsOn]="1"
            [locale]="locale"
            (eventClicked)="onEventClicked($event.event)"
          />
        }
      </div>
    </div>
  `,
  styles: [
    `
      .calendar-wrapper {
        background: var(--color-surface);
        border-radius: var(--radius);
        box-shadow: var(--shadow);
        padding: 16px;
        display: flex;
        flex-direction: column;
        gap: 12px;
      }
      .calendar-header {
        display: flex;
        align-items: center;
        gap: 16px;
        flex-wrap: wrap;
      }
      .nav-controls {
        display: flex;
        align-items: center;
        gap: 4px;
      }
      .period-label {
        flex: 1;
        text-align: center;
        font-size: 16px;
        font-weight: 600;
        margin: 0;
        text-transform: capitalize;
      }
      .view-toggle {
        font-size: 13px;
      }
      ::ng-deep .view-toggle .mat-button-toggle-label-content {
        line-height: 32px;
        padding: 0 12px;
      }
      .calendar-body {
        min-height: 400px;
      }
      .calendar-month {
        min-height: 600px;
      }
    `
  ]
})
export class AppointmentCalendarViewComponent {
  @Input() events: CalendarEvent<AppointmentDto>[] = [];

  /** Vue courante — bidirectionnelle. */
  @Input() set initialView(v: CalendarView) {
    this._view.set(v);
  }

  @Input() set initialDate(d: Date) {
    this._viewDate.set(d);
  }

  @Output() rangeChange = new EventEmitter<{ fromDate: Date; toDate: Date }>();
  @Output() eventClick = new EventEmitter<AppointmentDto>();
  @Output() viewChange = new EventEmitter<CalendarView>();

  /** Code locale (string) attendu par angular-calendar — pas l'objet Locale de date-fns. */
  protected readonly locale = 'fr';

  /** Alias enum pour le template (Angular n'autorise pas les enums TS directement). */
  protected readonly CV = CalendarView;

  private readonly _view = signal<CalendarView>(CalendarView.Week);
  private readonly _viewDate = signal<Date>(new Date());

  protected readonly view = this._view.asReadonly();
  protected readonly viewDate = this._viewDate.asReadonly();

  /** Libellé de la période affichée — "Avril 2026" / "Sem. 16" / "lun. 4 mai". */
  protected readonly periodLabel = computed(() => {
    const d = this._viewDate();
    if (this._view() === CalendarView.Day) {
      return format(d, "EEEE d MMMM yyyy", { locale: fr });
    }
    if (this._view() === CalendarView.Week) {
      const week = format(d, 'I', { locale: fr });
      return `Semaine ${week} — ${format(d, 'MMMM yyyy', { locale: fr })}`;
    }
    return format(d, 'MMMM yyyy', { locale: fr });
  });

  protected onViewChange(v: CalendarView): void {
    this._view.set(v);
    this.viewChange.emit(v);
    this.emitRange();
  }

  protected navigatePrevious(): void {
    const v = this._view();
    const d = this._viewDate();
    this._viewDate.set(
      v === CalendarView.Day
        ? subDays(d, 1)
        : v === CalendarView.Week
          ? subWeeks(d, 1)
          : subMonths(d, 1)
    );
    this.emitRange();
  }

  protected navigateNext(): void {
    const v = this._view();
    const d = this._viewDate();
    this._viewDate.set(
      v === CalendarView.Day
        ? addDays(d, 1)
        : v === CalendarView.Week
          ? addWeeks(d, 1)
          : addMonths(d, 1)
    );
    this.emitRange();
  }

  protected navigateToday(): void {
    this._viewDate.set(new Date());
    this.emitRange();
  }

  protected onEventClicked(event: CalendarEvent): void {
    if (event.meta) {
      this.eventClick.emit(event.meta as AppointmentDto);
    }
  }

  /**
   * Calcule la fenêtre de dates couverte par la vue courante et l'émet au
   * parent. Permet au parent de recharger les RDV correspondant.
   */
  private emitRange(): void {
    const d = this._viewDate();
    const v = this._view();
    let from: Date;
    let to: Date;
    if (v === CalendarView.Day) {
      from = d;
      to = d;
    } else if (v === CalendarView.Week) {
      const dow = d.getDay() === 0 ? 6 : d.getDay() - 1; // weekStartsOn = 1 (lundi)
      from = subDays(d, dow);
      to = addDays(from, 6);
    } else {
      // month — début et fin du mois affichés (peut inclure jours du mois précédent/suivant)
      from = new Date(d.getFullYear(), d.getMonth(), 1);
      to = new Date(d.getFullYear(), d.getMonth() + 1, 0);
    }
    this.rangeChange.emit({ fromDate: from, toDate: to });
  }
}
