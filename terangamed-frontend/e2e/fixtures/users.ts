/**
 * Comptes Keycloak pré-existants dans le seed de dev (realm-export.json).
 *
 * <p>Ces credentials sont volontairement publics — ils correspondent au seed
 * dev local de Keycloak, jamais à un environnement de production. Le seed est
 * chargé automatiquement au démarrage de Keycloak via la commande
 * {@code --import-realm}.
 *
 * <p>Si ce seed change (ajout de nouveaux users, rotation de mots de passe),
 * mettre à jour cette table — les tests E2E s'appuient dessus.
 */
export type SeedRole = 'ADMIN' | 'DOCTOR' | 'RECEPTIONIST';

export interface SeedUser {
  /** Username Keycloak (champ {@code preferred_username} dans le JWT). */
  readonly username: string;
  /** Mot de passe en clair — uniquement valable pour le seed dev. */
  readonly password: string;
  /** Rôle realm associé — utilisé par {@code roleGuard} dans le frontend. */
  readonly role: SeedRole;
  /** Nom affiché en interface (à titre informatif pour les assertions). */
  readonly displayName: string;
}

/**
 * Catalogue des utilisateurs seed indexés par rôle. Pour des tests qui
 * croisent plusieurs rôles, utiliser {@link SEED_USERS_BY_ROLE} ou
 * {@link allUsers}.
 */
export const SEED_USERS: Record<SeedRole, SeedUser> = {
  ADMIN: {
    username: 'admin',
    password: 'admin',
    role: 'ADMIN',
    displayName: 'Admin'
  },
  DOCTOR: {
    username: 'dr.martin',
    password: 'doctor123',
    role: 'DOCTOR',
    displayName: 'Dr. Martin'
  },
  RECEPTIONIST: {
    username: 'reception',
    password: 'reception',
    role: 'RECEPTIONIST',
    displayName: 'Réception'
  }
} as const;

export const allUsers = (): SeedUser[] => Object.values(SEED_USERS);
