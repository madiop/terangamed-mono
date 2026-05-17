package com.terangamed.common.finance;

/**
 * Moyens de paiement supportés — focus marché sénégalais.
 *
 * <p><b>Mobile money</b> : les 5 opérateurs actifs au Sénégal en 2026 sont inclus.
 * Wave est leader (frais bas, pas de SMS), Orange Money est historique mais
 * encore très utilisé (Sonatel), Free Money pour les abonnés Free, Wizall pour
 * les TPE/PME, Yas Money est l'ex-Tigo Cash.
 *
 * <p><b>Saisie de la référence</b> : pour les paiements mobile money, le
 * réceptionniste saisit la référence de transaction (envoyée par SMS au patient
 * ou affichée dans l'app de l'opérateur) dans le champ {@code paymentReference}
 * de l'invoice. L'intégration API directe avec les opérateurs (callbacks,
 * webhooks) est un chantier hors-scope MVP.
 *
 * <p>La liste des méthodes effectivement activées dans une instance TerangaMed
 * est paramétrée via {@code terangamed.finance.enabled-payment-methods} dans
 * Config Server — les cabinets peuvent restreindre selon leurs partenariats.
 */
public enum PaymentMethod {

    CASH("Espèces", PaymentCategory.CASH, false),
    CARD("Carte bancaire", PaymentCategory.CARD, true),
    BANK_TRANSFER("Virement bancaire", PaymentCategory.BANK, true),
    CHECK("Chèque", PaymentCategory.BANK, true),

    // ─── Mobile money — opérateurs sénégalais ───
    WAVE("Wave", PaymentCategory.MOBILE_MONEY, true),
    ORANGE_MONEY("Orange Money", PaymentCategory.MOBILE_MONEY, true),
    FREE_MONEY("Free Money", PaymentCategory.MOBILE_MONEY, true),
    WIZALL_MONEY("Wizall Money", PaymentCategory.MOBILE_MONEY, true),
    YAS_MONEY("Yas Money", PaymentCategory.MOBILE_MONEY, true),

    OTHER("Autre", PaymentCategory.OTHER, true);

    private final String displayName;
    private final PaymentCategory category;
    private final boolean requiresReference;

    PaymentMethod(String displayName, PaymentCategory category, boolean requiresReference) {
        this.displayName = displayName;
        this.category = category;
        this.requiresReference = requiresReference;
    }

    public String getDisplayName() {
        return displayName;
    }

    public PaymentCategory getCategory() {
        return category;
    }

    /**
     * {@code true} si une référence externe doit être saisie pour ce mode
     * (numéro de transaction Wave, n° de chèque, n° de carte tronqué, etc.).
     * Seul {@code CASH} ne nécessite pas de référence.
     */
    public boolean isRequiresReference() {
        return requiresReference;
    }

    public boolean isMobileMoney() {
        return category == PaymentCategory.MOBILE_MONEY;
    }
}
