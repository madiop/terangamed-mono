import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  effect,
  inject
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { AuthService } from '@core/auth/auth.service';
import { CurrentDoctorService } from '@core/auth/current-doctor.service';
import { displayNameOf, TerangaMedRole } from '@core/auth/auth.types';
import { AppointmentDto } from '@api/models/appointment.model';
import { DashboardFacade, DashboardMode, DashboardScope } from './dashboard.facade';
import { KpiCardComponent } from './components/kpi-card.component';
import { NextAppointmentCardComponent } from './components/next-appointment-card.component';
import { WeekPlanningComponent } from './components/week-planning.component';
import { SelectedPatientCardComponent } from './components/selected-patient-card.component';
import { appointmentToCalendarEvent } from './components/appointment-event.mapper';

/**
 * Détermine le mode dashboard à appliquer selon les rôles du user connecté.
 *
 * <p><b>Priorité</b> : ADMIN > DOCTOR > RECEPTIONIST. Un user multi-rôles
 * (rare en pratique) verra la vue la plus "puissante" qu'il a — un admin qui
 * pratique aussi la médecine voit la vue ADMIN globale.
 */
function modeFor(roles: readonly TerangaMedRole[]): DashboardMode {
  if (roles.includes('ADMIN')) return 'ADMIN';
  if (roles.includes('DOCTOR')) return 'DOCTOR';
  if (roles.includes('RECEPTIONIST')) return 'RECEPTIONIST';
  return 'ADMIN'; // fallback prudent — vue globale en lecture
}

/**
 * Page Dashboard — KPI live + planning hebdomadaire + carte dossier patient.
 *
 * <h3>Variantes par rôle (étape 9.3e)</h3>
 * <ul>
 *   <li><b>ADMIN</b> : vision globale du cabinet (tous médecins, tous RDV)</li>
 *   <li><b>DOCTOR</b> : KPI et planning filtrés sur le médecin connecté.
 *       4ᵉ KPI = "Mon prochain RDV" au lieu de "Factures en attente"</li>
 *   <li><b>RECEPTIONIST</b> : vision globale, sans tabs Historique/
 *       Prescriptions/Documents sur la carte patient. 4ᵉ KPI = "RDV à
 *       confirmer" au lieu de "Consultations à finaliser"</li>
 * </ul>
 */
@Component({
  selector: 'tm-dashboard-page',
  standalone: true,
  imports: [
    CommonModule,
    PageHeaderComponent,
    KpiCardComponent,
    NextAppointmentCardComponent,
    WeekPlanningComponent,
    SelectedPatientCardComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dashboard-page">
      <tm-page-header
        title="Tableau de bord"
        [subtitle]="'Bienvenue, ' + displayName()"
      >
        <button
          type="button"
          class="btn btn-outline"
          (click)="refresh()"
          [disabled]="facade.anyLoading()"
        >
          <span
            class="material-icons-round"
            [class.spin]="facade.anyLoading()"
          >refresh</span>
          Rafraîchir
        </button>
      </tm-page-header>

      <!-- Banner d'avertissement si DOCTOR sans profil résolu -->
      @if (mode() === 'DOCTOR' && currentDoctor.notFound()) {
        <div class="warning-banner" role="alert">
          <span class="material-icons-round">info</span>
          <div>
            <strong>Profil médecin introuvable.</strong>
            <span>Vue globale par défaut. Demandez à un administrateur de créer votre profil dans Personnel.</span>
          </div>
        </div>
      }

      <section class="kpi-grid">
        @switch (mode()) {
          @case ('DOCTOR') {
            <tm-kpi-card
              label="Mes RDV du jour"
              icon="calendar_today"
              color="blue"
              [state]="facade.appointmentsToday()"
            />
            <tm-kpi-card
              label="Mes patients en attente"
              icon="people"
              color="orange"
              [state]="facade.patientsWaiting()"
            />
            <tm-kpi-card
              label="Mes consultations à finaliser"
              icon="medical_services"
              color="purple"
              [state]="facade.consultationsPending()"
            />
            <tm-next-appointment-card [state]="facade.nextAppointment()" />
          }
          @case ('RECEPTIONIST') {
            <tm-kpi-card
              label="Rendez-vous du jour"
              icon="calendar_today"
              color="blue"
              [state]="facade.appointmentsToday()"
            />
            <tm-kpi-card
              label="Patients en attente"
              icon="people"
              color="orange"
              [state]="facade.patientsWaiting()"
            />
            <tm-kpi-card
              label="RDV à confirmer"
              icon="event_note"
              color="purple"
              [state]="facade.plannedAppointments()"
            />
            <tm-kpi-card
              label="Factures en attente"
              icon="receipt_long"
              color="green"
              [state]="facade.pendingInvoices()"
            />
          }
          @default {
            <!-- ADMIN -->
            <tm-kpi-card
              label="Rendez-vous du jour"
              icon="calendar_today"
              color="blue"
              [state]="facade.appointmentsToday()"
            />
            <tm-kpi-card
              label="Patients en attente"
              icon="people"
              color="orange"
              [state]="facade.patientsWaiting()"
            />
            <tm-kpi-card
              label="Consultations à finaliser"
              icon="medical_services"
              color="purple"
              [state]="facade.consultationsPending()"
            />
            <tm-kpi-card
              label="Factures en attente"
              icon="receipt_long"
              color="green"
              [state]="facade.pendingInvoices()"
            />
          }
        }
      </section>

      <section class="dashboard-body">
        <tm-week-planning
          [events]="weekEvents()"
          [loading]="facade.weekPlanning().loading"
          [error]="facade.weekPlanning().error"
          (weekChange)="onWeekChange($event)"
          (eventClick)="onAppointmentClick($event)"
        />

        <tm-selected-patient-card
          [patientId]="facade.selectedPatientId()"
          [restrictedView]="mode() === 'RECEPTIONIST'"
        />
      </section>
    </div>
  `,
  styles: [
    `
      .kpi-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
        gap: 16px;
        margin-bottom: 24px;
      }
      .dashboard-body {
        display: grid;
        grid-template-columns: 2fr 1fr;
        gap: 16px;

        @media (max-width: 1100px) {
          grid-template-columns: 1fr;
        }
      }
      .warning-banner {
        display: flex;
        align-items: flex-start;
        gap: 12px;
        padding: 12px 16px;
        margin-bottom: 16px;
        background: #fef3c7;
        border-left: 4px solid #f59e0b;
        border-radius: var(--radius);
        color: #78350f;
      }
      .warning-banner .material-icons-round {
        font-size: 22px;
        color: #d97706;
        flex-shrink: 0;
      }
      .warning-banner div {
        display: flex;
        flex-direction: column;
        gap: 2px;
      }
      .warning-banner strong {
        font-weight: 600;
      }
      .warning-banner span:not(.material-icons-round) {
        font-size: 13px;
      }
      .spin {
        animation: tm-spin 0.9s linear infinite;
      }
      @keyframes tm-spin {
        from { transform: rotate(0deg); }
        to   { transform: rotate(360deg); }
      }
    `
  ]
})
export class DashboardPageComponent implements OnInit {
  private readonly auth = inject(AuthService);
  protected readonly currentDoctor = inject(CurrentDoctorService);
  protected readonly facade = inject(DashboardFacade);

  readonly displayName = computed(() => displayNameOf(this.auth.currentUser()));

  /** Mode dashboard dérivé des rôles JWT — recalculé à chaque changement de user. */
  readonly mode = computed<DashboardMode>(() => modeFor(this.auth.roles()));

  /** Convertit les RDV chargés par la facade en CalendarEvent pour le planning. */
  readonly weekEvents = computed(() =>
    this.facade.weekPlanning().events.map(appointmentToCalendarEvent)
  );

  /**
   * Effect qui (re)déclenche un refresh quand le scope change.
   *
   * <p>Pour le mode DOCTOR, on attend que la résolution du profil soit
   * terminée (RESOLVED/NOT_FOUND/ERROR) avant de refresher — sinon on
   * lancerait un premier refresh inutile avec doctorIdFilter=null, suivi
   * d'un second avec l'ID résolu (race + double charge réseau).
   */
  private readonly scopeEffect = effect(() => {
    const mode = this.mode();
    const status = this.currentDoctor.state().status;

    // Bloque le refresh tant que la résolution DOCTOR n'a pas convergé.
    if (mode === 'DOCTOR' && (status === 'IDLE' || status === 'LOADING')) {
      return;
    }

    const scope: DashboardScope = {
      mode,
      doctorIdFilter: mode === 'DOCTOR' ? this.currentDoctor.myDoctorId() : null
    };
    this.facade.refresh(scope);
  });

  ngOnInit(): void {
    // Pour le mode DOCTOR, on déclenche la résolution du profil. Le scopeEffect
    // se réveillera ensuite automatiquement quand le statut passera à
    // RESOLVED/NOT_FOUND/ERROR. Pour ADMIN/RECEPTIONIST, l'effect tire directement.
    if (this.mode() === 'DOCTOR') {
      this.currentDoctor.resolve().subscribe();
    }
  }

  refresh(): void {
    const mode = this.mode();
    const scope: DashboardScope = {
      mode,
      doctorIdFilter: mode === 'DOCTOR' ? this.currentDoctor.myDoctorId() : null
    };
    this.facade.refresh(scope);
  }

  onWeekChange(date: Date): void {
    this.facade.loadWeek(date);
  }

  /**
   * Click sur un RDV → sélectionne le patient pour la carte de droite.
   * <p>Pas de navigation : l'utilisateur reste sur le dashboard et voit le
   * dossier complet via les tabs. Pour aller au détail RDV, le composant
   * patient-card a un bouton "Voir détails".
   */
  onAppointmentClick(appointment: AppointmentDto): void {
    this.facade.selectPatient(appointment.patientId);
  }
}
