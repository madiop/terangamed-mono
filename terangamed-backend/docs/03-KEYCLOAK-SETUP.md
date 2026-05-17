# TerangaMed — Configuration Keycloak (Étape 3)

## 1. Architecture du realm

| Élément | Identifiant | Type / Configuration |
|---------|-------------|----------------------|
| **Realm** | `terangamed` | Locales : fr (défaut), en — Brute-force protection ON |
| **Client** | `terangamed-frontend` | Public — Authorization Code + PKCE (S256) — Angular SPA |
| **Client** | `terangamed-backend` | Confidentiel — secret `terangamed-backend-secret-CHANGE-ME-IN-PROD` — Audience JWT + service account |
| **Client** | `swagger-ui` | Public — Authorization Code + PKCE — Multi-redirect URIs (un par microservice) |
| **Rôles realm** | `ADMIN`, `DOCTOR`, `RECEPTIONIST` | Lus par `JwtAuthConverter` depuis `realm_access.roles` |

**Mapper Audience** : les clients `terangamed-frontend` et `swagger-ui` ajoutent `terangamed-backend` au claim `aud` de tous leurs tokens. Cela permet d'activer plus tard la validation stricte d'audience côté microservices via `spring.security.oauth2.resourceserver.jwt.audiences=terangamed-backend`.

## 2. Utilisateurs de test

| Username | Password | Rôle | Email |
|----------|----------|------|-------|
| `admin` | `admin` | ADMIN | admin@terangamed.local |
| `dr.martin` | `doctor123` | DOCTOR | jean.martin@terangamed.local |
| `reception` | `reception` | RECEPTIONIST | aissatou.diop@terangamed.local |

> ⚠️ Mots de passe en clair dans le realm export — acceptable en **dev uniquement**. En staging/prod, l'admin Keycloak crée les comptes via la console et applique une politique de mot de passe stricte (`Authentication > Password Policy`).

## 3. Démarrage local

### 3.1 Pré-requis
- Docker Desktop ≥ 24
- Docker Compose v2

### 3.2 Lancer Keycloak + PostgreSQL

```bash
cd terangamed-backend/docker
docker compose up -d postgres keycloak
```

À la première exécution, Keycloak importe automatiquement `terangamed-realm.json`. Le démarrage complet prend ~45 s. Suivre :

```bash
docker compose logs -f keycloak | grep -E "(Imported|Listening|started)"
```

Quand vous voyez `Realm 'terangamed' imported` puis `Listening on: http://0.0.0.0:8180`, c'est prêt.

### 3.3 Accès

| Endpoint | URL | Identifiants |
|----------|-----|--------------|
| Console admin Keycloak (realm `master`) | http://localhost:8180/admin/ | `admin` / `admin` (super-admin Keycloak) |
| Account console realm `terangamed` | http://localhost:8180/realms/terangamed/account/ | `admin`/`dr.martin`/`reception` (utilisateurs métier) |
| Realm public (issuer) | http://localhost:8180/realms/terangamed | — (renvoie JSON, pas de login) |
| OIDC Discovery | http://localhost:8180/realms/terangamed/.well-known/openid-configuration | — |
| JWK Set | http://localhost:8180/realms/terangamed/protocol/openid-connect/certs | — |

> ⚠️ **Piège fréquent** — il existe **deux** admins `admin/admin` distincts :
> - Le **super-admin Keycloak** (realm `master`) → console `/admin/` → gère Keycloak lui-même
> - L'**utilisateur `admin` du realm `terangamed`** → account console → utilisateur métier de l'application
>
> Tenter d'utiliser un compte du realm `terangamed` sur la console `/admin/` (ou inversement) renvoie « Invalid username or password ».

### 3.4 Arrêt

```bash
docker compose down              # garde les volumes (DB persistée)
docker compose down -v           # supprime tout (reset complet)
```

## 4. Obtenir un JWT pour tester

### 4.1 Avec curl (Direct Access Grant — utilitaire de test)

```bash
TOKEN=$(curl -s -X POST \
  "http://localhost:8180/realms/terangamed/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=terangamed-frontend" \
  -d "username=dr.martin" \
  -d "password=doctor123" \
  | jq -r '.access_token')

echo "$TOKEN"
```

Le JWT obtenu contient (déchiffrable via [jwt.io](https://jwt.io)) :

```json
{
  "iss": "http://localhost:8180/realms/terangamed",
  "aud": ["terangamed-backend", "account"],
  "sub": "...",
  "preferred_username": "dr.martin",
  "email": "jean.martin@terangamed.local",
  "realm_access": {
    "roles": ["DOCTOR", "default-roles-terangamed", ...]
  },
  "resource_access": {
    "account": { "roles": ["..."] }
  },
  "scope": "openid profile email"
}
```

### 4.2 Appel d'un endpoint sécurisé

Une fois `patient-service` démarré (étape 4) :

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/patients
```

Le Gateway valide la signature, propage le JWT, le service vérifie le rôle requis (`@PreAuthorize`) et répond.

## 5. Test via Swagger UI (à partir de l'étape 4)

Chaque microservice expose `/swagger-ui.html` avec OAuth2 préconfiguré.

1. Ouvrir http://localhost:8081/swagger-ui.html (patient-service)
2. Cliquer **Authorize** (cadenas en haut à droite)
3. Le formulaire propose le flow `authorizationCode`
4. Renseigner :
   - `client_id` : `swagger-ui`
   - `client_secret` : laisser vide (client public)
5. Cliquer **Authorize** → redirection vers Keycloak
6. Se connecter (ex: `dr.martin` / `doctor123`)
7. Retour sur Swagger UI — le token est injecté automatiquement dans tous les `Try it out`

## 6. Configuration côté microservices

### 6.1 application.yml (déjà en place dans config-repo)

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://keycloak:8180/realms/terangamed   # docker
          # issuer-uri: http://localhost:8180/realms/terangamed  # local
```

Spring Boot découvre automatiquement le JWK set via OIDC Discovery et valide les signatures.

### 6.2 Configuration Swagger OAuth2 (à venir étape 4)

Dans le `OpenApiConfig` de chaque microservice :

```java
@Bean
public OpenAPI openAPI() {
    final String SCHEME_NAME = "keycloak-oauth2";
    return new OpenAPI()
            .components(new Components()
                    .addSecuritySchemes(SCHEME_NAME, new SecurityScheme()
                            .type(SecurityScheme.Type.OAUTH2)
                            .flows(new OAuthFlows()
                                    .authorizationCode(new OAuthFlow()
                                            .authorizationUrl("http://localhost:8180/realms/terangamed/protocol/openid-connect/auth")
                                            .tokenUrl("http://localhost:8180/realms/terangamed/protocol/openid-connect/token")
                                            .scopes(new Scopes()
                                                    .addString("openid", "OpenID Connect")
                                                    .addString("profile", "Profil utilisateur"))))))
            .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME));
}
```

## 7. Tests rapides post-installation

```bash
# 1. Réponse OIDC discovery (non sécurisée)
curl -s http://localhost:8180/realms/terangamed/.well-known/openid-configuration | jq '.issuer'
# → "http://localhost:8180/realms/terangamed"

# 2. Health Keycloak
curl -s http://localhost:8180/health/ready | jq
# → { "status": "UP", "checks": [...] }

# 3. Token admin
curl -s -X POST http://localhost:8180/realms/terangamed/protocol/openid-connect/token \
  -d "grant_type=password" \
  -d "client_id=terangamed-frontend" \
  -d "username=admin" -d "password=admin" \
  | jq '.access_token' | head -c 60

# 4. Introspection (avec service account terangamed-backend)
curl -s -X POST \
  -u "terangamed-backend:terangamed-backend-secret-CHANGE-ME-IN-PROD" \
  http://localhost:8180/realms/terangamed/protocol/openid-connect/token/introspect \
  -d "token=$TOKEN" | jq
```

## 8. Personnalisations à faire AVANT prod

- [ ] Régénérer le secret de `terangamed-backend` via Keycloak admin → **Clients > Credentials > Regenerate**
- [ ] Activer `sslRequired: all` (forcer HTTPS) — actuellement `external`
- [ ] Configurer le SMTP (récupération de mot de passe)
- [ ] Activer la politique de mot de passe (`Authentication > Password Policy`)
- [ ] Activer `verifyEmail: true`
- [ ] Restreindre les origines `webOrigins` aux domaines réels (pas `+`)
- [ ] Restreindre `redirectUris` aux URLs réelles (pas de wildcards en prod)
- [ ] Externaliser les credentials admin (secrets manager, pas env vars en clair)
- [ ] Activer les events Keycloak vers un agrégateur (`eventsListeners`)

## 9. Dépannage

### 9.1 Conflit de port (`bind: address already in use`)

**Symptôme** :
```
Error response from daemon: ports are not available:
  exposing port TCP 0.0.0.0:5432 -> 127.0.0.1:0:
  listen tcp 0.0.0.0:5432: bind: address already in use
```

**Cause probable** : un autre processus détient le port (PostgreSQL local Homebrew, conteneur Docker zombie, ou autre projet). Sur macOS, `lsof -i :5432` sans `sudo` ne voit pas tous les processus.

**Diagnostic** :
```bash
# Tous les processus utilisant le port (avec sudo — important sur macOS)
sudo lsof -nP -iTCP:5432 -sTCP:LISTEN

# Conteneurs Docker (même arrêtés) qui réservent le port
docker ps -a --filter "publish=5432"

# PostgreSQL via Homebrew ?
brew services list | grep -i postgres

# Sur macOS, voir ce que Docker Desktop tient
sudo netstat -anv | grep 5432
```

**Solutions** (par ordre de préférence) :

1. **Changer le port hôte via `.env`** (sans toucher au compose) :
   ```bash
   echo "POSTGRES_HOST_PORT=5433" >> .env
   docker compose up -d postgres keycloak
   # Connexion depuis l'hôte : psql -h localhost -p 5433 -U terangamed
   ```
   Les services Docker (Keycloak, futurs microservices) continuent d'utiliser `postgres:5432` en interne — aucune autre config à toucher.

2. **Arrêter le service en conflit** :
   - PostgreSQL Homebrew : `brew services stop postgresql@14` (adapter la version)
   - Conteneur Docker zombie : `docker rm -f $(docker ps -aq --filter "publish=5432")`

3. **Idem pour Keycloak** si 8180 est pris : `KEYCLOAK_HOST_PORT=8181` dans `.env`.

### 9.2 `FATAL: database "keycloak" does not exist`

**Cause** : le volume PostgreSQL a été initialisé une première fois sans que le script de création des bases s'exécute. Les scripts dans `/docker-entrypoint-initdb.d/` ne tournent **qu'au tout premier démarrage** (data directory vide). Sur un volume déjà initialisé, ils sont silencieusement ignorés.

**Solution** : reset complet du volume pour forcer le re-init.

```bash
cd terangamed-backend/docker
docker compose down -v          # -v efface les volumes (postgres-data inclus)
docker compose up -d postgres   # premier boot → exécute 01-create-databases.sql
docker compose logs postgres | grep -E "CREATE DATABASE|database system is ready"
# Vous devez voir 7 lignes "CREATE DATABASE" (keycloak + 6 services)

docker compose up -d keycloak   # une fois Postgres prêt
```

> **Note historique** : avant correction, ce projet utilisait un script bash `init-multiple-databases.sh` qui exigeait le bit exécutable `+x` — facilement perdu lors d'un clone Git ou d'une création de fichier via outil. Le passage à un fichier SQL pur (`01-create-databases.sql`) élimine ce piège : un fichier SQL n'a pas besoin d'être exécutable.

### 9.3 « Invalid username or password » avec admin/admin

**Causes possibles** :

1. **Mauvaise URL** — la console admin Keycloak est sur `/admin/`, pas sur `/realms/terangamed`. Voir le tableau § 3.3 pour les bonnes URLs.

2. **Mauvais compte pour la mauvaise console** — `admin/admin` master ≠ `admin/admin` du realm `terangamed`. Ce sont deux comptes différents qui partagent juste le même login.

3. **Bootstrap admin non créé** — pour Keycloak **24** (notre version), les variables sont `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD`. Pour Keycloak **26+**, ce sont `KC_BOOTSTRAP_ADMIN_USERNAME` / `KC_BOOTSTRAP_ADMIN_PASSWORD`. Utiliser les mauvaises pour la version → l'admin n'est jamais créé. Dans tous les cas, l'admin n'est créé qu'au **tout premier démarrage** sur une base vide.

**Solution** : reset complet pour forcer la création.
```bash
docker compose down -v
docker compose up -d postgres
sleep 10
docker compose logs postgres | grep "CREATE DATABASE"   # 7 bases attendues
docker compose up -d keycloak
docker compose logs keycloak | grep -iE "(admin|imported)"
# Doit afficher : "Added user 'admin' to realm 'master'" + "Imported realm 'terangamed'"
```

### 9.4 Autres problèmes courants

| Problème | Cause probable | Solution |
|----------|----------------|----------|
| `Realm with name terangamed already exists` | Volume Postgres déjà initialisé | `docker compose down -v` puis relancer |
| Token issuer mismatch côté microservice | URL `localhost` vs `keycloak` | Côté Docker, microservices doivent utiliser `http://keycloak:8180/...` ; côté hôte, `http://localhost:8180/...` |
| `invalid_grant` au login depuis Swagger | redirect_uri manquant dans le client `swagger-ui` | Ajouter l'URI dans Keycloak admin → Clients → swagger-ui → Valid Redirect URIs |
| 401 sur appel API même avec token valide | Mauvais audience / expiration | Vérifier `exp` du JWT, vérifier que le rôle requis est bien dans `realm_access.roles` |
| Browser refuse la redirection vers Keycloak (CORS) | Origine non autorisée dans le client | Ajouter l'origine dans `Web Origins` du client |

## 10. Références

- Realm export : [`docker/keycloak/realm-export.json`](../docker/keycloak/realm-export.json)
- Docker compose : [`docker/docker-compose.yml`](../docker/docker-compose.yml)
- `JwtAuthConverter` (extraction des rôles) : [`common-lib/.../JwtAuthConverter.java`](../common-lib/src/main/java/com/terangamed/common/security/JwtAuthConverter.java)
- Documentation Keycloak 24 : https://www.keycloak.org/docs/24.0/
