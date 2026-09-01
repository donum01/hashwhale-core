package com.hashwhale.core.config;

import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.EarnTermType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "app.earn")
public class EarnConfigurationProperties {

    @NotEmpty
    private Map<String, @Valid Product> products = new LinkedHashMap<>();

    @AssertTrue(message = "app.earn.products must not contain duplicate asset and term combinations")
    public boolean hasUniqueAssetAndTermCombinations() {
        if (products == null) {
            return true;
        }
        return products.values().stream()
                .filter(product -> product.getAsset() != null && product.getTermType() != null)
                .collect(Collectors.toSet())
                .size() == products.values().stream()
                        .filter(product -> product.getAsset() != null && product.getTermType() != null)
                        .count();
    }

    @Getter
    @Setter
    public static class Product {

        @NotNull
        private Asset asset;

        @NotNull
        private EarnTermType termType;

        @NotNull
        @Positive
        private BigDecimal apy;

        @NotNull
        @Positive
        private BigDecimal minimumAmount;

        private boolean active = true;

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Product product)) {
                return false;
            }
            return asset == product.asset && termType == product.termType;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(asset, termType);
        }
    }
}
