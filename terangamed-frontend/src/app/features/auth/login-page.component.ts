import { ChangeDetectionStrategy, Component, OnInit, effect, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '@core/auth/auth.service';

/**
 * Page de connexion intégrée — formulaire local (username/password) qui appelle
 * Keycloak via Resource Owner Password Credentials Grant. L'utilisateur ne
 * quitte JAMAIS l'application TerangaMed.
 */
@Component({
  selector: 'tm-login-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="login-page">
      <div class="login-card card">
        <!-- Branding -->
        <div class="login-brand">
          <div class="brand-icon">
            <span class="material-icons-round">local_hospital</span>
          </div>
          <h1 class="brand-name">TerangaMed</h1>
          <p class="brand-tagline">Clinique Médicale</p>
        </div>

        <!-- Formulaire -->
        <form
          class="login-form"
          [formGroup]="loginForm"
          (ngSubmit)="onSubmit()"
          autocomplete="on"
        >
          <div class="form-field">
            <label for="username">Identifiant</label>
            <div class="input-wrapper">
              <span class="material-icons-round input-icon">person_outline</span>
              <input
                id="username"
                type="text"
                formControlName="username"
                placeholder="ex: dr.martin"
                autocomplete="username"
                autofocus
              />
            </div>
            @if (showError('username', 'required')) {
              <p class="field-error">Identifiant requis</p>
            }
          </div>

          <div class="form-field">
            <label for="password">Mot de passe</label>
            <div class="input-wrapper">
              <span class="material-icons-round input-icon">lock_outline</span>
              <input
                id="password"
                [type]="showPassword() ? 'text' : 'password'"
                formControlName="password"
                placeholder="Votre mot de passe"
                autocomplete="current-password"
              />
              <button
                type="button"
                class="toggle-password"
                (click)="togglePassword()"
                [attr.aria-label]="showPassword() ? 'Masquer le mot de passe' : 'Afficher le mot de passe'"
                tabindex="-1"
              >
                <span class="material-icons-round">
                  {{ showPassword() ? 'visibility_off' : 'visibility' }}
                </span>
              </button>
            </div>
            @if (showError('password', 'required')) {
              <p class="field-error">Mot de passe requis</p>
            }
          </div>

          @if (errorMessage()) {
            <div class="login-error" role="alert">
              <span class="material-icons-round">error_outline</span>
              {{ errorMessage() }}
            </div>
          }

          <button
            type="submit"
            class="btn btn-primary login-submit"
            [disabled]="loading() || loginForm.invalid"
          >
            @if (loading()) {
              <span class="material-icons-round spin">progress_activity</span>
              Connexion en cours…
            } @else {
              <span class="material-icons-round">login</span>
              Se connecter
            }
          </button>
        </form>

        <!-- Footer card -->
        <p class="login-footer text-muted">
          Connexion sécurisée — accès réservé au personnel autorisé
        </p>
      </div>
    </div>
  `,
  styleUrl: './login-page.component.scss'
})
export class LoginPageComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly loginForm = this.fb.nonNullable.group({
    username: ['', [Validators.required]],
    password: ['', [Validators.required]]
  });

  readonly loading = signal(false);
  readonly showPassword = signal(false);
  readonly errorMessage = signal<string | null>(null);

  constructor() {
    // Auto-redirect si on devient authentifié pendant qu'on est sur la page
    effect(() => {
      if (this.auth.isAuthenticated()) {
        void this.router.navigate(['/dashboard']);
      }
    });
  }

  ngOnInit(): void {
    if (this.auth.isAuthenticated()) {
      void this.router.navigate(['/dashboard']);
    }
  }

  togglePassword(): void {
    this.showPassword.update((v) => !v);
  }

  showError(field: 'username' | 'password', error: string): boolean {
    const c = this.loginForm.controls[field];
    return c.touched && c.hasError(error);
  }

  async onSubmit(): Promise<void> {
    if (this.loginForm.invalid || this.loading()) {
      this.loginForm.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.errorMessage.set(null);

    const { username, password } = this.loginForm.getRawValue();
    try {
      await this.auth.loginWithCredentials(username, password);
      // La redirection est gérée par le effect() ci-dessus quand isAuthenticated devient true
    } catch (err: unknown) {
      this.errorMessage.set(this.translateLoginError(err));
    } finally {
      this.loading.set(false);
    }
  }

  /** Traduit les erreurs OAuth/réseau en messages user-friendly. */
  private translateLoginError(err: unknown): string {
    const message = err instanceof Error ? err.message : String(err);
    const lower = message.toLowerCase();

    if (lower.includes('invalid_grant') || lower.includes('401')) {
      return 'Identifiant ou mot de passe incorrect.';
    }
    if (lower.includes('unauthorized_client') || lower.includes('invalid_client')) {
      return 'Configuration client invalide. Contactez l\'administrateur.';
    }
    if (lower.includes('network') || lower.includes('failed to fetch')) {
      return 'Serveur d\'authentification injoignable. Vérifiez votre connexion.';
    }
    return 'Erreur de connexion. Réessayez ou contactez l\'administrateur.';
  }
}
