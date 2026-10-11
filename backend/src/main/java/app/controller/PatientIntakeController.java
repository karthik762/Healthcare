
package app.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.model.PatientIntakeSession;
import app.services.PatientIntakeService;

@RestController
@RequestMapping("/api/intake")
public class PatientIntakeController {

    private final PatientIntakeService intakeService;

    public PatientIntakeController(PatientIntakeService intakeService) {
        this.intakeService = intakeService;
    }

    // Return only the fields needed by the frontend
    private Map<String, Object> sessionResponse(
            PatientIntakeSession session) {

        return Map.of(
                "id", session.getId(),
                "sessionId", session.getId(),
                "conversation", session.getConversation() == null
                        ? "" : session.getConversation(),
                "status", session.getStatus() == null
                        ? "IN_PROGRESS" : session.getStatus(),
                "doctorSummary", session.getDoctorSummary() == null
                        ? "" : session.getDoctorSummary(),
                "createdAt", session.getCreatedAt() == null
                        ? "" : session.getCreatedAt().toString(),
                "updatedAt", session.getUpdatedAt() == null
                        ? "" : session.getUpdatedAt().toString()
        );
    }

    // Start a new patient intake conversation
    @PostMapping("/start")
    public Map<String, Object> startSession() {

        PatientIntakeSession session = intakeService.startSession();

        String firstMessage = session.getConversation()
                .replaceFirst("^Assistant: ", "");

        return Map.of(
                "sessionId", session.getId(),
                "reply", firstMessage,
                "status", session.getStatus()
        );
    }

    // Send a patient message and receive the next question
    @PostMapping("/{id}/message")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {

        String message = request != null ? request.get("message") : null;
        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Please enter a message.")
            );
        }

        try {
            String reply = intakeService.sendMessage(id, message);
            PatientIntakeSession session = intakeService.getSession(id);

            return ResponseEntity.ok(Map.of(
                    "sessionId", session.getId(),
                    "reply", reply,
                    "status", session.getStatus()
            ));
        } catch (RuntimeException exception) {
            int status = exception.getMessage().contains("not found") ? 404 : 400;
            return ResponseEntity.status(status).body(
                    Map.of("error", exception.getMessage())
            );
        }
    }

    // Generate the doctor summary
    @PostMapping("/{id}/summary")
    public ResponseEntity<Map<String, Object>> generateSummary(@PathVariable Long id) {
        try {
            String summary = intakeService.generateDoctorSummary(id);
            PatientIntakeSession session = intakeService.getSession(id);

            return ResponseEntity.ok(Map.of(
                    "sessionId", session.getId(),
                    "summary", summary,
                    "status", session.getStatus()
            ));
        } catch (RuntimeException exception) {
            int status = exception.getMessage().contains("not found") ? 404 : 400;
            return ResponseEntity.status(status).body(
                    Map.of("error", exception.getMessage())
            );
        }
    }

    // Retrieve a session without returning its User object
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getSession(
            @PathVariable Long id) {

        try {
            PatientIntakeSession session = intakeService.getSession(id);

            return ResponseEntity.ok(sessionResponse(session));

        } catch (RuntimeException exception) {
            return ResponseEntity.notFound().build();
        }
    }
}
