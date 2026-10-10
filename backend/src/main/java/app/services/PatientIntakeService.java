
package app.services;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import app.model.PatientIntakeSession;
import app.model.User;
import app.repository.PatientIntakeRepository;
import app.repository.UserRepository;

@Service
public class PatientIntakeService {

    private final PatientIntakeRepository repository;
    private final UserRepository userRepository;
    private final RestClient restClient;

    public PatientIntakeService(
            PatientIntakeRepository repository,
            UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.restClient = RestClient.create();
    }

    // Get the currently logged-in user
    private User getCurrentUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || authentication.getName().equals("anonymousUser")) {
            throw new RuntimeException("Please log in first.");
        }

        return userRepository
                .findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() ->
                        new RuntimeException("User account not found."));
    }

    // Start a new session for the logged-in user
    public PatientIntakeSession startSession() {
        User user = getCurrentUser();

        PatientIntakeSession session = new PatientIntakeSession();

        session.setUser(user);
        session.setConversation("");
        session.setStatus("IN_PROGRESS");
        session.setDoctorSummary("");

        String firstQuestion =
                "Hello! I will help collect information for your doctor. "
                + "What brings you to the hospital today?";

        session.setConversation("Assistant: " + firstQuestion);

        return repository.save(session);
    }

    // Retrieve a session only if it belongs to the logged-in user
    public PatientIntakeSession getSession(Long id) {
        User user = getCurrentUser();

        return repository.findByIdAndUser_Id(id, user.getId())
                .orElseThrow(() ->
                        new RuntimeException(
                                "Patient intake session not found."));
    }

    // Send a message in the user's own session
    public String sendMessage(Long id, String message) {
        PatientIntakeSession session = getSession(id);

        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw new RuntimeException(
                    "This intake session is no longer active.");
        }

        if (message == null || message.trim().isEmpty()) {
            throw new RuntimeException("Please enter a message.");
        }

        String cleanMessage = message.trim();

        String conversation = session.getConversation()
                + "\nPatient: " + cleanMessage;

        String lowerMessage = cleanMessage.toLowerCase();

        // Basic emergency keyword safeguard, not clinical triage
        if (lowerMessage.contains("can't breathe")
                || lowerMessage.contains("cannot breathe")
                || lowerMessage.contains("severe chest pain")
                || lowerMessage.contains("unconscious")
                || lowerMessage.contains("heavy bleeding")
                || lowerMessage.contains("face drooping")) {

            String emergencyReply =
                    "Your symptoms may require emergency attention. "
                    + "Please seek immediate medical help or contact "
                    + "your local emergency service. Do not wait for "
                    + "this chat to continue.";

            conversation += "\nAssistant: " + emergencyReply;

            session.setConversation(conversation);
            session.setStatus("URGENT");

            repository.save(session);
            return emergencyReply;
        }

        String prompt =
                "You are a patient intake assistant helping collect "
                + "information for a doctor. You must not diagnose "
                + "illnesses or prescribe treatment.\n\n"

                + "IMPORTANT RULES:\n"
                + "1. Read the entire conversation before responding.\n"
                + "2. The LAST Patient message is the newest information.\n"
                + "3. Respond directly to the newest patient message.\n"
                + "4. Never repeat a question already asked if the patient "
                + "has answered it.\n"
                + "5. Ask exactly ONE relevant follow-up question.\n"
                + "6. Choose the next question based on information that "
                + "is still missing.\n"
                + "7. Gather onset, duration, location, severity, "
                + "progression, related symptoms, medical history, "
                + "medicines and allergies when appropriate.\n"
                + "8. Use simple, respectful language.\n"
                + "9. Do not recommend medicines or claim the patient "
                + "is safe.\n"
                + "10. If the patient describes a possible emergency, "
                + "advise immediate medical attention.\n"
                + "11. Return only your next question or necessary "
                + "safety guidance.\n\n"

                + "CONVERSATION HISTORY:\n"
                + conversation
                + "\n\nYour response to the latest patient message:";

        Map<?, ?> response = restClient.post()
                .uri("http://localhost:11434/api/generate")
                .body(Map.of(
                        "model", "llama3.2:3b",
                        "prompt", prompt,
                        "stream", false,
                        "options", Map.of(
                                "temperature", 0.4,
                                "repeat_penalty", 1.2
                        )
                ))
                .retrieve()
                .body(Map.class);

        String reply = "";

        if (response != null && response.get("response") != null) {
            reply = response.get("response").toString().trim();
        }

        if (reply.isEmpty()) {
            reply = "I couldn't generate the next question. "
                    + "Please try sending your message again.";
        }

        conversation += "\nAssistant: " + reply;

        session.setConversation(conversation);
        repository.save(session);

        return reply;
    }

    // Generate a doctor summary for the user's own session
    public String generateDoctorSummary(Long id) {
        PatientIntakeSession session = getSession(id);

        if ("URGENT".equals(session.getStatus())) {
            throw new RuntimeException(
                    "This session requires urgent clinical attention.");
        }

        if (session.getConversation() == null
                || session.getConversation().trim().isEmpty()) {
            throw new RuntimeException(
                    "No conversation is available to summarize.");
        }

        String prompt =
                "Create a concise, structured patient intake summary "
                + "for a doctor using only the conversation below.\n\n"

                + "RULES:\n"
                + "1. Use only information explicitly provided by the patient.\n"
                + "2. Do not invent details or make a diagnosis.\n"
                + "3. Do not recommend medicines or treatments.\n"
                + "4. Mark missing information as Not provided.\n"
                + "5. Clearly distinguish reported symptoms from confirmed facts.\n"
                + "6. Include warning signs mentioned in the conversation.\n"
                + "7. Do not claim that the patient is safe or stable.\n\n"

                + "Use these headings:\n"
                + "PATIENT INTAKE SUMMARY\n"
                + "1. Chief Complaint\n"
                + "2. History of Present Illness\n"
                + "3. Symptom Location and Severity\n"
                + "4. Associated Symptoms\n"
                + "5. Relevant Medical History\n"
                + "6. Current Medicines\n"
                + "7. Allergies\n"
                + "8. Important Warning Signs Reported\n"
                + "9. Missing Information / Follow-up Questions\n\n"

                + "For warning signs, list only those reported in the "
                + "conversation. If none were mentioned, write "
                + "'None reported in the conversation'.\n\n"

                + "Conversation:\n"
                + session.getConversation();

        Map<?, ?> response = restClient.post()
                .uri("http://localhost:11434/api/generate")
                .body(Map.of(
                        "model", "llama3.2:3b",
                        "prompt", prompt,
                        "stream", false,
                        "options", Map.of(
                                "temperature", 0.2,
                                "repeat_penalty", 1.2,
                                "num_predict", 700
                        )
                ))
                .retrieve()
                .body(Map.class);

        String summary = "";

        if (response != null && response.get("response") != null) {
            summary = response.get("response").toString().trim();
        }

        if (summary.isEmpty()) {
            throw new RuntimeException(
                    "Unable to generate the summary. Please try again.");
        }

        session.setDoctorSummary(summary);
        session.setStatus("COMPLETED");

        repository.save(session);

        return summary;
    }
}
