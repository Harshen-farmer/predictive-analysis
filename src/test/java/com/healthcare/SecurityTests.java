package com.healthcare;

import com.healthcare.model.AppUser;
import com.healthcare.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearLoginFailures() {
        for (String username : new String[]{"admin", "staff"}) {
            AppUser user = appUserRepository.findByUsername(username).orElseThrow();
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
            appUserRepository.save(user);
        }
    }

    @Test
    void adminCannotLogInThroughStaffPage() throws Exception {
        mockMvc.perform(post("/staff/login")
                        .with(csrf())
                        .param("username", "admin")
                        .param("password", "admin123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    void staffCannotLogInThroughAdminPage() throws Exception {
        mockMvc.perform(post("/admin/login")
                        .with(csrf())
                        .param("username", "staff")
                        .param("password", "staff123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    @WithMockUser(username = "staff", roles = "STAFF")
    void staffCannotAccessAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/reports"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.message").value("Forbidden"));
    }

    @Test
    void unauthenticatedApiAndPageRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Forbidden"));

        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));

        mockMvc.perform(get("/admin/dashboard.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));

        mockMvc.perform(get("/staff/dashboard.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/staff/login"));
    }

    @Test
    void loginPagesRemainPublic() throws Exception {
        mockMvc.perform(get("/admin/login.html"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/staff/login.html"))
                .andExpect(status().isOk());
    }

    @Test
    void locksAccountAfterFiveFailedLogins() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/admin/login")
                            .with(csrf())
                            .param("username", "admin")
                            .param("password", "wrong-password"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Invalid credentials"));
        }

        AppUser admin = appUserRepository.findByUsername("admin").orElseThrow();
        assertThat(admin.getFailedAttempts()).isEqualTo(5);
        assertThat(admin.getLockedUntil()).isNotNull().isAfter(java.time.LocalDateTime.now());

        mockMvc.perform(post("/admin/login")
                        .with(csrf())
                        .param("username", "admin")
                        .param("password", "admin123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    void adminLoginCreatesSessionAndReturnsDashboard() throws Exception {
        AppUser admin = appUserRepository.findByUsername("admin").orElseThrow();
        assertThat(admin.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches("admin123", admin.getPasswordHash())).isTrue();

        MvcResult result = mockMvc.perform(post("/admin/login")
                        .with(csrf())
                        .param("username", "admin")
                        .param("password", "admin123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirect").value("/admin/dashboard"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(post("/admin/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    void staffLoginReturnsStaffDashboardAndMatchingLogoutPage() throws Exception {
        MvcResult result = mockMvc.perform(post("/staff/login")
                        .with(csrf())
                        .param("username", "staff")
                        .param("password", "staff123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirect").value("/staff/dashboard"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("staff"))
                .andExpect(jsonPath("$.role").value("STAFF"));

        mockMvc.perform(post("/staff/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/staff/login"));
    }
}
