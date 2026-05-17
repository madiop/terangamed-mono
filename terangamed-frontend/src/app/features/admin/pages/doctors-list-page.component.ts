import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  computed,
  inject
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { Sort } from '@angular/material/sort';
import { Subject, takeUntil } from 'rxjs';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { AuthService } from '@core/auth/auth.service';
import {
  DoctorDto,
  DoctorSearchCriteria,
  DoctorStatus,
  Specialty
} from '@api/models/doctor.model';
import { PageRequest } from '@api/common.types';
import { DoctorFacade } from '../doctor.facade';
import { DoctorSearchBarComponent } from '../components/doctor-search-bar.component';
import {
  DoctorTableComponent,
  DoctorTablePermissions
} from '../components/doctor-table.component';

const VALID_STATUSES: readonly DoctorStatus[] = ['ACTIVE', 'ON_LEAVE', 'RETIRED'];
const VALID_SPECIALTIES: readonly Specialty[] = [
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
const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

/**
 * Page liste personnel médical — `/admin/staff`.
 *
 * <h3>URL stateful</h3>
 * Tous les filtres + pagination + tri sont reflétés dans la query string :
 * {@code /admin/staff?lastName=Sow&specialty=CARDIOLOGY&status=ACTIVE&page=2}.
 * Avantage : URL partageable, navigation back/forward fonctionnelle, refresh
 * F5 préserve l'état.
 *
 * <h3>Permissions</h3>
 * Module entier réservé ADMIN (cf. roleGuard sur la route parente
 * {@code /admin}). Pas besoin de re-vérifier finement les actions ici —
 * les boutons sont visibles si {@code auth.hasAnyRole(['ADMIN'])}.
 *
 * <h3>Actions sur les lignes</h3>
 * Le menu contextuel propose Modifier + transitions d'état (selon statut
 * courant). Les transitions ouvrent les dialogs dédiés en 9.7d ; pour
 * l'instant (Commit 2), les events transitionnent vers une navigation
 * détail où l'admin peut effectuer l'action — comportement provisoire.
 *
 * <p><b>Volontairement absent</b> : suppression physique. Cf. décision
 * 9.7 — la désactivation passe par {@code retire} pour préserver l'historique.
 */
@Component({
  selector: 'tm-doctors-list-page',
  standalone: true,
  imports: [
    CommonModule,
    PageHeaderComponent,
    DoctorSearchBarComponent,
    DoctorTableComponent,
    MatPaginatorModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="doctors-list-page">
      <tm-page-header
        title="Personnel médical"
        [subtitle]="totalLabel()"
      >
        @if (canCreate()) {
          <button type="button" class="btn btn-primary" (click)="goToCreate()">
            <span class="material-icons-round">add</span>
            Ajouter un médecin
          </button>
        }
      </tm-page-header>

      <tm-doctor-search-bar
        [initialCriteria]="initialCriteria"
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

      <tm-doctor-table
        [doctors]="facade.list().doctors"
        [loading]="facade.list().loading"
        [permissions]="permissions()"
        (rowClick)="goToDetail($event)"
        (editClick)="goToEdit($event)"
        (putOnLeaveClick)="onTransitionFromList($event)"
        (retireClick)="onTransitionFromList($event)"
        (reactivateClick)="onTransitionFromList($event)"
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
    </div>
  `,
  styles: [
    `
      .doctors-list-page {
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
      .error-banner .material-icons-round {
        flex-shrink: 0;
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
export class DoctorsListPageComponent implements OnInit, OnDestroy {
  protected readonly facade = inject(DoctorFacade);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly destroy$ = new Subject<void>();

  protected readonly pageSizeOptions = PAGE_SIZE_OPTIONS;

  /** Critères extraits de l'URL au mount — passés à la search-bar pour pré-remplissage. */
  protected initialCriteria: DoctorSearchCriteria = {};

  /** État courant local — utilisé pour la cohérence des appels facade.search. */
  private currentCriteria: DoctorSearchCriteria = {};
  private currentPageRequest: PageRequest = { page: 0, size: 20, sort: 'lastName,asc' };

  protected readonly canCreate = computed(() => this.auth.hasAnyRole(['ADMIN']));

  protected readonly permissions = computed<DoctorTablePermissions>(() => ({
    canEdit: this.auth.hasAnyRole(['ADMIN']),
    canTransition: this.auth.hasAnyRole(['ADMIN'])
  }));

  /** Libellé sous le titre — "12 médecins trouvés". */
  protected readonly totalLabel = computed(() => {
    const total = this.facade.list().totalElements;
    if (this.facade.list().loading && total === 0) return 'Chargement…';
    return total === 0
      ? 'Aucun médecin'
      : `${total} médecin${total > 1 ? 's' : ''} trouvé${total > 1 ? 's' : ''}`;
  });

  ngOnInit(): void {
    // URL stateful : on lit les query params à chaque changement → la facade
    // est rappelée. takeUntil pour clean unsubscribe.
    this.route.queryParamMap.pipe(takeUntil(this.destroy$)).subscribe((params) => {
      const criteria = this.parseCriteria(params);
      const pageRequest = this.parsePageRequest(params);

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

  protected onCriteriaChange(criteria: DoctorSearchCriteria): void {
    // Nouveau filtre → on retourne à la page 0 (sinon on pourrait être hors plage)
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
      : 'lastName,asc';
    this.currentPageRequest = {
      ...this.currentPageRequest,
      sort: sortParam,
      page: 0
    };
    this.syncUrl();
  }

  protected goToCreate(): void {
    void this.router.navigate(['/admin/staff/new']);
  }

  protected goToDetail(d: DoctorDto): void {
    void this.router.navigate(['/admin/staff', d.id]);
  }

  protected goToEdit(d: DoctorDto): void {
    void this.router.navigate(['/admin/staff', d.id, 'edit']);
  }

  /**
   * Action de transition depuis la liste (putOnLeave / retire / reactivate)
   * → en Commit 2, on redirige vers la page détail où l'admin peut confirmer
   * via les dialogs dédiés (livrés en Commit 4 / 9.7d). Pattern identique
   * à l'archivage patient depuis la liste.
   */
  protected onTransitionFromList(d: DoctorDto): void {
    void this.router.navigate(['/admin/staff', d.id]);
  }

  // ─── URL <-> état ───

  private syncUrl(): void {
    const queryParams = {
      ...this.criteriaToParams(this.currentCriteria),
      ...this.pageRequestToParams(this.currentPageRequest)
    };
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams
    });
  }

  private criteriaToParams(c: DoctorSearchCriteria): Record<string, string> {
    const out: Record<string, string> = {};
    if (c.lastName) out['lastName'] = c.lastName;
    if (c.firstName) out['firstName'] = c.firstName;
    if (c.licenseNumber) out['licenseNumber'] = c.licenseNumber;
    if (c.email) out['email'] = c.email;
    if (c.specialty) out['specialty'] = c.specialty;
    if (c.status) out['status'] = c.status;
    if (c.minYearsOfExperience != null) {
      out['minYearsOfExperience'] = String(c.minYearsOfExperience);
    }
    if (c.maxConsultationFee != null) {
      out['maxConsultationFee'] = String(c.maxConsultationFee);
    }
    return out;
  }

  private pageRequestToParams(p: PageRequest): Record<string, string> {
    const out: Record<string, string> = {};
    if (p.page && p.page > 0) out['page'] = String(p.page);
    if (p.size && p.size !== 20) out['size'] = String(p.size);
    if (p.sort && p.sort !== 'lastName,asc') {
      out['sort'] = typeof p.sort === 'string' ? p.sort : p.sort.join(',');
    }
    return out;
  }

  /** Parse les query params en validant les enums (ignore les valeurs corrompues). */
  private parseCriteria(params: { get: (k: string) => string | null }): DoctorSearchCriteria {
    const out: DoctorSearchCriteria = {};
    const lastName = params.get('lastName');
    if (lastName) out.lastName = lastName;
    const firstName = params.get('firstName');
    if (firstName) out.firstName = firstName;
    const licenseNumber = params.get('licenseNumber');
    if (licenseNumber) out.licenseNumber = licenseNumber;
    const email = params.get('email');
    if (email) out.email = email;

    const specialty = params.get('specialty');
    if (specialty && (VALID_SPECIALTIES as readonly string[]).includes(specialty)) {
      out.specialty = specialty as Specialty;
    }
    const status = params.get('status');
    if (status && (VALID_STATUSES as readonly string[]).includes(status)) {
      out.status = status as DoctorStatus;
    }

    const minExpRaw = params.get('minYearsOfExperience');
    if (minExpRaw && /^\d+$/.test(minExpRaw)) {
      const v = parseInt(minExpRaw, 10);
      if (v >= 0 && v <= 70) out.minYearsOfExperience = v;
    }
    const maxFeeRaw = params.get('maxConsultationFee');
    if (maxFeeRaw && /^\d+$/.test(maxFeeRaw)) {
      const v = parseInt(maxFeeRaw, 10);
      if (v >= 0) out.maxConsultationFee = v;
    }

    return out;
  }

  private parsePageRequest(params: { get: (k: string) => string | null }): PageRequest {
    const pageRaw = params.get('page');
    const sizeRaw = params.get('size');
    const sortRaw = params.get('sort');

    const page = pageRaw && /^\d+$/.test(pageRaw) ? parseInt(pageRaw, 10) : 0;
    const size = sizeRaw && PAGE_SIZE_OPTIONS.includes(parseInt(sizeRaw, 10))
      ? parseInt(sizeRaw, 10)
      : 20;
    const sort = sortRaw && /^[a-zA-Z]+,(asc|desc)$/.test(sortRaw)
      ? sortRaw
      : 'lastName,asc';

    return { page, size, sort };
  }
}
