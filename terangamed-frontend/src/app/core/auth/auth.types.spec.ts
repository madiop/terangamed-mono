import { displayNameOf, initialsOf, isTerangaMedRole, AuthUser } from './auth.types';

describe('auth.types helpers', () => {
  const sampleUser = (override: Partial<AuthUser> = {}): AuthUser => ({
    sub: 'kc-sub-1',
    username: 'dr.martin',
    email: 'martin@example.sn',
    firstName: 'Jean',
    lastName: 'Martin',
    roles: ['DOCTOR'],
    ...override
  });

  describe('isTerangaMedRole', () => {
    it('accepte ADMIN/DOCTOR/RECEPTIONIST/PATIENT', () => {
      expect(isTerangaMedRole('ADMIN')).toBe(true);
      expect(isTerangaMedRole('DOCTOR')).toBe(true);
      expect(isTerangaMedRole('RECEPTIONIST')).toBe(true);
      expect(isTerangaMedRole('PATIENT')).toBe(true);
    });
    it('rejette les rôles inconnus (uma_authorization, default-roles, etc.)', () => {
      expect(isTerangaMedRole('uma_authorization')).toBe(false);
      expect(isTerangaMedRole('default-roles-terangamed')).toBe(false);
      expect(isTerangaMedRole('')).toBe(false);
    });
  });

  describe('displayNameOf', () => {
    it('utilise prenom + nom si dispos', () => {
      expect(displayNameOf(sampleUser())).toBe('Jean Martin');
    });
    it('fallback sur username si pas de noms', () => {
      expect(displayNameOf(sampleUser({ firstName: null, lastName: null }))).toBe('dr.martin');
    });
    it('fallback final "Utilisateur"', () => {
      expect(displayNameOf(null)).toBe('Utilisateur');
      expect(
        displayNameOf(sampleUser({ firstName: null, lastName: null, username: '' }))
      ).toBe('Utilisateur');
    });
  });

  describe('initialsOf', () => {
    it('combine prenom + nom', () => {
      expect(initialsOf(sampleUser())).toBe('JM');
    });
    it('1 mot → 2 premières lettres', () => {
      expect(initialsOf(sampleUser({ firstName: null, lastName: null, username: 'admin' }))).toBe(
        'AD'
      );
    });
    it('null → ?', () => {
      expect(initialsOf(null)).toBe('?');
    });
  });
});
