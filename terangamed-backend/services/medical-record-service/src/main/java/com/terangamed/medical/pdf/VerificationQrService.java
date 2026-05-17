package com.terangamed.medical.pdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Génère le QR code de vérification d'une ordonnance.
 *
 * <p>Le QR encode l'URL publique de vérification (ex:
 * {@code https://app.terangamed.sn/verify-prescription/ORD-2026-00042?h=ab12cd}).
 * Le scan amène le pharmacien (ou le patient) vers une page qui confirme :
 * <ul>
 *   <li>L'ordonnance existe et n'est pas falsifiée</li>
 *   <li>Le hash du contenu correspond — empêche la modification post-impression</li>
 * </ul>
 *
 * <p><b>Niveau de correction d'erreur</b> : {@code M} (15%) — bon compromis entre
 * densité et résistance à l'impression / aux taches. {@code L} (7%) trop fragile,
 * {@code H} (30%) augmente sensiblement la taille du QR sans bénéfice ici.
 *
 * <p>Output : PNG encodé en base64 (sans préfixe {@code data:image/png;base64,})
 * → inséré dans le template via {@code <img src="data:image/png;base64,[[${qr}]]">}.
 */
@Slf4j
@Service
public class VerificationQrService {

    /** Taille du QR en pixels (carré). 180 px ≈ 4 cm imprimés à 150 DPI. */
    private static final int DEFAULT_SIZE_PX = 180;

    /**
     * Encode l'URL donnée en QR PNG base64.
     *
     * @param verificationUrl URL absolue à encoder (ex: {@code https://...})
     * @return base64 du PNG, sans préfixe {@code data:}
     * @throws PdfStorageException encapsule toute erreur d'encodage ZXing/IO
     *                             (cohérent avec le reste du package pdf)
     */
    public String encodeAsBase64Png(String verificationUrl) {
        return encodeAsBase64Png(verificationUrl, DEFAULT_SIZE_PX);
    }

    /** Variante explicite avec taille custom — utile pour les tests. */
    public String encodeAsBase64Png(String verificationUrl, int sizePx) {
        if (verificationUrl == null || verificationUrl.isBlank()) {
            throw new PdfStorageException("URL de vérification vide — impossible de générer le QR");
        }
        Map<EncodeHintType, Object> hints = Map.of(
                EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name(),
                EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN, 1
        );
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(
                    verificationUrl, BarcodeFormat.QR_CODE, sizePx, sizePx, hints
            );
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                MatrixToImageWriter.writeToStream(matrix, "PNG", out);
                byte[] png = out.toByteArray();
                log.debug("QR généré : url='{}', size={}px, bytes={}",
                        verificationUrl, sizePx, png.length);
                return Base64.getEncoder().encodeToString(png);
            }
        } catch (WriterException | IOException e) {
            throw new PdfStorageException(
                    "Échec génération QR code pour URL=" + verificationUrl, e);
        }
    }
}
