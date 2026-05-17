import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

/**
 * Affichée quand un guard de rôle refuse l'accès (403). Différent du login —
 * l'utilisateur est connu, mais n'a pas les droits.
 */
@Component({
  selector: 'tm-unauthorized-page',
  standalone: true,
  imports: [RouterLink, MatButtonModule, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex min-h-screen items-center justify-center bg-ts-bg p-6">
      <div class="w-full max-w-md rounded-card bg-ts-surface p-8 text-center shadow-card">
        <div
          class="mx-auto mb-6 flex h-16 w-16 items-center justify-center rounded-full bg-orange-100 text-orange-600"
        >
          <mat-icon class="!text-3xl">block</mat-icon>
        </div>
        <h1 class="text-xl font-bold text-ts-text">Accès refusé</h1>
        <p class="mt-2 text-sm text-ts-text-muted">
          Votre rôle ne permet pas d'accéder à cette section. Contactez l'administrateur
          si vous pensez qu'il s'agit d'une erreur.
        </p>
        <a mat-stroked-button routerLink="/dashboard" class="mt-6">
          <mat-icon>arrow_back</mat-icon>
          Retour au tableau de bord
        </a>
      </div>
    </div>
  `
})
export class UnauthorizedPageComponent {}
