import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTabsModule } from '@angular/material/tabs';
import { Sort } from '@angular/material/sort';
import { CalendarView } from 'angular-calendar';
// CalendarView est un enum string : Day='day', Week='week', Month='month'
import { Subject, takeUntil } from 'rxjs';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { AuthService } from '@core/auth/auth.service';
import { CurrentDoctorService } from '@core/auth/current-doctor.service';
import {
  AppointmentDto,
  AppointmentSearchCriteria,
  AppointmentStatus
} from '@api/models/appointment.model';
import { DoctorDto } from '@api/models/doctor.model';
import { DoctorApi } from '@api/doctor.api';
import { PageRequest } from '@api/common.types';
import { toLocalDateString } from '@shared/utils/date.utils';
import { AppointmentFacade } from '../appointment.facade';
import { AppointmentSearchBarComponent } from '../components/appointment-search-bar.component';
import {
  AppointmentTableComponent,
  AppointmentTablePermissions
} from '../components/appointment-table.component';
import { AppointmentCalendarViewComponent } from '../components/appointment-calendar-view.component';
import { appointmentToCalendarEvent } from '@features/dashboard/components/appointment-event.mapper';

const VALID_STATUSES: readonly AppointmentStatus[] = [
  'PLANNED',
  'CONFIRMED',
  'COMPLETED',
  'CANCELLED',
  'NO_SHOW'
];
const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

type ViewMode = 'list' | 'calendar';

/**
 * Page liste rendez-vous avec bascule liste/calendrier.
 *
 * <h3>URL stateful</h3>
 * Tous les filtres + la pagination + tri + vue (list|calendar) sont reflétés
 * dans la query string : {@code /appointments?view=calendar&status=CONFIRMED&fromDate=2026-05-04}.
 *
 * <h3>Permissions</h3>
 * <ul>
 *   <li>Vue : tous les rôles authentifiés</li>
 *   <li>Création : ADMIN/DOCTOR/RECEPTIONIST</li>
 *   <li>Transitions état : selon le rôle (voir AppointmentTablePermissions)</li>
 * </ul>
 *
 * <h3>Filtrage automatique pour DOCTOR</h3>
 * Si l'utilisateur connecté est DOCTOR (et pas ADMIN), on filtre par défaut
 * sur ses propres RDV via {@link CurrentDoctorService}.
 */
@Component({
  selector: 'tm-appointments-list-page',
  standalone: true,
  imports: [
    CommonModule,
    PageHeaderComponent,
    AppointmentSearchBarComponent,
    AppointmentTableComponent,
    AppointmentCalendarViewComponent,
    MatPaginatorModule,
    MatTabsModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="appointments-list-page">
      <tm-page-header title="Rendez-vous" [subtitle]="totalLabel()">
        @if (canCreate()) {
          <button type="button" class="btn btn-primary" (click)="goToCreate()">
            <span class="material-icons-round">add</span>
            Nouveau rendez-vous
          </button>
        }
      </tm-page-header>

      <!-- Bascule list/calendar -->
      <mat-tab-group
        [selectedIndex]="viewMode() === 'list' ? 0 : 1"
        (selectedIndexChange)="onViewModeChange($event)"
        animationDuration="200ms"
      >
        <mat-tab label="Liste" />
        <mat-tab label="Calendrier" />
      </mat-tab-group>

      <tm-appointment-search-bar
        [initialCriteria]="initialCriteria"
        [activeDoctors]="activeDoctors()"
        (criteriaChange)="onCriteriaChange($event)"
      />

      @if (facade.list().error) {
        <div class="error-banner" role="alert">
          <span class="material-icons-round">error_outline</span>
          <span>{{ facade.list().error }}</span>
          <button type="button" class="btn btn-link" (click)="facade.refresh()">
            Réessayer
          </button>
        </div>
      }

      @if (viewMode() === 'list') {
        <tm-appointment-table
          [appointments]="facade.list().appointments"
          [loading]="facade.list().loading"
          [permissions]="permissions()"
          (rowClick)="goToDetail($event)"
          (editClick)="goToEdit($event)"
          (confirmClick)="onConfirm($event)"
          (completeClick)="onComplete($event)"
          (cancelClick)="onCancel($event)"
          (noShowClick)="onNoShow($event)"
          (sortChange)="onSortChange($event)"
        />

        <mat-paginator
          [length]="facade.list().totalElements"
          [pageSize]="facade.list().size"
          [pageIndex]="facade.list().page"
          [pageSizeOptions]="pageSizeOptions"
          (page)="onPageChange($event)"
          showFirstLastButtons
        />
      } @else {
        <tm-appointment-calendar-view
          [events]="calendarEvents()"
          [initialView]="calendarView()"
          [initialDate]="calendarDate()"
          (rangeChange)="onCalendarRangeChange($event)"
          (eventClick)="goToDetail($event)"
          (viewChange)="onCalendarViewChange($event)"
        />
      }
    </div>
  `,
  styles: [
    `
      .appointments-list-page {
        display: flex;
        flex-direction: column;
        gap: 16px;
      }
      .error-banner {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 12px 16px;
        background: #fee2e2;
        border-left: 4px solid #ef4444;
        border-radius: var(--radius);
        color: #991b1b;
      }
      .error-banner .btn-link {
        margin-left: auto;
        background: none;
        border: none;
        color: #991b1b;
        text-decoration: underline;
        cursor: pointer;
        font-weight: 600;
      }
      ::ng-deep .mat-mdc-paginator {
        background: var(--color-surface);
        border-radius: var(--radius);
      }
    `
  ]
})
export class AppointmentsListPageComponent implements OnInit, OnDestroy {
  protected readonly facade = inject(AppointmentFacade);
  private readonly auth = inject(AuthService);
  private readonly currentDoctor = inject(CurrentDoctorService);
  private readonly doctorApi = inject(DoctorApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly destroy$ = new Subject<void>();

  protected readonly pageSizeOptions = PAGE_SIZE_OPTIONS;
  protected readonly activeDoctors = signal<ReadonlyArray<DoctorDto>>([]);

  protected initialCriteria: AppointmentSearchCriteria = {};
  private currentCriteria: AppointmentSearchCriteria = {};
  private currentPageRequest: PageRequest = {
    page: 0,
    size: 20,
    sort: 'startTime,desc'
  };

  protected readonly viewMode = signal<ViewMode>('list');
  protected readonly calendarView = signal<CalendarView>(CalendarView.Week);
  protected readonly calendarDate = signal<Date>(new Date());

  protected readonly canCreate = computed(() =>
    this.auth.hasAnyRole(['ADMIN', 'DOCTOR', 'RECEPTIONIST'])
  );

  /**
   * Permissions table — combine les rôles avec la logique métier.
   * RECEPTIONIST ne peut pas "Terminer" un RDV (c'est l'acte du médecin).
   */
  protected readonly permissions = computed<AppointmentTablePermissions>(() => ({
    canEdit: this.auth.hasAnyRole(['ADMIN', 'DOCTOR', 'RECEPTIONIST']),
    canConfirm: this.auth.hasAnyRole(['ADMIN', 'DOCTOR', 'RECEPTIONIST']),
    canComplete: this.auth.hasAnyRole(['ADMIN', 'DOCTOR']),
    canCancel: this.auth.hasAnyRole(['ADMIN', 'DOCTOR', 'RECEPTIONIST']),
    canMarkNoShow: this.auth.hasAnyRole(['ADMIN', 'DOCTOR', 'RECEPTIONIST'])
  }));

  /** Conversion DTO → CalendarEvent pour la vue calendrier. */
  protected readonly calendarEvents = computed(() =>
    this.facade.list().appointments.map(appointmentToCalendarEvent)
  );

  protected readonly totalLabel = computed(() => {
    const total = this.facade.list().totalElements;
    if (this.facade.list().loading && total === 0) return 'Chargement…';
    return total === 0
      ? 'Aucun rendez-vous'
      : `${total} rendez-vous trouvé${total > 1 ? 's' : ''}`;
  });

  ngOnInit(): void {
    // Charge la liste des médecins ACTIFS pour le filtre
    this.doctorApi.searchActive({}, { page: 0, size: 200, sort: 'lastName,asc' }).subscribe({
      next: (page) => this.activeDoctors.set(page.content),
      error: () => {
        /* silencieux — le select restera vide */
      }
    });

    // Si user DOCTOR (et pas ADMIN), on charge son profil pour filtrer par défaut
    if (this.auth.hasAnyRole(['DOCTOR']) && !this.auth.hasAnyRole(['ADMIN'])) {
      this.currentDoctor.resolve().subscribe();
    }

    this.route.queryParamMap.pipe(takeUntil(this.destroy$)).subscribe((params) => {
      const view = params.get('view');
      this.viewMode.set(view === 'calendar' ? 'calendar' : 'list');

      const calView = params.get('calView');
      if (calView === CalendarView.Day) this.calendarView.set(CalendarView.Day);
      else if (calView === CalendarView.Week) this.calendarView.set(CalendarView.Week);
      else if (calView === CalendarView.Month) this.calendarView.set(CalendarView.Month);

      const calDate = params.get('calDate');
      if (calDate) {
        const d = new Date(calDate);
        if (!Number.isNaN(d.getTime())) {
          this.calendarDate.set(d);
        }
      }

      const criteria = this.parseCriteria(params);
      const pageRequest = this.parsePageRequest(params);

      // Pas de filtre de date par défaut en mode liste — l'utilisateur voit
      // tous les RDV au premier chargement et peut affiner via la search-bar.
      // En mode calendrier, le composant émet un range automatiquement à
      // l'init, qui ré-déclenchera une recherche avec fromDate/toDate.

      this.initialCriteria = criteria;
      this.currentCriteria = criteria;
      this.currentPageRequest = pageRequest;

      this.facade.search(criteria, pageRequest);
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ─── Handlers ───

  protected onCriteriaChange(criteria: AppointmentSearchCriteria): void {
    this.currentCriteria = criteria;
    this.currentPageRequest = { ...this.currentPageRequest, page: 0 };
    this.syncUrl();
  }

  protected onPageChange(event: PageEvent): void {
    this.currentPageRequest = {
      ...this.currentPageRequest,
      page: event.pageIndex,
      size: event.pageSize
    };
    this.syncUrl();
  }

  protected onSortChange(sort: Sort): void {
    const sortParam = sort.direction
      ? `${sort.active},${sort.direction}`
      : 'startTime,desc';
    this.currentPageRequest = {
      ...this.currentPageRequest,
      sort: sortParam,
      page: 0
    };
    this.syncUrl();
  }

  protected onViewModeChange(index: number): void {
    this.viewMode.set(index === 1 ? 'calendar' : 'list');
    this.syncUrl();
  }

  protected onCalendarViewChange(v: CalendarView): void {
    this.calendarView.set(v);
    this.syncUrl();
  }

  protected onCalendarRangeChange(range: { fromDate: Date; toDate: Date }): void {
    this.calendarDate.set(range.fromDate);
    this.currentCriteria = {
      ...this.currentCriteria,
      fromDate: toLocalDateString(range.fromDate),
      toDate: toLocalDateString(range.toDate)
    };
    // Calendrier — pas de pagination, on charge tous les RDV de la fenêtre
    this.currentPageRequest = { page: 0, size: 200, sort: 'startTime,asc' };
    this.syncUrl();
  }

  protected goToCreate(): void {
    void this.router.navigate(['/appointments/new']);
  }

  protected goToDetail(a: AppointmentDto): void {
    void this.router.navigate(['/appointments', a.id]);
  }

  protected goToEdit(a: AppointmentDto): void {
    void this.router.navigate(['/appointments', a.id, 'edit']);
  }

  // Transitions d'état déclenchées depuis la table — refresh après succès
  protected onConfirm(a: AppointmentDto): void {
    this.facade.confirm(a.id).subscribe({ next: () => this.facade.refresh() });
  }

  protected onComplete(a: AppointmentDto): void {
    this.facade.complete(a.id).subscribe({ next: () => this.facade.refresh() });
  }

  protected onCancel(a: AppointmentDto): void {
    // Confirmation simple via window.confirm — un dialog dédié sera ajouté
    // dans le composant détail. Pour la liste, on garde simple.
    if (confirm(`Annuler le rendez-vous de ${a.patientNameSnapshot} ?`)) {
      this.facade.cancel(a.id).subscribe({ next: () => this.facade.refresh() });
    }
  }

  protected onNoShow(a: AppointmentDto): void {
    if (confirm(`Marquer ${a.patientNameSnapshot} comme absent ?`)) {
      this.facade.markNoShow(a.id).subscribe({ next: () => this.facade.refresh() });
    }
  }

  // ─── URL stateful ───

  private syncUrl(): void {
    const queryParams: Record<string, string> = {
      ...this.criteriaToParams(this.currentCriteria),
      ...this.pageRequestToParams(this.currentPageRequest)
    };
    if (this.viewMode() === 'calendar') {
      queryParams['view'] = 'calendar';
      queryParams['calView'] = this.calendarView();
      queryParams['calDate'] = toLocalDateString(this.calendarDate());
    }
    void this.router.navigate([], { relativeTo: this.route, queryParams });
  }

  private criteriaToParams(c: AppointmentSearchCriteria): Record<string, string> {
    const out: Record<string, string> = {};
    if (c.status) out['status'] = c.status;
    if (c.doctorId !== undefined && c.doctorId !== null) {
      out['doctorId'] = String(c.doctorId);
    }
    if (c.patientId !== undefined && c.patientId !== null) {
      out['patientId'] = String(c.patientId);
    }
    if (c.fromDate) out['fromDate'] = c.fromDate;
    if (c.toDate) out['toDate'] = c.toDate;
    return out;
  }

  private pageRequestToParams(p: PageRequest): Record<string, string> {
    const out: Record<string, string> = {};
    if (p.page && p.page > 0) out['page'] = String(p.page);
    if (p.size && p.size !== 20) out['size'] = String(p.size);
    if (p.sort && p.sort !== 'startTime,desc') {
      out['sort'] = typeof p.sort === 'string' ? p.sort : p.sort.join(',');
    }
    return out;
  }

  private parseCriteria(params: { get: (k: string) => string | null }): AppointmentSearchCriteria {
    const out: AppointmentSearchCriteria = {};
    const status = params.get('status');
    if (status && (VALID_STATUSES as readonly string[]).includes(status)) {
      out.status = status as AppointmentStatus;
    }
    const doctorId = params.get('doctorId');
    if (doctorId && /^\d+$/.test(doctorId)) {
      out.doctorId = parseInt(doctorId, 10);
    }
    const patientId = params.get('patientId');
    if (patientId && /^\d+$/.test(patientId)) {
      out.patientId = parseInt(patientId, 10);
    }
    const fromDate = params.get('fromDate');
    if (fromDate && /^\d{4}-\d{2}-\d{2}$/.test(fromDate)) {
      out.fromDate = fromDate;
    }
    const toDate = params.get('toDate');
    if (toDate && /^\d{4}-\d{2}-\d{2}$/.test(toDate)) {
      out.toDate = toDate;
    }
    return out;
  }

  private parsePageRequest(params: { get: (k: string) => string | null }): PageRequest {
    const pageRaw = params.get('page');
    const sizeRaw = params.get('size');
    const sortRaw = params.get('sort');

    const page = pageRaw && /^\d+$/.test(pageRaw) ? parseInt(pageRaw, 10) : 0;
    const size =
      sizeRaw && PAGE_SIZE_OPTIONS.includes(parseInt(sizeRaw, 10))
        ? parseInt(sizeRaw, 10)
        : 20;
    const sort =
      sortRaw && /^[a-zA-Z]+,(asc|desc)$/.test(sortRaw) ? sortRaw : 'startTime,desc';

    return { page, size, sort };
  }
}
