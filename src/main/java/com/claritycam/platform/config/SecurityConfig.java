package com.claritycam.platform.config;

import com.claritycam.platform.model.finance.Payment;
import com.claritycam.platform.repository.auth.AdminUserRepository;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService userDetailsService(AdminUserRepository users) {
    return email -> users.findByEmailIgnoreCase(email)
        .filter(user -> user.isActive())
        .map(user -> User.withUsername(user.getEmail())
            .password(user.getPasswordHash())
            .roles(user.getRole())
            .build())
        .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("Unknown user"));
  }

  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
    provider.setUserDetailsService(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      SecurityContextRepository securityContextRepository,
      CorsConfigurationSource corsConfigurationSource) throws Exception {
    CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfRepository.setCookiePath("/");

    return http
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository))
        .securityContext(context -> context.securityContextRepository(securityContextRepository)
            .requireExplicitSave(true))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            .sessionFixation(fixation -> fixation.migrateSession()))
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .headers(headers -> headers
            .contentTypeOptions(Customizer.withDefaults())
            .frameOptions(frame -> frame.deny())
            .contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"))
            .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                "camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
            .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Opener-Policy", "same-origin"))
            .referrerPolicy(policy -> policy.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
        .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
            .requestMatchers("/actuator/metrics", "/actuator/metrics/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/features", "/api/catalog/**", "/api/media/catalog/**",
                "/api/stores", "/api/auth/csrf", "/api/customer/support").permitAll()
            .requestMatchers(HttpMethod.GET,
                "/api/customer/account/me",
                "/api/customer/account/bookings",
                "/api/customer/account/bookings/*/identity/*",
                "/api/customer/account/bookings/*/payment-proof",
                "/api/bookings/holds",
                "/api/bookings/holds/*").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/customer/waitlist", "/api/auth/login", "/api/otp/**",
                "/api/bookings", "/api/bookings/quote", "/api/bookings/hold", "/api/bookings/hold/release",
                "/api/bookings/hold/payment-proof",
                "/api/bookings/track", "/api/customer/support").permitAll()
            .requestMatchers(HttpMethod.POST,
                "/api/customer/account/login",
                "/api/customer/account/register",
                "/api/customer/account/logout",
                "/api/customer/account/onboarding/complete",
                "/api/customer/account/password/change",
                "/api/customer/account/identity-documents",
                "/api/customer/account/payment-proof").permitAll()
            .requestMatchers("/api/admin/bookings/*/identity/*").hasAnyRole("ADMIN", "MANAGER")
            .requestMatchers("/api/admin/customer-accounts/**").hasRole("ADMIN")
            .requestMatchers("/api/admin/users/**").hasRole("ADMIN")
            .requestMatchers("/api/admin/inventory/**").hasAnyRole("ADMIN", "MANAGER", "WAREHOUSE", "TECH")
            .requestMatchers("/api/admin/finance/**", "/api/finance/**").hasAnyRole("ADMIN", "MANAGER")
            .requestMatchers("/api/admin/promotions/**").hasAnyRole("ADMIN", "MANAGER", "SALES")
            .requestMatchers("/api/admin/support-requests/**").hasAnyRole("ADMIN", "MANAGER", "SALES", "OPS")
            .requestMatchers("/api/admin/bookings/**").hasAnyRole("ADMIN", "MANAGER", "SALES", "OPS", "WAREHOUSE")
            .requestMatchers("/api/auth/me", "/api/auth/logout", "/api/admin/**", "/api/inventory/**", "/api/finance/**")
            .hasAnyRole("STAFF", "ADMIN", "MANAGER", "SALES", "OPS", "WAREHOUSE", "TECH")
            .anyRequest().denyAll())
        .build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(@Value("${claritycam.cors-origins}") String configuredOrigins) {
    CorsConfiguration configuration = new CorsConfiguration();
    List<String> origins = Arrays.stream(configuredOrigins.split(","))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .toList();
    configuration.setAllowedOrigins(origins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Accept", "Content-Type", "X-XSRF-TOKEN", "X-Requested-With"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }
}
