package com.terangamed.medical.pdf;

import java.util.Map;
import java.util.Optional;

/**
 * Abstraction du stockage des PDFs d'ordonnances.
 *
 * <p>L'implémentation par défaut est {@link MinioPdfStorage} (S3-compatible).
 * L'interface est volontairement minimale et synchrone — les PDFs d'ordonnance
 * font quelques dizaines de Ko (vs upload multipart pour des fichiers GB).
 *
 * <h3>Sémantique de la clé d'objet</h3>
 * <p>La clé est conçue par l'appelant (cf. {@code PrescriptionPdfService}) pour
 * intégrer un <i>content hash</i> qui rend le stockage idempotent : si le PDF
 * d'une même ordonnance est regénéré sans changement, la clé reste identique →
 * pas de doublon ni de surcoût d'écriture.
 *
 * <h3>Garantie de concurrence</h3>
 * <p>{@code MinIO} gère le put atomic au niveau objet. Deux {@code store(key, ...)}
 * concurrents avec la même clé → dernier writer gagne, mais comme le hash garantit
 * que le contenu est identique, c'est sans conséquence.
 */
public interface PdfStorageService {

    /**
     * Persiste un PDF sous la clé donnée. Idempotent : si la clé existe déjà
     * avec le même contenu, l'écriture est une no-op logique côté caller.
     *
     * @param objectKey    chemin unique dans le bucket (ex: {@code ord/2026/ORD-2026-00042/abc123.pdf})
     * @param pdfBytes     contenu binaire du PDF (généralement < 100 Ko)
     * @param userMetadata métadonnées custom (prescriptionId, renderedBy, etc.) — peut être vide ou null
     * @throws PdfStorageException si le storage est inaccessible ou rejette l'écriture
     */
    void store(String objectKey, byte[] pdfBytes, Map<String, String> userMetadata);

    /**
     * Tente de récupérer un PDF préalablement stocké.
     *
     * @return un {@link StoredPdf} si la clé existe, {@link Optional#empty()} sinon (cache miss légitime)
     * @throws PdfStorageException pour toute erreur autre que "objet inexistant"
     *                             (panne réseau, droits insuffisants, etc.)
     */
    Optional<StoredPdf> retrieve(String objectKey);

    /**
     * Vérifie l'existence d'un objet sans le télécharger. Léger : un seul HEAD request.
     *
     * @throws PdfStorageException pour toute erreur autre que "objet inexistant"
     */
    boolean existsByKey(String objectKey);
}
