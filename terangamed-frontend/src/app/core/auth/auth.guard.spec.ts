import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { signal } from '@angular/core';
import { authGuard, roleGuard } from './auth.guard';
import { AuthService } from './auth.service';
import { TerangaMedRole } from './auth.types';

describe('auth guards', () => {
  let isAuthenticatedSignal: ReturnType<typeof signal<boolean>>;
  let mockAuth: Partial<AuthService>;
  let mockRouter: { createUrlTree: jest.Mock };

  beforeEach(() => {
    isAuthenticatedSignal = signal(false);
    mockAuth = {
      // computed-like accessor
      isAuthenticated: isAuthenticatedSignal as unknown as AuthService['isAuthenticated'],
      hasAnyRole: jest.fn((roles: readonly TerangaMedRole[]) => roles.includes('ADMIN'))
    };
    mockRouter = {
      createUrlTree: jest.fn((commands: unknown[]) => ({ commands }) as unknown as UrlTree)
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: mockAuth },
        { provide: Router, useValue: mockRouter }
      ]
    });
  });

  describe('authGuard', () => {
    it('autorise si authentifié', () => {
      isAuthenticatedSignal.set(true);
      const result = TestBed.runInInjectionContext(() =>
        authGuard({} as never, {} as never)
      );
      expect(result).toBe(true);
    });

    it('redirige vers /login si pas authentifié', () => {
      isAuthenticatedSignal.set(false);
      TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
      expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/login']);
    });
  });

  describe('roleGuard', () => {
    it('redirige vers /login si non authentifié', () => {
      isAuthenticatedSignal.set(false);
      const guard = roleGuard(['ADMIN']);
      TestBed.runInInjectionContext(() => guard({} as never, {} as never));
      expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/login']);
    });

    it('autorise si role attendu présent', () => {
      isAuthenticatedSignal.set(true);
      const guard = roleGuard(['ADMIN']);
      const result = TestBed.runInInjectionContext(() =>
        guard({} as never, {} as never)
      );
      expect(result).toBe(true);
    });

    it('redirige vers /unauthorized si rôle manquant', () => {
      isAuthenticatedSignal.set(true);
      const guard = roleGuard(['DOCTOR']); // mockAuth.hasAnyRole renvoie true seulement pour ADMIN
      TestBed.runInInjectionContext(() => guard({} as never, {} as never));
      expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/unauthorized']);
    });
  });
});
