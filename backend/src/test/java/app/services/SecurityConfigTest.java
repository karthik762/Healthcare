package app.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class SecurityConfigTest {

    @Test
    void corsConfigurationSourceAllowsVercelAndLocalhostOrigins() {
        SecurityConfig securityConfig = new SecurityConfig(null);
        CorsConfigurationSource corsSource = securityConfig.corsConfigurationSource();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/auth/register");

        CorsConfiguration config = corsSource.getCorsConfiguration(request);

        assertNotNull(config, "CORS configuration should be registered for API paths");
        assertNotNull(config.getAllowedOrigins(), "Allowed origins list should not be null");

        assertTrue(
                config.getAllowedOrigins().contains("https://healthcare-zeta-one.vercel.app"),
                "Should allow the deployed Vercel frontend"
        );
        assertTrue(
                config.getAllowedOrigins().contains("http://localhost:5173"),
                "Should allow local Vite development server"
        );
        assertTrue(
                config.getAllowedOrigins().contains("http://127.0.0.1:5173"),
                "Should allow 127.0.0.1 Vite development server"
        );
        assertFalse(
                config.getAllowedOrigins().contains("https://unauthorized-domain.com"),
                "Should not allow arbitrary origins"
        );

        assertTrue(config.getAllowedMethods().contains("OPTIONS"));
        assertTrue(config.getAllowedMethods().contains("POST"));
        assertTrue(config.getAllowedHeaders().contains("Content-Type"));
        assertTrue(config.getAllowedHeaders().contains("Authorization"));
    }
}
