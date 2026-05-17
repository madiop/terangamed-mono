package com.terangamed.common.finance;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMethodTest {

    @Test
    void cash_is_only_method_without_reference() {
        assertThat(PaymentMethod.CASH.isRequiresReference()).isFalse();
        assertThat(PaymentMethod.CARD.isRequiresReference()).isTrue();
        assertThat(PaymentMethod.WAVE.isRequiresReference()).isTrue();
        assertThat(PaymentMethod.CHECK.isRequiresReference()).isTrue();
    }

    @Test
    void senegalese_mobile_money_operators_are_present() {
        assertThat(PaymentMethod.WAVE.isMobileMoney()).isTrue();
        assertThat(PaymentMethod.ORANGE_MONEY.isMobileMoney()).isTrue();
        assertThat(PaymentMethod.FREE_MONEY.isMobileMoney()).isTrue();
        assertThat(PaymentMethod.WIZALL_MONEY.isMobileMoney()).isTrue();
        assertThat(PaymentMethod.YAS_MONEY.isMobileMoney()).isTrue();
    }

    @Test
    void categories_are_correctly_assigned() {
        assertThat(PaymentMethod.CASH.getCategory()).isEqualTo(PaymentCategory.CASH);
        assertThat(PaymentMethod.CARD.getCategory()).isEqualTo(PaymentCategory.CARD);
        assertThat(PaymentMethod.BANK_TRANSFER.getCategory()).isEqualTo(PaymentCategory.BANK);
        assertThat(PaymentMethod.CHECK.getCategory()).isEqualTo(PaymentCategory.BANK);
        assertThat(PaymentMethod.OTHER.getCategory()).isEqualTo(PaymentCategory.OTHER);
    }

    @Test
    void display_names_are_human_readable() {
        assertThat(PaymentMethod.WAVE.getDisplayName()).isEqualTo("Wave");
        assertThat(PaymentMethod.ORANGE_MONEY.getDisplayName()).isEqualTo("Orange Money");
        assertThat(PaymentMethod.CASH.getDisplayName()).isEqualTo("Espèces");
    }

    @Test
    void only_mobile_money_methods_are_categorized_as_such() {
        for (PaymentMethod method : PaymentMethod.values()) {
            boolean expectedMobileMoney = method.getCategory() == PaymentCategory.MOBILE_MONEY;
            assertThat(method.isMobileMoney()).isEqualTo(expectedMobileMoney);
        }
    }
}
