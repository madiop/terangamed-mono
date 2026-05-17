import { differenceInYears, format, parseISO } from 'date-fns';

/**
 * Formate une date locale en {@code YYYY-MM-DD} (sans timezone — utile pour
 * les paramètres backend de type {@code LocalDate}).
 *
 * <p><b>Pourquoi pas {@code Date.toISOString()}</b> ? Parce que c'est UTC :
 * en TZ +0200 à 23h locale, on serait déjà au lendemain en UTC.
 * {@code date-fns/format} respecte le fuseau du navigateur.
 */
export function toLocalDateString(date: Date = new Date()): string {
  return format(date, 'yyyy-MM-dd');
}

/** Date du jour locale en {@code YYYY-MM-DD}. */
export function todayLocal(): string {
  return toLocalDateString(new Date());
}

/**
 * Âge en années à partir d'une date de naissance ISO ({@code YYYY-MM-DD}).
 * Renvoie {@code null} si la date est invalide.
 */
export function ageFromBirthDate(birthDateIso: string | null | undefined): number | null {
  if (!birthDateIso) {
    return null;
  }
  try {
    const birth = parseISO(birthDateIso);
    if (Number.isNaN(birth.getTime())) {
      return null;
    }
    return differenceInYears(new Date(), birth);
  } catch {
    return null;
  }
}

