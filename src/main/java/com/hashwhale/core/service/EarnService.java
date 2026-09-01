package com.hashwhale.core.service;

import com.hashwhale.core.config.EarnConfigurationProperties;
import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.EarnPosition;
import com.hashwhale.core.entity.EarnPositionStatus;
import com.hashwhale.core.entity.EarnTermType;
import com.hashwhale.core.entity.Transaction;
import com.hashwhale.core.entity.TransactionStatus;
import com.hashwhale.core.entity.TransactionType;
import com.hashwhale.core.entity.User;
import com.hashwhale.core.entity.WalletBalance;
import com.hashwhale.core.repository.EarnPositionRepository;
import com.hashwhale.core.repository.TransactionRepository;
import com.hashwhale.core.repository.UserRepository;
import com.hashwhale.core.repository.WalletBalanceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EarnService {

    private static final BigDecimal PERCENT_DENOMINATOR = new BigDecimal("100");
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");
    private static final int MONEY_SCALE = 18;
    private static final int SUMMARY_SCALE = 8;

    private final UserRepository userRepository;
    private final WalletBalanceRepository walletBalanceRepository;
    private final EarnPositionRepository earnPositionRepository;
    private final TransactionRepository transactionRepository;
    private final EarnConfigurationProperties configuration;
    private final PriceService priceService;
    private final Clock applicationClock;

    public List<EarnProduct> getProducts() {
        return configuration.getProducts().entrySet().stream()
                .map(entry -> toProduct(entry.getKey(), entry.getValue()))
                .filter(EarnProduct::active)
                .sorted(Comparator
                        .comparing((EarnProduct product) -> product.asset().ordinal())
                        .thenComparing(product -> product.termType().ordinal()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EarnPosition> getPositions(Long userId) {
        validateUserId(userId);
        return earnPositionRepository.findByUserIdOrderByStartDateDescIdDesc(userId);
    }

    @Transactional(readOnly = true)
    public EarnSummary getSummary(Long userId) {
        validateUserId(userId);
        List<EarnPosition> positions = earnPositionRepository
                .findByUserIdAndStatus(userId, EarnPositionStatus.ACTIVE);
        LocalDate today = today();
        Map<Asset, BigDecimal> prices = new EnumMap<>(Asset.class);

        BigDecimal principalUsd = BigDecimal.ZERO;
        BigDecimal rewardsUsd = BigDecimal.ZERO;
        BigDecimal weightedApyNumerator = BigDecimal.ZERO;
        LocalDate nextMaturity = null;

        for (EarnPosition position : positions) {
            BigDecimal assetPrice = prices.computeIfAbsent(position.getAsset(), priceService::getUsdPrice);
            BigDecimal positionPrincipalUsd = position.getPrincipalAmount().multiply(assetPrice);
            BigDecimal positionRewardsUsd = calculateAccruedRewards(position, today).multiply(assetPrice);

            principalUsd = principalUsd.add(positionPrincipalUsd);
            rewardsUsd = rewardsUsd.add(positionRewardsUsd);
            weightedApyNumerator = weightedApyNumerator.add(positionPrincipalUsd.multiply(position.getApy()));

            if (position.getTermType() != EarnTermType.FLEXIBLE
                    && position.getEndDate() != null
                    && (nextMaturity == null || position.getEndDate().isBefore(nextMaturity))) {
                nextMaturity = position.getEndDate();
            }
        }

        BigDecimal weightedApy = principalUsd.signum() == 0
                ? BigDecimal.ZERO
                : weightedApyNumerator.divide(principalUsd, SUMMARY_SCALE, RoundingMode.HALF_UP);
        return new EarnSummary(
                principalUsd.setScale(SUMMARY_SCALE, RoundingMode.HALF_UP),
                rewardsUsd.setScale(SUMMARY_SCALE, RoundingMode.HALF_UP),
                weightedApy,
                positions.size(),
                nextMaturity);
    }

    @Transactional
    public EarnPosition subscribe(Long userId, String productId, BigDecimal amount) {
        validateUserId(userId);
        validatePositive(amount, "Amount");
        EarnProduct product = findActiveProduct(productId);
        if (amount.compareTo(product.minimumAmount()) < 0) {
            throw new IllegalArgumentException(
                    "Minimum subscription for " + product.id() + " is "
                            + product.minimumAmount() + " " + product.asset());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        WalletBalance balance = walletBalanceRepository
                .findByUserIdAndAssetForUpdate(userId, product.asset())
                .orElseThrow(() -> new InsufficientBalanceException(
                        "No wallet balance exists for asset " + product.asset()));
        if (balance.getAvailableAmount().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient available " + product.asset() + " balance");
        }

        balance.setAvailableAmount(balance.getAvailableAmount().subtract(amount));
        balance.setLockedAmount(balance.getLockedAmount().add(amount));
        walletBalanceRepository.save(balance);

        LocalDate startDate = today();
        EarnPosition position = new EarnPosition();
        position.setUser(user);
        position.setAsset(product.asset());
        position.setPrincipalAmount(amount);
        position.setApy(product.apy());
        position.setTermType(product.termType());
        position.setStartDate(startDate);
        position.setEndDate(product.termType() == EarnTermType.FLEXIBLE
                ? null
                : startDate.plusDays(termDays(product.termType())));
        position.setStatus(EarnPositionStatus.ACTIVE);

        EarnPosition savedPosition = earnPositionRepository.save(position);
        transactionRepository.save(createTransaction(
                user, TransactionType.EARN_SUBSCRIBE, product.asset(), amount));
        return savedPosition;
    }

    @Transactional
    public EarnPosition withdraw(Long positionId, Long authenticatedUserId) {
        validateRequired(positionId, "Position id");
        validateUserId(authenticatedUserId);
        EarnPosition position = earnPositionRepository.findByIdForUpdate(positionId)
                .orElseThrow(() -> new IllegalArgumentException("Earn position not found: " + positionId));
        if (!position.getUser().getId().equals(authenticatedUserId)) {
            throw new ForbiddenException("You are not authorized to withdraw this Earn position");
        }
        if (position.getStatus() != EarnPositionStatus.ACTIVE) {
            throw new IllegalStateException("Only active Earn positions can be withdrawn");
        }

        LocalDate today = today();
        if (!isWithdrawable(position, today)) {
            throw new IllegalStateException(
                    "Locked Earn position cannot be withdrawn before " + position.getEndDate());
        }

        WalletBalance balance = walletBalanceRepository
                .findByUserIdAndAssetForUpdate(authenticatedUserId, position.getAsset())
                .orElseThrow(() -> new IllegalStateException("Earn wallet balance does not exist"));
        if (balance.getLockedAmount().compareTo(position.getPrincipalAmount()) < 0) {
            throw new IllegalStateException("Locked balance is less than the Earn principal amount");
        }

        BigDecimal rewards = calculateAccruedRewards(position, today);
        BigDecimal creditedAmount = position.getPrincipalAmount().add(rewards);
        balance.setLockedAmount(balance.getLockedAmount().subtract(position.getPrincipalAmount()));
        balance.setAvailableAmount(balance.getAvailableAmount().add(creditedAmount));
        if (position.getTermType() == EarnTermType.FLEXIBLE) {
            position.setEndDate(today);
        }
        position.setStatus(EarnPositionStatus.WITHDRAWN);

        walletBalanceRepository.save(balance);
        EarnPosition savedPosition = earnPositionRepository.save(position);
        transactionRepository.save(createTransaction(
                position.getUser(), TransactionType.EARN_WITHDRAW, position.getAsset(), creditedAmount));
        return savedPosition;
    }

    public BigDecimal calculateAccruedRewards(EarnPosition position) {
        return calculateAccruedRewards(position, today());
    }

    public BigDecimal calculateAccruedRewards(EarnPosition position, LocalDate asOfDate) {
        validateRequired(position, "Earn position");
        validateRequired(asOfDate, "Accrual date");
        validatePositive(position.getPrincipalAmount(), "Principal amount");
        validateRequired(position.getApy(), "APY");
        validateRequired(position.getStartDate(), "Start date");

        LocalDate accrualEnd = resolveAccrualEnd(position, asOfDate);
        long elapsedDays = Math.max(0, ChronoUnit.DAYS.between(position.getStartDate(), accrualEnd));
        if (elapsedDays == 0 || position.getApy().signum() == 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        return position.getPrincipalAmount()
                .multiply(position.getApy())
                .multiply(BigDecimal.valueOf(elapsedDays))
                .divide(PERCENT_DENOMINATOR.multiply(DAYS_PER_YEAR), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateEstimatedRewardsAtMaturity(EarnPosition position) {
        if (position.getTermType() == EarnTermType.FLEXIBLE || position.getEndDate() == null) {
            return null;
        }
        return calculateAccruedRewards(position, position.getEndDate());
    }

    public boolean isWithdrawable(EarnPosition position) {
        return isWithdrawable(position, today());
    }

    public Long daysRemaining(EarnPosition position) {
        if (position.getStatus() != EarnPositionStatus.ACTIVE
                || position.getTermType() == EarnTermType.FLEXIBLE
                || position.getEndDate() == null) {
            return null;
        }
        return Math.max(0, ChronoUnit.DAYS.between(today(), position.getEndDate()));
    }

    public int termDays(EarnTermType termType) {
        return switch (termType) {
            case FLEXIBLE -> 0;
            case LOCKED_30 -> 30;
            case LOCKED_90 -> 90;
        };
    }

    private EarnProduct findActiveProduct(String productId) {
        validateRequired(productId, "Product id");
        String normalizedId = productId.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        EarnConfigurationProperties.Product configuredProduct = configuration.getProducts().get(normalizedId);
        if (configuredProduct == null) {
            throw new IllegalArgumentException("Earn product not found: " + productId);
        }
        EarnProduct product = toProduct(normalizedId, configuredProduct);
        if (!product.active()) {
            throw new IllegalStateException("Earn product is not currently available");
        }
        return product;
    }

    private EarnProduct toProduct(String id, EarnConfigurationProperties.Product product) {
        return new EarnProduct(
                id,
                product.getAsset(),
                product.getTermType(),
                product.getApy(),
                product.getMinimumAmount(),
                product.isActive());
    }

    private LocalDate resolveAccrualEnd(EarnPosition position, LocalDate asOfDate) {
        if (position.getStatus() == EarnPositionStatus.WITHDRAWN && position.getEndDate() != null) {
            return position.getEndDate();
        }
        if (position.getTermType() != EarnTermType.FLEXIBLE
                && position.getEndDate() != null
                && asOfDate.isAfter(position.getEndDate())) {
            return position.getEndDate();
        }
        return asOfDate;
    }

    private boolean isWithdrawable(EarnPosition position, LocalDate asOfDate) {
        return position.getStatus() == EarnPositionStatus.ACTIVE
                && (position.getTermType() == EarnTermType.FLEXIBLE
                        || position.getEndDate() == null
                        || !asOfDate.isBefore(position.getEndDate()));
    }

    private Transaction createTransaction(
            User user, TransactionType type, Asset asset, BigDecimal amount) {
        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setType(type);
        transaction.setAsset(asset);
        transaction.setAmount(amount);
        transaction.setStatus(TransactionStatus.COMPLETED);
        return transaction;
    }

    private LocalDate today() {
        return LocalDate.now(applicationClock);
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("User id must be greater than zero");
        }
    }

    private void validatePositive(BigDecimal value, String fieldName) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }
    }

    private void validateRequired(Object value, String fieldName) {
        if (value == null || value instanceof String stringValue && stringValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
