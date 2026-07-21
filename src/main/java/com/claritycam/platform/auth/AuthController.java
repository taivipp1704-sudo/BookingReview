package com.claritycam.platform.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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

  public AuthController(AuthenticationManager authenticationManager, SecurityContextRepository securityContextRepository) {
    this.authenticationManager = authenticationManager;
    this.securityContextRepository = securityContextRepository;
  }

  @GetMapping("/csrf")
  CsrfResponse csrf(CsrfToken token) {
    return new CsrfResponse(token.getToken(), token.getHeaderName());
  }

  @PostMapping("/login")
  SessionResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
    Authentication authenticated = authenticationManager.authenticate(
        UsernamePasswordAuthenticationToken.unauthenticated(request.email().trim().toLowerCase(), request.password()));
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
  void logout(HttpServletRequest request) {
    if (request.getSession(false) != null) {
      request.getSession(false).invalidate();
    }
    SecurityContextHolder.clearContext();
  }

  public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
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
