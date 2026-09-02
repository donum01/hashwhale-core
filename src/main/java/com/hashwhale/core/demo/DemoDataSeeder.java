package com.hashwhale.core.demo;

import com.hashwhale.core.config.DemoDataProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Runs the destructive reset only when both the demo profile and explicit enable flag are used. */
@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.demo-seed", name = "enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private final DemoDataProperties properties;
    private final DemoDataService demoDataService;

    @Override
    public void run(ApplicationArguments arguments) {
        if (!properties.isReset()) {
            log.warn("Demo seeding is enabled, but reset was not confirmed; no user data was changed");
            return;
        }

        log.warn("Resetting user-owned data and creating the interview demo account");
        DemoSeedResult result = demoDataService.reset(properties.getEmail(), properties.getPassword());
        log.info(
                "Demo account ready: email={}, userId={}, balances={}, loans={}, earnPositions={}, transactions={}",
                result.email(),
                result.userId(),
                result.walletBalanceCount(),
                result.loanCount(),
                result.earnPositionCount(),
                result.transactionCount());
    }
}
