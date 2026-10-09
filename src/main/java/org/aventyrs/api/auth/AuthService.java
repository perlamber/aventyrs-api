package org.aventyrs.api.auth;

import org.aventyrs.api.auth.dto.LoginResponse;
import org.aventyrs.api.player.PlayerDocument;
import org.aventyrs.api.player.PlayerRepository;
import org.aventyrs.api.player.PlayerRole;
import org.aventyrs.api.player.dto.PlayerResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    /**
     * Compared against when the login doesn't exist (or has no password), so a miss costs the
     * same BCrypt work as a wrong password and response timing doesn't reveal which logins exist.
     */
    private static final String DUMMY_HASH = "$2a$10$nBFMkkTU.kSN3p9EnUTlQeADlM5Mm0F0m9xx8kB9TLF1h8llLYwbS";

    private final PlayerRepository players;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(PlayerRepository players, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.players = players;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    public LoginResponse login(String login, String password) {
        PlayerDocument player = players.findByLogin(login).orElse(null);
        String hash = player == null || player.getPasswordHash() == null ? null : player.getPasswordHash();
        boolean matches = passwordEncoder.matches(password, hash == null ? DUMMY_HASH : hash);
        if (hash == null || !matches) {
            throw new InvalidCredentialsException();
        }
        PlayerRole role = player.getRole() == null ? PlayerRole.PLAYER : player.getRole();
        TokenService.IssuedToken issued = tokenService.issue(player, role);
        return new LoginResponse(issued.token(), "Bearer", issued.expiresAt(),
                new PlayerResponse(player.getId(), player.getName(), player.getLogin(), role));
    }
}
