package com.healthcare.controller;

import com.healthcare.dto.CsrfResponse;
import com.healthcare.dto.LoginResponse;
import com.healthcare.dto.MeResponse;
import com.healthcare.model.UserRole;
import com.healthcare.service.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final String INVALID_CREDENTIALS = "Invalid credentials";

    private final AuthenticationManager authenticationManager;
    private final LoginAttemptService loginAttemptService;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(
            AuthenticationManager authenticationManager,
            LoginAttemptService loginAttemptService,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository) {
        this.authenticationManager = authenticationManager;
        this.loginAttemptService = loginAttemptService;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
    }

    @GetMapping("/admin/login")
    public String adminLoginPage() {
        return "forward:/admin/login.html";
    }

    @GetMapping("/staff/login")
    public String staffLoginPage() {
        return "forward:/staff/login.html";
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard() {
        return "forward:/admin/dashboard.html";
    }

    @GetMapping("/staff/dashboard")
    public String staffDashboard() {
        return "forward:/staff/dashboard.html";
    }

    @GetMapping("/api/csrf")
    @ResponseBody
    public CsrfResponse csrfToken(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getToken(), csrfToken.getHeaderName());
    }

    @GetMapping("/api/me")
    @ResponseBody
    public MeResponse currentUser(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Authenticated user has no role"))
                .getAuthority()
                .replaceFirst("^ROLE_", "");
        return new MeResponse(authentication.getName(), role);
    }

    @PostMapping("/admin/login")
    @ResponseBody
    public ResponseEntity<LoginResponse> adminLogin(
            @RequestParam String username,
            @RequestParam String password,
            HttpServletRequest request,
            HttpServletResponse response) {
        return login(username, password, UserRole.ADMIN, "/admin/dashboard", request, response);
    }

    @PostMapping("/staff/login")
    @ResponseBody
    public ResponseEntity<LoginResponse> staffLogin(
            @RequestParam String username,
            @RequestParam String password,
            HttpServletRequest request,
            HttpServletResponse response) {
        return login(username, password, UserRole.STAFF, "/staff/dashboard", request, response);
    }

    @PostMapping("/admin/logout")
    public String adminLogout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return "redirect:/admin/login";
    }

    @PostMapping("/staff/logout")
    public String staffLogout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return "redirect:/staff/login";
    }

    private ResponseEntity<LoginResponse> login(
            String username,
            String password,
            UserRole expectedRole,
            String redirect,
            HttpServletRequest request,
            HttpServletResponse response) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
        } catch (AuthenticationException exception) {
            loginAttemptService.recordFailure(username);
            log.warn("Login failed for user {}", username);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(null, INVALID_CREDENTIALS));
        }

        boolean hasExpectedRole = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + expectedRole.name()));
        if (!hasExpectedRole) {
            loginAttemptService.recordFailure(username);
            log.warn("Login failed for user {} on {} login", username, expectedRole);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(null, INVALID_CREDENTIALS));
        }

        loginAttemptService.recordSuccess(username);
        request.getSession(true);
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        log.info("Login succeeded for user {} as {}", username, expectedRole);
        return ResponseEntity.ok(new LoginResponse(redirect, null));
    }
}
