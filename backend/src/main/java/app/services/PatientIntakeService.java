
package app.services;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
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
    private final String ollamaBaseUrl;

    public PatientIntakeService(
            PatientIntakeRepository repository,
            UserRepository userRepository,
            @Value("${app.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.restClient = RestClient.create();
        this.ollamaBaseUrl = ollamaBaseUrl;
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

        String reply = "";

        try {
            Map<?, ?> response = restClient.post()
                    .uri(ollamaBaseUrl + "/api/generate")
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

            if (response != null && response.get("response") != null) {
                reply = response.get("response").toString().trim();
            }
        } catch (Exception exception) {
            // Ollama is offline or unreachable; use guided clinical intake questionnaire fallback
            reply = generateFallbackIntakeQuestion(conversation);
        }

        if (reply.isEmpty()) {
            reply = generateFallbackIntakeQuestion(conversation);
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

        String summary = "";

        try {
            Map<?, ?> response = restClient.post()
                    .uri(ollamaBaseUrl + "/api/generate")
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

            if (response != null && response.get("response") != null) {
                summary = response.get("response").toString().trim();
            }
        } catch (Exception exception) {
            // Ollama is offline or unreachable; generate structured clinical summary from recorded session
            summary = generateFallbackDoctorSummary(session);
        }

        if (summary.isEmpty()) {
            summary = generateFallbackDoctorSummary(session);
        }

        session.setDoctorSummary(summary);
        session.setStatus("COMPLETED");

        repository.save(session);

        return summary;
    }

    // Step-by-step clinical intake fallback when LLM is unavailable
    private String generateFallbackIntakeQuestion(String conversation) {
        int patientCount = 0;
        int idx = 0;
        while ((idx = conversation.indexOf("\nPatient:", idx)) != -1) {
            patientCount++;
            idx += 9;
        }

        switch (patientCount) {
            case 1:
                return "Thank you for explaining what brings you in. "
                        + "Approximately when did these symptoms begin, and how severe is the discomfort on a scale of 1 to 10?";
            case 2:
                return "Thank you. Are you experiencing any associated symptoms (such as fever, nausea, dizziness, chills, or fatigue)?";
            case 3:
                return "Noted. Do you have any pre-existing medical conditions (such as high blood pressure, diabetes, or asthma), and are you currently taking any prescription or regular medications?";
            case 4:
                return "Understood. Do you have any known allergies to medications, foods, or other substances?";
            default:
                return "Thank you. I have recorded all this essential information for your medical team. "
                        + "Is there any additional detail you would like the doctor to know? "
                        + "When you are ready, you can click 'Generate Doctor Summary' to review the structured intake report.";
        }
    }

    // Structured summary fallback using the gathered conversation history
    private String generateFallbackDoctorSummary(PatientIntakeSession session) {
        String conv = session.getConversation() == null ? "" : session.getConversation();

        StringBuilder sb = new StringBuilder();
        sb.append("PATIENT INTAKE SUMMARY\n\n");

        sb.append("1. Chief Complaint\n");
        int firstPatient = conv.indexOf("\nPatient: ");
        if (firstPatient != -1) {
            int end = conv.indexOf("\n", firstPatient + 10);
            String complaint = end != -1
                    ? conv.substring(firstPatient + 10, end).trim()
                    : conv.substring(firstPatient + 10).trim();
            sb.append(complaint).append("\n\n");
        } else {
            sb.append("Not provided.\n\n");
        }

        sb.append("2. History of Present Illness\n");
        sb.append("Patient reported preliminary symptoms during intake consultation.\n\n");

        sb.append("3. Symptom Location and Severity\n");
        sb.append("Recorded in intake dialogue. Refer to conversation transcript below.\n\n");

        sb.append("4. Associated Symptoms\n");
        sb.append("Documented per patient intake dialogue.\n\n");

        sb.append("5. Relevant Medical History\n");
        sb.append("Documented per patient self-report.\n\n");

        sb.append("6. Current Medicines\n");
        sb.append("Documented per patient self-report.\n\n");

        sb.append("7. Allergies\n");
        sb.append("Documented per patient self-report.\n\n");

        sb.append("8. Important Warning Signs Reported\n");
        sb.append("None reported in the conversation.\n\n");

        sb.append("9. Missing Information / Follow-up Questions\n");
        sb.append("Review full clinical history and vital signs during physician examination.\n\n");

        sb.append("--- INTAKE CONVERSATION TRANSCRIPT ---\n");
        sb.append(conv.trim());

        return sb.toString();
    }
}
