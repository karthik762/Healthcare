package app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;

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
                config.getAllowedOrigins().contains("https://healthcare-kq1tgwxr1-karthiks-projects-2fe9ad10.vercel.app"),
                "Should allow the active Vercel frontend origin"
        );
        assertTrue(
                config.getAllowedOrigins().contains("https://healthcare-zeta-one.vercel.app"),
                "Should allow the production Vercel frontend"
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

        List<String> methods = config.getAllowedMethods();
        assertTrue(methods.contains("OPTIONS"));
        assertTrue(methods.contains("GET"));
        assertTrue(methods.contains("POST"));
        assertTrue(methods.contains("PUT"));
        assertTrue(methods.contains("PATCH"));
        assertTrue(methods.contains("DELETE"));

        List<String> headers = config.getAllowedHeaders();
        assertTrue(headers.contains("Content-Type"));
        assertTrue(headers.contains("Authorization"));
        assertTrue(headers.contains("Accept"));
    }

    @Test
    void corsPreflightRequestToRegisterAllowsVercelOrigin() throws Exception {
        SecurityConfig securityConfig = new SecurityConfig(null);
        CorsConfigurationSource corsSource = securityConfig.corsConfigurationSource();

        MockHttpServletRequest preflightRequest = new MockHttpServletRequest("OPTIONS", "/api/auth/register");
        preflightRequest.addHeader("Origin", "https://healthcare-kq1tgwxr1-karthiks-projects-2fe9ad10.vercel.app");
        preflightRequest.addHeader("Access-Control-Request-Method", "POST");
        preflightRequest.addHeader("Access-Control-Request-Headers", "Content-Type, Authorization");

        CorsConfiguration config = corsSource.getCorsConfiguration(preflightRequest);
        assertNotNull(config, "CORS config must exist for preflight request");

        DefaultCorsProcessor processor = new DefaultCorsProcessor();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean processed = processor.processRequest(config, preflightRequest, response);

        assertTrue(processed, "Preflight request should be accepted by CORS processor");
        assertEquals(
                "https://healthcare-kq1tgwxr1-karthiks-projects-2fe9ad10.vercel.app",
                response.getHeader("Access-Control-Allow-Origin"),
                "Access-Control-Allow-Origin header must match the requesting Vercel origin"
        );
        assertNotNull(response.getHeader("Access-Control-Allow-Methods"));
        assertTrue(response.getHeader("Access-Control-Allow-Methods").contains("POST"));
    }

    @Test
    void corsConfigurationAppendsAdditionalOriginsFromEnvironment() {
        SecurityConfig securityConfig = new SecurityConfig(null, "https://preview-domain.vercel.app, https://custom.domain.com");
        CorsConfigurationSource corsSource = securityConfig.corsConfigurationSource();

        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/register");
        CorsConfiguration config = corsSource.getCorsConfiguration(request);

        assertNotNull(config);
        assertTrue(config.getAllowedOrigins().contains("https://preview-domain.vercel.app"));
        assertTrue(config.getAllowedOrigins().contains("https://custom.domain.com"));
        assertTrue(config.getAllowedOrigins().contains("https://healthcare-kq1tgwxr1-karthiks-projects-2fe9ad10.vercel.app"));
    }
}

