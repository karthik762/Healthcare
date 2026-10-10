
package app.services;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import app.model.User;
import app.repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public Map<String, Object> register(
            String name,
            String email,
            String password) {

        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Name is required.");
        }

        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email is required.");
        }

        if (password == null || password.length() < 8) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must contain at least 8 characters.");
        }

        String cleanEmail = email.trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(cleanEmail)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An account with this email already exists.");
        }

        User user = new User();
        user.setName(name.trim());
        user.setEmail(cleanEmail);
        user.setPassword(passwordEncoder.encode(password));

        User savedUser = userRepository.save(user);

        return createLoginResponse(savedUser);
    }

    public Map<String, Object> login(
            String email,
            String password) {

        if (email == null || password == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email and password are required.");
        }

        User user = userRepository
                .findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid email or password."));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password.");
        }

        return createLoginResponse(user);
    }

    private Map<String, Object> createLoginResponse(User user) {
        String token = jwtService.generateToken(user.getEmail());

        return Map.of(
                "token", token,
                "id", user.getId(),
                "name", user.getName(),
                "email", user.getEmail()
        );
    }
}
