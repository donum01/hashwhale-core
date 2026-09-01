package com.hashwhale.core.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.EarnPosition;
import com.hashwhale.core.entity.User;
import com.hashwhale.core.entity.WalletBalance;
import com.hashwhale.core.repository.EarnPositionRepository;
import com.hashwhale.core.repository.UserRepository;
import com.hashwhale.core.repository.WalletBalanceRepository;
import com.hashwhale.core.security.JwtService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
class EarnControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletBalanceRepository walletBalanceRepository;

    @Autowired
    private EarnPositionRepository earnPositionRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void allEarnEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/earn/products")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/earn/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/earn/positions")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/earn/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"USDT_FLEXIBLE\",\"amount\":100}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/earn/positions/1/withdraw"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    @Rollback
    void subscribeListSummarizeAndWithdrawFlexiblePosition() throws Exception {
        User user = savedUser("earn-flow@example.com");
        walletBalanceRepository.saveAndFlush(balance(user, "2000", "0"));
        String token = jwtService.generateToken(user);

        mockMvc.perform(get("/api/earn/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9))
                .andExpect(jsonPath("$[6].id").value("USDT_FLEXIBLE"))
                .andExpect(jsonPath("$[6].apy").value(4.50));

        mockMvc.perform(post("/api/earn/positions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"USDT_FLEXIBLE\",\"amount\":1000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.asset").value("USDT"))
                .andExpect(jsonPath("$.principalAmount").value(1000))
                .andExpect(jsonPath("$.termType").value("FLEXIBLE"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.withdrawable").value(true));

        EarnPosition position = earnPositionRepository
                .findByUserIdAndStatus(user.getId(), com.hashwhale.core.entity.EarnPositionStatus.ACTIVE)
                .getFirst();

        mockMvc.perform(get("/api/earn/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrincipalUsd").value(1000))
                .andExpect(jsonPath("$.activePositions").value(1))
                .andExpect(jsonPath("$.weightedAverageApy").value(4.50));

        mockMvc.perform(get("/api/earn/positions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(position.getId()));

        mockMvc.perform(post("/api/earn/positions/{positionId}/withdraw", position.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.endDate").isNotEmpty());

        WalletBalance updatedBalance = walletBalanceRepository
                .findByUserIdAndAsset(user.getId(), Asset.USDT)
                .orElseThrow();
        assertEquals(0, updatedBalance.getAvailableAmount().compareTo(new BigDecimal("2000")));
        assertEquals(0, updatedBalance.getLockedAmount().compareTo(BigDecimal.ZERO));
    }

    @Test
    @Transactional
    @Rollback
    void anotherUserCannotWithdrawAnEarnPosition() throws Exception {
        User owner = savedUser("earn-owner@example.com");
        User attacker = savedUser("earn-attacker@example.com");
        walletBalanceRepository.saveAndFlush(balance(owner, "1000", "0"));
        String ownerToken = jwtService.generateToken(owner);
        String attackerToken = jwtService.generateToken(attacker);

        mockMvc.perform(post("/api/earn/positions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"USDT_FLEXIBLE\",\"amount\":500}"))
                .andExpect(status().isCreated());
        EarnPosition position = earnPositionRepository
                .findByUserIdAndStatus(owner.getId(), com.hashwhale.core.entity.EarnPositionStatus.ACTIVE)
                .getFirst();

        mockMvc.perform(post("/api/earn/positions/{positionId}/withdraw", position.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + attackerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "You are not authorized to withdraw this Earn position"));
    }

    private User savedUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("not-used-by-this-test");
        return userRepository.saveAndFlush(user);
    }

    private WalletBalance balance(User user, String availableAmount, String lockedAmount) {
        WalletBalance balance = new WalletBalance();
        balance.setUser(user);
        balance.setAsset(Asset.USDT);
        balance.setAvailableAmount(new BigDecimal(availableAmount));
        balance.setLockedAmount(new BigDecimal(lockedAmount));
        return balance;
    }
}
