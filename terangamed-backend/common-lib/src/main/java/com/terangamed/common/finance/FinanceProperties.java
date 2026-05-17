package com.terangamed.common.finance;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Propriétés financières paramétrables pour TerangaMed.
 *
 * <p>Lues depuis Config Server (ou {@code application.yml} en fallback) :
 * <pre>
 * terangamed:
 *   finance:
 *     default-currency: XOF
 *     supported-currencies: [XOF, EUR, USD]
 *     enabled-payment-methods:
 *       - CASH
 *       - CARD
 *       - WAVE
 *       - ORANGE_MONEY
 *       - FREE_MONEY
 *       - BANK_TRANSFER
 *       - CHECK
 * </pre>
 *
 * <p>Le {@code billing-service} (étape 8) utilisera ces propriétés pour exposer
 * un endpoint {@code GET /api/payment-methods} qui ne renvoie que les options
 * activées dans la configuration en cours.
 *
 * <p>Les services qui utilisent uniquement {@code defaultCurrency} (comme
 * {@code doctor-service} pour les tarifs de consultation) peuvent l'injecter
 * directement.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "terangamed.finance")
public class FinanceProperties {

    /**
     * Devise par défaut pour les nouveaux montants saisis. {@code XOF} (FCFA)
     * pour le marché sénégalais.
     */
    private Currency defaultCurrency = Currency.XOF;

    /**
     * Devises acceptées en saisie. Permet aux cabinets multi-zones (international,
     * patients étrangers) d'accepter EUR/USD en plus de XOF.
     */
    private List<Currency> supportedCurrencies = List.of(Currency.XOF);

    /**
     * Moyens de paiement activés dans cette instance. Permet à un cabinet sans
     * partenariat Wave (par exemple) de masquer cette option dans l'UI.
     */
    private List<PaymentMethod> enabledPaymentMethods = List.of(
            PaymentMethod.CASH,
            PaymentMethod.CARD,
            PaymentMethod.WAVE,
            PaymentMethod.ORANGE_MONEY,
            PaymentMethod.FREE_MONEY,
            PaymentMethod.BANK_TRANSFER,
            PaymentMethod.CHECK
    );
}
