package com.terangamed.common.finance;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyTest {

    @Test
    void xof_default_currency_for_senegal_market() {
        assertThat(Currency.XOF.getSymbol()).isEqualTo("FCFA");
        assertThat(Currency.XOF.getDefaultScale()).isZero();   // pas de décimales pour FCFA
        assertThat(Currency.XOF.getDisplayName()).contains("UEMOA", "Sénégal");
    }

    @Test
    void xaf_franc_cfa_beac_uses_same_symbol_but_distinct_currency() {
        assertThat(Currency.XAF.getSymbol()).isEqualTo("FCFA");
        assertThat(Currency.XAF.getDefaultScale()).isZero();
        assertThat(Currency.XAF.getDisplayName()).contains("CEMAC");
        assertThat(Currency.XAF).isNotEqualTo(Currency.XOF);
    }

    @Test
    void eur_and_usd_have_two_decimals() {
        assertThat(Currency.EUR.getSymbol()).isEqualTo("€");
        assertThat(Currency.EUR.getDefaultScale()).isEqualTo(2);
        assertThat(Currency.USD.getSymbol()).isEqualTo("$");
        assertThat(Currency.USD.getDefaultScale()).isEqualTo(2);
    }
}
