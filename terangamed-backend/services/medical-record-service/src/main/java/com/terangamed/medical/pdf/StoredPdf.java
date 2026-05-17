package com.terangamed.medical.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Représente un PDF récupéré depuis le storage (MinIO).
 *
 * <p>Wrappe :
 * <ul>
 *   <li>{@code content} — le flux d'octets du PDF (à consommer ET fermer côté appelant)</li>
 *   <li>{@code contentLength} — taille en octets, utile pour {@code Content-Length} HTTP</li>
 *   <li>{@code contentType} — toujours {@code application/pdf}</li>
 *   <li>{@code userMetadata} — métadonnées custom posées au {@code store} (prescriptionId, renderedBy, etc.)</li>
 * </ul>
 *
 * <p><b>Important</b> : implémente {@link AutoCloseable} → DOIT être consommé dans
 * un try-with-resources. Si le stream n'est pas fermé, la connexion MinIO reste
 * ouverte (fuite de socket).
 *
 * <pre>{@code
 * try (StoredPdf pdf = storage.retrieve(key).orElseThrow()) {
 *     IOUtils.copy(pdf.content(), response.getOutputStream());
 * }
 * }</pre>
 */
public record StoredPdf(
        InputStream content,
        long contentLength,
        String contentType,
        Map<String, String> userMetadata
) implements AutoCloseable {

    @Override
    public void close() throws IOException {
        if (content != null) {
            content.close();
        }
    }
}
