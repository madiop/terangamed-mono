package com.terangamed.common.pagination;

import com.terangamed.common.exception.BadRequestException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Vérifie que les champs de tri demandés appartiennent à une whitelist explicite.
 *
 * <p><b>Pourquoi cette protection ?</b><br>
 * Permettre un tri arbitraire sur n'importe quel champ JPA expose à plusieurs risques :
 * <ul>
 *   <li><b>Information leak</b> — l'ordre de tri sur un champ sensible (mot de passe hashé,
 *       date de dernière connexion, etc.) peut révéler des informations</li>
 *   <li><b>SQL injection partielle</b> — Spring Data ne valide pas par défaut les noms de propriétés</li>
 *   <li><b>Performance</b> — un tri sur un champ non-indexé peut faire écrouler la base</li>
 * </ul>
 *
 * <p>Usage typique dans un service :
 * <pre>
 *   private static final Set&lt;String&gt; SORTABLE = Set.of("lastName", "firstName", "createdAt");
 *
 *   public Page&lt;PatientEntity&gt; search(SearchCriteria c, Pageable pageable) {
 *       SortValidator.sanitize(pageable, SORTABLE);
 *       return repository.findAll(PatientSpecifications.withCriteria(c), pageable);
 *   }
 * </pre>
 *
 * <p>En cas de champ non whitelisté, {@link BadRequestException} (HTTP 400) est levée.
 */
public final class SortValidator {

    private SortValidator() {
        throw new UnsupportedOperationException("Utility class — do not instantiate");
    }

    /**
     * Valide que tous les champs de tri du {@code pageable} sont dans la whitelist.
     *
     * @param pageable      le {@code Pageable} reçu (peut être {@code null} ou unsorted)
     * @param allowedFields ensemble des noms de champs autorisés
     * @return le {@code pageable} inchangé (chaînage fluide)
     * @throws BadRequestException si un ou plusieurs champs ne sont pas dans la whitelist
     */
    public static Pageable sanitize(Pageable pageable, Set<String> allowedFields) {
        if (pageable == null || pageable.getSort().isUnsorted()) {
            return pageable;
        }
        Set<String> rejected = new LinkedHashSet<>();
        for (Sort.Order order : pageable.getSort()) {
            if (!allowedFields.contains(order.getProperty())) {
                rejected.add(order.getProperty());
            }
        }
        if (!rejected.isEmpty()) {
            throw new BadRequestException("INVALID_SORT_FIELD",
                    "Sort field(s) not allowed: %s. Allowed fields: %s"
                            .formatted(rejected, allowedFields));
        }
        return pageable;
    }
}
