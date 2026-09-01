package com.hashwhale.core.entity;

import lombok.Getter;

/**
 * ISO 3166-1 alpha-2 country codes currently offered during registration.
 * Each country owns the server-side mapping to its default fiat quote currency.
 */
@Getter
public enum CountryCode {
    US(FiatCurrency.USD),
    GB(FiatCurrency.GBP),
    CA(FiatCurrency.CAD),
    DE(FiatCurrency.EUR),
    FR(FiatCurrency.EUR),
    NL(FiatCurrency.EUR),
    SG(FiatCurrency.SGD),
    JP(FiatCurrency.JPY),
    AU(FiatCurrency.AUD),
    AE(FiatCurrency.AED),
    CH(FiatCurrency.CHF),
    PH(FiatCurrency.PHP);

    private final FiatCurrency fiatCurrency;

    CountryCode(FiatCurrency fiatCurrency) {
        this.fiatCurrency = fiatCurrency;
    }
}
