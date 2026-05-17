import {
  APP_INITIALIZER,
  ApplicationConfig,
  LOCALE_ID,
  provideZoneChangeDetection
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { provideOAuthClient } from 'angular-oauth2-oidc';
import { DateAdapter } from 'angular-calendar';
import { adapterFactory } from 'angular-calendar/date-adapters/date-fns';

import { routes } from './app.routes';
import { AuthService } from '@core/auth/auth.service';
import { jwtInterceptor } from '@core/http/jwt.interceptor';

registerLocaleData(localeFr);

/**
 * Initializer factory pour APP_INITIALIZER (Angular 17).
 * <p>Renvoie une fonction qui retourne une Promise — l'application
 * attendra sa résolution avant de démarrer le routing.
 */
function initializeAuthFactory(auth: AuthService): () => Promise<void> {
  return () => auth.initialize();
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),

    // HTTP client + JwtInterceptor (functional)
    provideHttpClient(withInterceptors([jwtInterceptor])),

    provideAnimations(),
    { provide: LOCALE_ID, useValue: 'fr-FR' },

    // angular-oauth2-oidc — fournit OAuthService et son storage
    provideOAuthClient(),

    // angular-calendar — adapter date-fns (locale FR via locales/fr de date-fns)
    { provide: DateAdapter, useFactory: adapterFactory },

    // Au bootstrap, configure Keycloak + tente le login silencieux.
    // Le routing ne démarre qu'après résolution de cette promise.
    {
      provide: APP_INITIALIZER,
      useFactory: initializeAuthFactory,
      deps: [AuthService],
      multi: true
    }
  ]
};
