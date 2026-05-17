package com.terangamed.common.finance;

/**
 * Devises ISO 4217 supportées par TerangaMed.
 *
 * <p><b>XOF</b> (Franc CFA BCEAO) est la devise par défaut — utilisée par les 8 pays
 * de l'UEMOA dont le Sénégal, premier marché cible. En pratique, le FCFA n'utilise
 * pas de décimales (10 FCFA s'affiche « 10 », pas « 10,00 ») d'où {@code defaultScale = 0}.
 *
 * <p><b>XAF</b> (Franc CFA BEAC) est la devise jumelle pour la zone CEMAC (Cameroun,
 * Tchad, ...) — incluse pour faciliter une expansion future. Mêmes propriétés
 * d'affichage que XOF mais devise distincte (banque centrale différente, taux
 * de change identique au moment de l'écriture).
 *
 * <p>Le symbole d'affichage du Franc CFA est « FCFA » au Sénégal (préféré à « CFA » seul).
 */
public enum Currency {

    XOF("FCFA", "Franc CFA BCEAO (UEMOA — Sénégal, Côte d'Ivoire, Mali, ...)", 0),
    XAF("FCFA", "Franc CFA BEAC (CEMAC — Cameroun, Tchad, Gabon, ...)", 0),
    EUR("€",    "Euro", 2),
    USD("$",    "US Dollar", 2);

    private final String symbol;
    private final String displayName;
    private final int defaultScale;

    Currency(String symbol, String displayName, int defaultScale) {
        this.symbol = symbol;
        this.displayName = displayName;
        this.defaultScale = defaultScale;
    }

    /** Symbole d'affichage (« FCFA », « € », ...). */
    public String getSymbol() {
        return symbol;
    }

    /** Nom complet pour la documentation et l'UI. */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Nombre de décimales d'affichage par défaut. {@code 0} pour XOF/XAF
     * (jamais utilisées en pratique), {@code 2} pour EUR/USD.
     */
    public int getDefaultScale() {
        return defaultScale;
    }
}
