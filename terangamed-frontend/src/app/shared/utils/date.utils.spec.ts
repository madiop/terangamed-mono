import { ageFromBirthDate, todayLocal, toLocalDateString } from './date.utils';

describe('date.utils', () => {
  describe('todayLocal', () => {
    it('renvoie la date du jour locale au format YYYY-MM-DD', () => {
      const today = todayLocal();
      expect(today).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    });
  });

  describe('toLocalDateString', () => {
    it('formate une date arbitraire', () => {
      // Date locale arbitraire
      expect(toLocalDateString(new Date(2026, 3, 15))).toBe('2026-04-15');
    });
  });

  describe('ageFromBirthDate', () => {
    it('renvoie null si entrée vide/null/undefined', () => {
      expect(ageFromBirthDate(null)).toBeNull();
      expect(ageFromBirthDate(undefined)).toBeNull();
      expect(ageFromBirthDate('')).toBeNull();
    });

    it('renvoie null si format invalide', () => {
      expect(ageFromBirthDate('not-a-date')).toBeNull();
    });

    it("calcule l'âge depuis une date de naissance valide", () => {
      // 30 ans dans le passé : doit donner 30 (ou 29 si l'anniv n'est pas encore passé)
      const thirtyYearsAgo = new Date();
      thirtyYearsAgo.setFullYear(thirtyYearsAgo.getFullYear() - 30);
      const iso = thirtyYearsAgo.toISOString().split('T')[0];
      const age = ageFromBirthDate(iso);
      expect(age).toBeGreaterThanOrEqual(29);
      expect(age).toBeLessThanOrEqual(30);
    });
  });
});
