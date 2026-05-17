import { HttpParams } from '@angular/common/http';

/**
 * Page paginée — réplique de {@code com.terangamed.common.pagination.PageResponse}.
 *
 * <p>Le backend renvoie ce shape pour tous les endpoints "/search" :
 * <pre>
 * { content: [...], page, size, totalElements, totalPages, first, last }
 * </pre>
 */
export interface Page<T> {
  readonly content: readonly T[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly first: boolean;
  readonly last: boolean;
}

/**
 * Pagination + tri pour les requêtes "/search". Mappé par le SortValidator
 * backend qui rejette les champs non whitelistés (HTTP 400).
 */
export interface PageRequest {
  readonly page?: number;
  readonly size?: number;
  /** ex: "lastName,asc" ou "consultationDate,desc". Multiple = répéter le param. */
  readonly sort?: string | readonly string[];
}

/**
 * Helper — convertit un objet plat en {@link HttpParams}, en filtrant les
 * valeurs nulles/undefined/empty-string. Les arrays deviennent des params
 * répétés (ex: ?sort=a&sort=b).
 */
export function toHttpParams(input: Record<string, unknown> | undefined): HttpParams {
  let params = new HttpParams();
  if (!input) {
    return params;
  }
  for (const [key, value] of Object.entries(input)) {
    if (value === null || value === undefined) {
      continue;
    }
    if (Array.isArray(value)) {
      for (const v of value) {
        if (v !== null && v !== undefined && v !== '') {
          params = params.append(key, String(v));
        }
      }
    } else if (value === '') {
      continue;
    } else if (value instanceof Date) {
      params = params.set(key, value.toISOString());
    } else {
      params = params.set(key, String(value));
    }
  }
  return params;
}
