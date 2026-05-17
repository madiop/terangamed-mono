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
import { MatDialog } from '@angular/material/dialog';
import { Subject, takeUntil } from 'rxjs';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { AuthService } from '@core/auth/auth.service';
import {
  BloodGroup,
  Gender,
  PatientDto,
  PatientSearchCriteria,
  PatientStatus
} from '@api/models/patient.model';
import { PageRequest } from '@api/common.types';
import { PatientFacade } from '../patient.facade';
import { PatientSearchBarComponent } from '../components/patient-search-bar.component';
import {
  PatientTableComponent,
  PatientTablePermissions
} from '../components/patient-table.component';
import {
  PatientArchiveDialogComponent,
  PatientArchiveDialogResult
} from '../components/patient-archive-dialog.component';

const VALID_STATUSES: readonly PatientStatus[] = ['ACTIVE', 'INACTIVE', 'ARCHIVED'];
const VALID_GENDERS: readonly Gender[] = ['MALE', 'FEMALE'];
const VALID_BLOOD: readonly BloodGroup[] = [
  'A_POS', 'A_NEG', 'B_POS', 'B_NEG',
  'AB_POS', 'AB_NEG', 'O_POS', 'O_NEG', 'UNKNOWN'
];
const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

/**
 * Page liste patients — orchestration des composants enfants.
 *
 * <h3>URL stateful</h3>
 * Tous les filtres + la pagination + le tri sont reflétés dans la query
 * string : {@code /patients?lastName=Diop&status=ACTIVE&page=2&sort=lastName,asc}.
 * Avantage : URL partageable, navigation back/forward fonctionnelle, refresh
 * F5 préserve l'état.
 *
 * <h3>Permissions</h3>
 * <ul>
 *   <li>Bouton "Nouveau patient" : ADMIN, DOCTOR, RECEPTIONIST</li>
 *   <li>Modifier : ADMIN, DOCTOR, RECEPTIONIST</li>
 *   <li>Archiver : ADMIN uniquement</li>
 * </ul>
 */
@Component({
  selector: 'tm-patients-list-page',
  standalone: true,
  imports: [
    CommonModule,
    PageHeaderComponent,
    PatientSearchBarComponent,
    PatientTableComponent,
    MatPaginatorModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="patients-list-page">
      <tm-page-header
        title="Patients"
        [subtitle]="totalLabel()"
      >
        @if (canCreate()) {
          <button type="button" class="btn btn-primary" (click)="goToCreate()">
            <span class="material-icons-round">add</span>
            Nouveau patient
          </button>
        }
      </tm-page-header>

      <tm-patient-search-bar
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

      <tm-patient-table
        [patients]="facade.list().patients"
        [loading]="facade.list().loading"
        [permissions]="permissions()"
        (rowClick)="goToDetail($event)"
        (editClick)="goToEdit($event)"
        (archiveClick)="onArchive($event)"
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
      .patients-list-page {
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
export class PatientsListPageComponent implements OnInit, OnDestroy {
  protected readonly facade = inject(PatientFacade);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  private readonly destroy$ = new Subject<void>();

  protected readonly pageSizeOptions = PAGE_SIZE_OPTIONS;

  /** Critères extraits de l'URL au mount — passés à la search-bar pour pré-remplissage. */
  protected initialCriteria: PatientSearchCriteria = {};

  /** État courant local — utilisé pour la cohérence des appels facade.search. */
  private currentCriteria: PatientSearchCriteria = {};
  private currentPageRequest: PageRequest = { page: 0, size: 20, sort: 'lastName,asc' };

  protected readonly canCreate = computed(() =>
    this.auth.hasAnyRole(['ADMIN', 'DOCTOR', 'RECEPTIONIST'])
  );

  protected readonly permissions = computed<PatientTablePermissions>(() => ({
    canEdit: this.auth.hasAnyRole(['ADMIN', 'DOCTOR', 'RECEPTIONIST']),
    canArchive: this.auth.hasAnyRole(['ADMIN'])
  }));

  /** Libellé sous le titre — "12 patients trouvés". */
  protected readonly totalLabel = computed(() => {
    const total = this.facade.list().totalElements;
    if (this.facade.list().loading && total === 0) return 'Chargement…';
    return total === 0
      ? 'Aucun patient'
      : `${total} patient${total > 1 ? 's' : ''} trouvé${total > 1 ? 's' : ''}`;
  });

  ngOnInit(): void {
    // Lit les query params pour reconstruire l'état initial — supporte refresh F5
    // et liens partageables. takeUntil pour clean unsubscribe.
    this.route.queryParamMap.pipe(takeUntil(this.destroy$)).subscribe((params) => {
      const criteria = this.parseCriteria(params);
      const pageRequest = this.parsePageRequest(params);

      // Mémorise pour l'init de la search-bar (synchrone via @Input)
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

  protected onCriteriaChange(criteria: PatientSearchCriteria): void {
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
    // Si direction vide, on retire le sort pour utiliser le défaut backend
    const sortParam = sort.direction
      ? `${sort.active},${sort.direction}`
      : 'lastName,asc';
    this.currentPageRequest = {
      ...this.currentPageRequest,
      sort: sortParam,
      page: 0 // re-trier depuis le début
    };
    this.syncUrl();
  }

  protected goToCreate(): void {
    void this.router.navigate(['/patients/new']);
  }

  protected goToDetail(p: PatientDto): void {
    void this.router.navigate(['/patients', p.id]);
  }

  protected goToEdit(p: PatientDto): void {
    void this.router.navigate(['/patients', p.id, 'edit']);
  }

  protected onArchive(p: PatientDto): void {
    this.dialog
      .open<PatientArchiveDialogComponent, { patient: PatientDto }, PatientArchiveDialogResult>(
        PatientArchiveDialogComponent,
        {
          data: { patient: p },
          width: '480px'
        }
      )
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          // Le statut local du patient dans facade.detail() a été mis à jour,
          // mais la liste vient de facade.list() — on rejoue la recherche
          // pour rafraîchir le statut affiché dans le tableau.
          this.facade.refresh();
        }
      });
  }

  // ─── URL <-> état ───

  /**
   * Met à jour l'URL avec l'état courant (criteria + pageRequest), ce qui
   * déclenche la souscription queryParamMap et donc le refresh de la liste.
   * Source unique de vérité = l'URL.
   */
  private syncUrl(): void {
    const queryParams = {
      ...this.criteriaToParams(this.currentCriteria),
      ...this.pageRequestToParams(this.currentPageRequest)
    };
    // Pas de queryParamsHandling → comportement par défaut = remplace les params
    // (ce qu'on veut ici : l'état complet est reflété dans l'URL).
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams
    });
  }

  private criteriaToParams(c: PatientSearchCriteria): Record<string, string> {
    const out: Record<string, string> = {};
    if (c.lastName) out['lastName'] = c.lastName;
    if (c.status) out['status'] = c.status;
    if (c.gender) out['gender'] = c.gender;
    if (c.bloodGroup) out['bloodGroup'] = c.bloodGroup;
    if (c.city) out['city'] = c.city;
    return out;
  }

  private pageRequestToParams(p: PageRequest): Record<string, string> {
    const out: Record<string, string> = {};
    // Page 0 et taille par défaut omises pour des URLs propres
    if (p.page && p.page > 0) out['page'] = String(p.page);
    if (p.size && p.size !== 20) out['size'] = String(p.size);
    if (p.sort && p.sort !== 'lastName,asc') {
      // typeof narrow plus fiable que Array.isArray sur les readonly arrays
      out['sort'] = typeof p.sort === 'string' ? p.sort : p.sort.join(',');
    }
    return out;
  }

  /** Parse les query params en validant les enums (ignore les valeurs corrompues). */
  private parseCriteria(params: { get: (k: string) => string | null }): PatientSearchCriteria {
    const out: PatientSearchCriteria = {};
    const lastName = params.get('lastName');
    if (lastName) out.lastName = lastName;
    const city = params.get('city');
    if (city) out.city = city;

    const status = params.get('status');
    if (status && (VALID_STATUSES as readonly string[]).includes(status)) {
      out.status = status as PatientStatus;
    }
    const gender = params.get('gender');
    if (gender && (VALID_GENDERS as readonly string[]).includes(gender)) {
      out.gender = gender as Gender;
    }
    const bloodGroup = params.get('bloodGroup');
    if (bloodGroup && (VALID_BLOOD as readonly string[]).includes(bloodGroup)) {
      out.bloodGroup = bloodGroup as BloodGroup;
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
