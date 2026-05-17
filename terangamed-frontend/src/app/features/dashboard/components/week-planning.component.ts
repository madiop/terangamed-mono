import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { CalendarCommonModule, CalendarEvent, CalendarWeekModule } from 'angular-calendar';
import { addDays, format } from 'date-fns';
import { fr } from 'date-fns/locale';
import { AppointmentDto } from '@api/models/appointment.model';

/**
 * Planning hebdomadaire — vue calendrier des RDV.
 *
 * <p>Composant de présentation : reçoit les events déjà mappés par le parent
 * (via {@code appointmentToCalendarEvent}) et émet les clics utilisateur.
 * La récupération des données est dans {@code DashboardFacade}.
 *
 * <h3>Inputs</h3>
 * <ul>
 *   <li>{@code events} : événements à afficher (semaine courante)</li>
 *   <li>{@code loading} : skeleton / spinner pendant chargement</li>
 *   <li>{@code error} : message d'erreur (affiché en lieu et place du calendar)</li>
 * </ul>
 *
 * <h3>Outputs</h3>
 * <ul>
 *   <li>{@code weekChange(Date)} : nouvelle date du début de semaine sélectionné</li>
 *   <li>{@code eventClick(AppointmentDto)} : RDV cliqué (sera relié à /appointments/:id en 9.5)</li>
 * </ul>
 */
@Component({
  selector: 'tm-week-planning',
  standalone: true,
  imports: [CommonModule, CalendarCommonModule, CalendarWeekModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="card week-planning">
      <header class="planning-header">
        <div class="header-left">
          <h2 class="planning-title">Planning des Rendez-vous</h2>
          <p class="planning-subtitle">{{ weekLabel() }}</p>
        </div>
        <div class="header-actions">
          <button type="button" class="nav-btn" (click)="goPrevious()" aria-label="Semaine précédente">
            <span class="material-icons-round">chevron_left</span>
          </button>
          <button type="button" class="nav-btn today-btn" (click)="goToday()">
            Aujourd'hui
          </button>
          <button type="button" class="nav-btn" (click)="goNext()" aria-label="Semaine suivante">
            <span class="material-icons-round">chevron_right</span>
          </button>
        </div>
      </header>

      <div class="planning-body">
        @if (error) {
          <div class="planning-error">
            <span class="material-icons-round">error_outline</span>
            <span>{{ error }}</span>
          </div>
        } @else if (loading) {
          <div class="planning-skeleton" aria-busy="true">
            <span class="material-icons-round spin">progress_activity</span>
            Chargement du planning…
          </div>
        } @else {
          <mwl-calendar-week-view
            [viewDate]="viewDate()"
            [events]="events"
            [locale]="locale"
            [hourSegments]="2"
            [dayStartHour]="8"
            [dayEndHour]="19"
            [weekStartsOn]="1"
            (eventClicked)="onEventClicked($event.event)"
          />
        }
      </div>
    </article>
  `,
  styleUrl: './week-planning.component.scss'
})
export class WeekPlanningComponent {
  /**
   * Événements à afficher dans la vue.
   * <p>Type mutable car {@code mwl-calendar-week-view} attend {@code CalendarEvent[]}
   * (la lib peut muter en interne pour le drag-and-drop). Le parent doit passer
   * un tableau frais à chaque rendu (via {@code computed()}).
   */
  @Input() events: CalendarEvent<AppointmentDto>[] = [];
  @Input() loading = false;
  @Input() error: string | null = null;

  /** Locale date-fns pour les labels (jours, mois) — utilisée par le composant calendar. */
  protected readonly locale = 'fr';

  /** Date de référence — n'importe quel jour DANS la semaine affichée. */
  protected readonly viewDate = signal(new Date());

  @Output() weekChange = new EventEmitter<Date>();
  @Output() eventClick = new EventEmitter<AppointmentDto>();

  protected weekLabel(): string {
    const date = this.viewDate();
    return format(date, "'Semaine du' dd MMMM yyyy", { locale: fr });
  }

  protected goPrevious(): void {
    const next = addDays(this.viewDate(), -7);
    this.viewDate.set(next);
    this.weekChange.emit(next);
  }

  protected goNext(): void {
    const next = addDays(this.viewDate(), 7);
    this.viewDate.set(next);
    this.weekChange.emit(next);
  }

  protected goToday(): void {
    const today = new Date();
    this.viewDate.set(today);
    this.weekChange.emit(today);
  }

  protected onEventClicked(event: CalendarEvent<AppointmentDto>): void {
    if (event.meta) {
      this.eventClick.emit(event.meta);
    }
  }
}
