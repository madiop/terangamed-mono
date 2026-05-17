package com.terangamed.medical.pdf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VerificationQrServiceTest {

    private final VerificationQrService service = new VerificationQrService();

    @Test
    @DisplayName("Encode une URL → base64 non vide d'un PNG valide")
    void encodesUrlToValidPng() {
        String b64 = service.encodeAsBase64Png("https://terangamed.sn/verify/ORD-2026-00042");

        assertThat(b64).isNotBlank();
        byte[] png = Base64.getDecoder().decode(b64);
        // Magic bytes PNG : 89 50 4E 47 0D 0A 1A 0A
        assertThat(png[0] & 0xFF).isEqualTo(0x89);
        assertThat(png[1]).isEqualTo((byte) 'P');
        assertThat(png[2]).isEqualTo((byte) 'N');
        assertThat(png[3]).isEqualTo((byte) 'G');
    }

    @Test
    @DisplayName("Taille custom respectée — produit un PNG plus grand")
    void respectsCustomSize() {
        byte[] small = Base64.getDecoder().decode(service.encodeAsBase64Png("https://test", 100));
        byte[] big = Base64.getDecoder().decode(service.encodeAsBase64Png("https://test", 400));

        assertThat(big.length).isGreaterThan(small.length);
    }

    @Test
    @DisplayName("URL UTF-8 (accents) → encodée sans erreur")
    void supportsUtf8Url() {
        String b64 = service.encodeAsBase64Png(
                "https://terangamed.sn/vérifier/ordonnance?nom=Aïssatou");

        assertThat(b64).isNotBlank();
        assertThat(Base64.getDecoder().decode(b64)).hasSizeGreaterThan(100);
    }

    @Test
    @DisplayName("URL null → PdfStorageException explicite")
    void rejectsNullUrl() {
        assertThatThrownBy(() -> service.encodeAsBase64Png(null))
                .isInstanceOf(PdfStorageException.class)
                .hasMessageContaining("URL");
    }

    @Test
    @DisplayName("URL vide → PdfStorageException explicite")
    void rejectsBlankUrl() {
        assertThatThrownBy(() -> service.encodeAsBase64Png("   "))
                .isInstanceOf(PdfStorageException.class)
                .hasMessageContaining("URL");
    }
}
