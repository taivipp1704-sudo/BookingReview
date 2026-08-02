package com.claritycam.platform.controller.auth;

import com.claritycam.platform.service.common.ClientAddressResolver;
import com.claritycam.platform.service.common.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthenticationManager authenticationManager;
  private final SecurityContextRepository securityContextRepository;
  private final RateLimitService rateLimit;
  private final ClientAddressResolver clientAddressResolver;

  public AuthController(
      AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository,
      RateLimitService rateLimit,
      ClientAddressResolver clientAddressResolver) {
    this.authenticationManager = authenticationManager;
    this.securityContextRepository = securityContextRepository;
    this.rateLimit = rateLimit;
    this.clientAddressResolver = clientAddressResolver;
  }

  @GetMapping("/csrf")
  CsrfResponse csrf(CsrfToken token) {
    return new CsrfResponse(token.getToken(), token.getHeaderName());
  }

  @PostMapping("/login")
  SessionResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
    String email = request.email().trim().toLowerCase();
    rateLimit.check("admin-login:ip:" + clientAddressResolver.resolve(servletRequest), 10, Duration.ofMinutes(15));
    rateLimit.check("admin-login:email:" + email, 8, Duration.ofMinutes(15));
    Authentication authenticated = authenticationManager.authenticate(
        UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authenticated);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, servletRequest, servletResponse);
    return SessionResponse.from(authenticated);
  }

  @GetMapping("/me")
  SessionResponse me(Authentication authentication) {
    return SessionResponse.from(authentication);
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void logout(HttpServletRequest request, HttpServletResponse response) {
    if (request.getSession(false) != null) {
      request.getSession(false).invalidate();
    }
    SecurityContextHolder.clearContext();
    response.setHeader("Clear-Site-Data", "\"cache\", \"cookies\", \"storage\"");
  }

  public record LoginRequest(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(max = 128) String password) {}
  public record CsrfResponse(String token, String headerName) {}
  public record SessionResponse(String email, String role) {
    static SessionResponse from(Authentication authentication) {
      String role = authentication.getAuthorities().stream()
          .findFirst()
          .map(authority -> authority.getAuthority().replace("ROLE_", ""))
          .orElse("STAFF");
      return new SessionResponse(authentication.getName(), role);
    }
  }
}
