package com.hashwhale.core.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hashwhale.core.entity.CountryCode;
import com.hashwhale.core.entity.FiatCurrency;
import com.hashwhale.core.entity.User;
import com.hashwhale.core.repository.UserRepository;
import com.hashwhale.core.security.JwtService;
import java.util.UUID;
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
class AuthCountryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    @Transactional
    @Rollback
    void registrationPersistsCountryAndMeReturnsItsDefaultFiatCurrency() throws Exception {
        String email = "country-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123",
                                  "countryCode": "PH"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail(email).orElseThrow();
        assertNotNull(user.getPasswordHash());
        assertEquals(CountryCode.PH, user.getCountryCode());
        assertEquals(FiatCurrency.PHP, user.getPreferredFiatCurrency());

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countryCode").value("PH"))
                .andExpect(jsonPath("$.preferredFiatCurrency").value("PHP"));
    }

    @Test
    void registrationRejectsMissingCountry() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "missing-country@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
