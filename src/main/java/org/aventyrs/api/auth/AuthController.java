package org.aventyrs.api.auth;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.aventyrs.api.auth.dto.LoginRequest;
import org.aventyrs.api.auth.dto.LoginResponse;
import org.aventyrs.api.common.ApiError;
import org.aventyrs.api.player.PlayerService;
import org.aventyrs.api.player.dto.PlayerResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;
    private final PlayerService playerService;

    public AuthController(AuthService authService, PlayerService playerService) {
        this.authService = authService;
        this.playerService = playerService;
    }

    /** Public: exchanges a login and password for a bearer token. */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.login(), request.password());
    }

    /** The player the presented token belongs to. */
    @GetMapping("/me")
    public PlayerResponse me(@AuthenticationPrincipal Jwt jwt) {
        return playerService.get(jwt.getSubject());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                        ex.getMessage()));
    }
}
