package com.hashwhale.core.demo;

public record DemoSeedResult(
        Long userId,
        String email,
        int walletBalanceCount,
        int loanCount,
        int earnPositionCount,
        int transactionCount) {
}
