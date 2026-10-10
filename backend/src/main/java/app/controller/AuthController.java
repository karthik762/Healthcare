
package app.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import app.services.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record RegisterRequest(
            @NotBlank
            @Size(max = 100)
            String name,

            @NotBlank
            @Email
            @Size(max = 150)
            String email,

            @NotBlank
            @Size(min = 8, max = 72)
            String password
    ) {}

    public record LoginRequest(
            @NotBlank
            @Email
            String email,

            @NotBlank
            String password
    ) {}

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(
            @Valid @RequestBody RegisterRequest request) {

        return authService.register(
                request.name(),
                request.email(),
                request.password()
        );
    }

    @PostMapping("/login")
    public Map<String, Object> login(
            @Valid @RequestBody LoginRequest request) {

        return authService.login(
                request.email(),
                request.password()
        );
    }
}
