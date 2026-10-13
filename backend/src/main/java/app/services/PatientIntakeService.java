
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
    private final AiSummaryService aiSummaryService;

    public PatientIntakeService(
            PatientIntakeRepository repository,
            UserRepository userRepository,
            AiSummaryService aiSummaryService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.aiSummaryService = aiSummaryService;
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

    // Retrieve the most recent intake session belonging to the logged-in user
    public java.util.Optional<PatientIntakeSession> getLatestSessionForCurrentUser() {
        User user = getCurrentUser();
        var sessions = repository.findAllByUser_IdOrderByCreatedAtDesc(user.getId());
        if (sessions == null || sessions.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(sessions.get(0));
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
                "You are an empathetic, clinical patient intake assistant collecting preliminary information for a doctor. "
                + "You must NEVER diagnose illnesses, recommend medications, or advise changing treatments.\n\n"

                + "CLINICAL GUIDELINES:\n"
                + "1. Review the full conversation history. The LAST patient message is the newest information.\n"
                + "2. Adapt dynamically to the patient's specific complaint (e.g. for headache: ask location, onset, light sensitivity; for stomach pain: ask location, meal timing, digestive symptoms; for cough/fever: ask duration, temperature, phlegm).\n"
                + "3. Ask exactly ONE clear, focused follow-up question at a time.\n"
                + "4. Never repeat a question the patient already answered.\n"
                + "5. Systematically cover key missing details when appropriate: onset, duration, location, severity (1-10), progression, associated symptoms, past medical history, current medicines, and allergies.\n"
                + "6. If sufficient details are collected, inform the patient that their intake details are ready for the doctor and encourage them to click 'Generate Doctor Summary'.\n"
                + "7. If the patient reports emergency red-flag symptoms (severe chest pain, inability to breathe, sudden confusion, stroke signs, uncontrollable bleeding), immediately urge them to seek emergency care.\n"
                + "8. Keep the response concise, respectful, and conversational.\n\n"

                + "CONVERSATION HISTORY:\n"
                + conversation
                + "\n\nNext focused intake question for the patient:";

        String reply = "";

        try {
            reply = aiSummaryService.generateAiText(prompt);
            if (reply != null) {
                reply = reply.trim();
            }
        } catch (Exception exception) {
            // Gemini is offline, key missing, or unreachable; use adaptive rule-based clinical intake fallback
            reply = generateFallbackIntakeQuestion(conversation);
        }

        if (reply == null || reply.isBlank()) {
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
                "Create a concise, structured patient intake summary for a attending physician using ONLY the dialogue below.\n\n"

                + "CLINICAL SUMMARY RULES:\n"
                + "1. Use only information explicitly stated by the patient.\n"
                + "2. Do not invent details, infer unstated symptoms, or make a diagnosis.\n"
                + "3. Do not recommend medicines, dosages, or treatments.\n"
                + "4. Mark missing clinical information explicitly as 'Not provided by patient'.\n"
                + "5. Clearly distinguish reported symptoms from confirmed medical facts.\n"
                + "6. Include any warning signs or red-flags reported during the chat.\n"
                + "7. Do not declare that the patient is safe or stable.\n\n"

                + "Use EXACTLY these nine numbered headings:\n"
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

                + "Conversation Transcript:\n"
                + session.getConversation();

        String summary = "";

        try {
            summary = aiSummaryService.generateAiText(prompt);
            if (summary != null) {
                summary = summary.trim();
            }
        } catch (Exception exception) {
            // Gemini offline or unconfigured; generate structured clinical summary from recorded session
            summary = generateFallbackDoctorSummary(session, exception.getMessage());
        }

        if (summary == null || summary.isBlank()) {
            summary = generateFallbackDoctorSummary(session, "Empty response from Gemini AI");
        }

        session.setDoctorSummary(summary);
        session.setStatus("COMPLETED");

        repository.save(session);

        return summary;
    }

    // Adaptive step-by-step clinical intake fallback when Gemini is unavailable
    private String generateFallbackIntakeQuestion(String conversation) {
        String lowerConv = conversation.toLowerCase();
        int patientCount = 0;
        int idx = 0;
        while ((idx = conversation.indexOf("\nPatient:", idx)) != -1) {
            patientCount++;
            idx += 9;
        }

        // Tailor follow-up questions to the specific complaint when available
        boolean mentionsHeadache = lowerConv.contains("headache") || lowerConv.contains("migraine") || lowerConv.contains("head pain");
        boolean mentionsStomach = lowerConv.contains("stomach") || lowerConv.contains("abdominal") || lowerConv.contains("belly") || lowerConv.contains("nausea");
        boolean mentionsChestOrBreathing = lowerConv.contains("chest") || lowerConv.contains("cough") || lowerConv.contains("breath") || lowerConv.contains("fever");

        if (patientCount == 1) {
            if (mentionsHeadache) {
                return "Thank you for explaining. Where is the headache located (e.g. temples, forehead, one side), and how severe is the pain on a scale of 1 to 10?";
            } else if (mentionsStomach) {
                return "Thank you for letting me know. Where in your abdomen is the discomfort located, and does it feel sharper before or after meals?";
            } else if (mentionsChestOrBreathing) {
                return "Understood. When did this begin, and are you having any fever, chills, or difficulty catching your breath?";
            } else {
                return "Thank you for explaining what brings you in. Approximately when did these symptoms begin, and how severe is the discomfort on a scale of 1 to 10?";
            }
        } else if (patientCount == 2) {
            if (mentionsHeadache && !lowerConv.contains("vision") && !lowerConv.contains("light")) {
                return "Noted. Are you noticing any sensitivity to light or sound, nausea, or visual changes with this headache?";
            } else if (mentionsStomach && !lowerConv.contains("vomit") && !lowerConv.contains("bowel")) {
                return "Thank you. Have you experienced any vomiting, fever, or changes in your bowel habits?";
            } else {
                return "Thank you. Are you experiencing any other associated symptoms (such as fever, dizziness, nausea, or unusual fatigue)?";
            }
        } else if (patientCount == 3) {
            return "Noted. Do you have any pre-existing medical conditions (such as high blood pressure, diabetes, or asthma), and are you currently taking any prescription or regular medications?";
        } else if (patientCount == 4) {
            return "Understood. Do you have any known allergies to medications, foods, or other substances?";
        } else {
            return "Thank you. I have recorded these details for your doctor. Is there any additional detail you would like the doctor to know? When you are ready, please click 'Generate Doctor Summary' to compile your intake report.";
        }
    }

    private String generateFallbackDoctorSummary(PatientIntakeSession session) {
        return generateFallbackDoctorSummary(session, "");
    }

    private String generateFallbackDoctorSummary(PatientIntakeSession session, String failureReason) {
        String conv = session.getConversation() == null ? "" : session.getConversation();

        StringBuilder sb = new StringBuilder();
        sb.append("PATIENT INTAKE SUMMARY (OFFLINE MODE)\n");
        if (failureReason != null && !failureReason.isBlank()) {
            sb.append("AI Service Status: ").append(failureReason).append("\n\n");
        } else {
            sb.append("Note: Generated from recorded patient intake responses because Gemini AI is currently offline or unconfigured.\n\n");
        }

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
        sb.append("Review full clinical history, allergies, and vital signs during physician examination.\n\n");

        sb.append("--- INTAKE CONVERSATION TRANSCRIPT ---\n");
        sb.append(conv.trim());

        return sb.toString();
    }
}
