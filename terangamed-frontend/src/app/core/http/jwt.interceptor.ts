import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '@core/auth/auth.service';

/**
 * Intercepteur HTTP — ajoute l'header {@code Authorization: Bearer <token>}
 * à toutes les requêtes vers l'API TerangaMed.
 *
 * <p>Filtres :
 * <ul>
 *   <li>Uniquement les URLs commençant par {@code /api/} (relatives — proxifiées
 *       en dev par Angular CLI, servies par nginx en prod)</li>
 *   <li>Ne touche pas aux requêtes externes (Keycloak, fonts, etc.)</li>
 *   <li>N'écrase pas un Authorization header déjà présent (cas custom)</li>
 *   <li>Skippe si l'utilisateur n'est pas authentifié — le serveur répondra 401
 *       et l'app pourra rediriger vers /login</li>
 * </ul>
 */
export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  // Filtrage : seulement les appels vers notre API
  if (!req.url.startsWith('/api/') && !req.url.startsWith('api/')) {
    return next(req);
  }
  if (req.headers.has('Authorization')) {
    return next(req);
  }

  const auth = inject(AuthService);
  const token = auth.accessToken();
  if (!token) {
    return next(req);
  }

  const authReq = req.clone({
    setHeaders: { Authorization: `Bearer ${token}` }
  });
  return next(authReq);
};
