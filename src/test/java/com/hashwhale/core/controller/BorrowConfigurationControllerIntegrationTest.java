package com.hashwhale.core.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BorrowConfigurationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void configurationRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/borrow/configuration"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void configurationReturnsTheEffectiveBorrowEngineValues() throws Exception {
        mockMvc.perform(get("/api/borrow/configuration"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.usdPrices.BTC").value(60000))
                .andExpect(jsonPath("$.usdPrices.ETH").value(3000))
                .andExpect(jsonPath("$.usdPrices.USDT").value(1))
                .andExpect(jsonPath("$.priceSource").value("STATIC"))
                .andExpect(jsonPath("$.pricesStale").value(false))
                .andExpect(jsonPath("$.interestRateApr").value(2.88))
                .andExpect(jsonPath("$.maxLtvPercent").value(70))
                .andExpect(jsonPath("$.warningLtvPercent").value(50))
                .andExpect(jsonPath("$.liquidationLtvPercent").value(85));
    }
}
