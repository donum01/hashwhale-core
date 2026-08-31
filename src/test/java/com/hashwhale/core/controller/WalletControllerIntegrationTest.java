package com.hashwhale.core.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hashwhale.core.entity.Asset;
import com.hashwhale.core.entity.User;
import com.hashwhale.core.entity.WalletBalance;
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
class WalletControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletBalanceRepository walletBalanceRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void getBalancesRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/wallet/1/balances"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void walletMutationAndTransactionEndpointsRequireAuthentication() throws Exception {
        String requestBody = """
                {"asset":"USDT","amount":100}
                """;

        mockMvc.perform(post("/api/wallet/1/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/wallet/1/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/wallet/1/transactions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    @Rollback
    void getBalancesReturnsSeededUserBalancesForValidJwt() throws Exception {
        User user = new User();
        user.setEmail("wallet-test@example.com");
        user.setPasswordHash("not-used-by-this-test");
        User savedUser = userRepository.saveAndFlush(user);

        walletBalanceRepository.saveAndFlush(balance(savedUser, Asset.BTC, "0.75000000", "0.25000000"));
        walletBalanceRepository.saveAndFlush(balance(savedUser, Asset.USDT, "1250.00", "100.00"));

        String token = jwtService.generateToken(savedUser);

        mockMvc.perform(get("/api/wallet/{userId}/balances", savedUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].asset").value("BTC"))
                .andExpect(jsonPath("$[0].availableAmount").value(0.75))
                .andExpect(jsonPath("$[0].lockedAmount").value(0.25))
                .andExpect(jsonPath("$[1].asset").value("USDT"))
                .andExpect(jsonPath("$[1].availableAmount").value(1250.00))
                .andExpect(jsonPath("$[1].lockedAmount").value(100.00));
    }

    @Test
    @Transactional
    @Rollback
    void depositWithdrawAndTransactionHistoryWorkForValidJwt() throws Exception {
        User user = new User();
        user.setEmail("wallet-mutations@example.com");
        user.setPasswordHash("not-used-by-this-test");
        User savedUser = userRepository.saveAndFlush(user);
        String token = jwtService.generateToken(savedUser);

        mockMvc.perform(post("/api/wallet/{userId}/deposit", savedUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"asset":"USDT","amount":500.50}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asset").value("USDT"))
                .andExpect(jsonPath("$.availableAmount").value(500.50))
                .andExpect(jsonPath("$.lockedAmount").value(0));

        mockMvc.perform(post("/api/wallet/{userId}/withdraw", savedUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"asset":"USDT","amount":125.25}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableAmount").value(375.25))
                .andExpect(jsonPath("$.lockedAmount").value(0));

        mockMvc.perform(get("/api/wallet/{userId}/transactions", savedUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("WITHDRAW"))
                .andExpect(jsonPath("$[0].asset").value("USDT"))
                .andExpect(jsonPath("$[0].amount").value(125.25))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$[1].type").value("DEPOSIT"))
                .andExpect(jsonPath("$[1].amount").value(500.50));
    }

    private WalletBalance balance(User user, Asset asset, String availableAmount, String lockedAmount) {
        WalletBalance balance = new WalletBalance();
        balance.setUser(user);
        balance.setAsset(asset);
        balance.setAvailableAmount(new BigDecimal(availableAmount));
        balance.setLockedAmount(new BigDecimal(lockedAmount));
        return balance;
    }
}
