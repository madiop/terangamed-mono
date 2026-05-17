package com.terangamed.common.finance;

import com.terangamed.common.config.CommonLibAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class FinancePropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonLibAutoConfiguration.class));

    @Test
    void defaults_are_xof_and_senegalese_mobile_money() {
        runner.run(ctx -> {
            FinanceProperties props = ctx.getBean(FinanceProperties.class);
            assertThat(props.getDefaultCurrency()).isEqualTo(Currency.XOF);
            assertThat(props.getEnabledPaymentMethods())
                    .contains(PaymentMethod.CASH, PaymentMethod.WAVE,
                              PaymentMethod.ORANGE_MONEY, PaymentMethod.FREE_MONEY);
        });
    }

    @Test
    void can_be_overridden_via_properties() {
        runner.withPropertyValues(
                        "terangamed.finance.default-currency=EUR",
                        "terangamed.finance.supported-currencies=EUR,USD",
                        "terangamed.finance.enabled-payment-methods=CASH,CARD")
                .run(ctx -> {
                    FinanceProperties props = ctx.getBean(FinanceProperties.class);
                    assertThat(props.getDefaultCurrency()).isEqualTo(Currency.EUR);
                    assertThat(props.getSupportedCurrencies())
                            .containsExactly(Currency.EUR, Currency.USD);
                    assertThat(props.getEnabledPaymentMethods())
                            .containsExactly(PaymentMethod.CASH, PaymentMethod.CARD);
                });
    }
}
