package com.ai_dlc.workshop.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void actuatorHealth_noAuth_returns200() throws Exception {
        // GIVEN no authentication
        // WHEN GET /actuator/health
        // THEN 200 OK — permit-all on health endpoint
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void rootPath_noAuth_returns401() throws Exception {
        // GIVEN no authentication
        // WHEN GET /
        // THEN 401 or 403 — unauthenticated request is rejected
        mockMvc.perform(get("/"))
                .andExpect(status().is(org.hamcrest.Matchers.either(
                        org.hamcrest.Matchers.is(401)).or(
                        org.hamcrest.Matchers.is(403))));
    }

    @Test
    void apiPath_noAuth_returns401() throws Exception {
        // GIVEN no authentication
        // WHEN GET /api/anything
        // THEN 401 or 403 — unauthenticated request is rejected
        mockMvc.perform(get("/api/anything"))
                .andExpect(status().is(org.hamcrest.Matchers.either(
                        org.hamcrest.Matchers.is(401)).or(
                        org.hamcrest.Matchers.is(403))));
    }

    @Test
    @WithMockUser
    void actuatorHealth_withAuth_returns200() throws Exception {
        // GIVEN an authenticated user
        // WHEN GET /actuator/health
        // THEN 200 OK — permit-all means auth does not matter
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
