import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { appConfig } from './app/app.config';
import { loadRuntimeConfig } from './app/core/config/runtime-config';

/**
 * Bootstrap — on charge d'abord `/assets/runtime-config.json` (généré au
 * démarrage du container Docker via envsubst, ou servi tel quel par ng serve)
 * puis on lance Angular. Les helpers `getKeycloakIssuer()` / `getApiBaseUrl()`
 * peuvent ensuite lire la config runtime de manière synchrone.
 */
async function bootstrap(): Promise<void> {
  window.__terangaRuntimeConfig = await loadRuntimeConfig();
  await bootstrapApplication(AppComponent, appConfig);
}

bootstrap().catch((err) => console.error(err));
