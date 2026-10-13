package app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import app.model.PatientIntakeSession;
import app.model.User;
import app.repository.PatientIntakeRepository;
import app.repository.UserRepository;

class PatientIntakeServiceTest {

    private PatientIntakeRepository repository;
    private UserRepository userRepository;
    private AiSummaryService aiSummaryService;
    private PatientIntakeService intakeService;
    private User testUser;

    @BeforeEach
    void setUp() {
        repository = mock(PatientIntakeRepository.class);
        userRepository = mock(UserRepository.class);
        aiSummaryService = mock(AiSummaryService.class);

        intakeService = new PatientIntakeService(
                repository,
                userRepository,
                aiSummaryService
        );

        testUser = mock(User.class);
        when(testUser.getId()).thenReturn(1L);
        when(testUser.getEmail()).thenReturn("tester@example.com");

        when(userRepository.findByEmailIgnoreCase("tester@example.com"))
                .thenReturn(Optional.of(testUser));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "tester@example.com",
                        null,
                        java.util.Collections.emptyList()
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void emergencyKeywordsTriggerImmediateSafeguard() {
        PatientIntakeSession session = new PatientIntakeSession();
        session.setId(10L);
        session.setUser(testUser);
        session.setStatus("IN_PROGRESS");
        session.setConversation("Assistant: What brings you in?");

        when(repository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(session));
        when(repository.save(any(PatientIntakeSession.class))).thenAnswer(i -> i.getArgument(0));

        String reply = intakeService.sendMessage(10L, "I have severe chest pain and cannot breathe");

        assertEquals("URGENT", session.getStatus());
        assertTrue(reply.contains("emergency"));
    }

    @Test
    void sendMessageUsesGeminiWhenAiServiceSucceeds() {
        PatientIntakeSession session = new PatientIntakeSession();
        session.setId(15L);
        session.setUser(testUser);
        session.setStatus("IN_PROGRESS");
        session.setConversation("Assistant: What brings you in?");

        when(repository.findByIdAndUser_Id(15L, 1L)).thenReturn(Optional.of(session));
        when(repository.save(any(PatientIntakeSession.class))).thenAnswer(i -> i.getArgument(0));
        when(aiSummaryService.generateAiText(any())).thenReturn("Where is the abdominal discomfort located, and does it worsen after eating?");

        String reply = intakeService.sendMessage(15L, "I have been having stomach pain since yesterday.");

        assertNotNull(reply);
        assertEquals("Where is the abdominal discomfort located, and does it worsen after eating?", reply);
        assertTrue(session.getConversation().contains("Patient: I have been having stomach pain"));
        assertTrue(session.getConversation().contains("Assistant: Where is the abdominal discomfort located"));
    }

    @Test
    void sendMessageFallsBackAdaptivelyWhenGeminiIsOfflineOrFails() {
        PatientIntakeSession session = new PatientIntakeSession();
        session.setId(20L);
        session.setUser(testUser);
        session.setStatus("IN_PROGRESS");
        session.setConversation("Assistant: What brings you in?");

        when(repository.findByIdAndUser_Id(20L, 1L)).thenReturn(Optional.of(session));
        when(repository.save(any(PatientIntakeSession.class))).thenAnswer(i -> i.getArgument(0));
        // Simulate AI service failure / missing key
        when(aiSummaryService.generateAiText(any())).thenThrow(new IllegalStateException("Gemini API key missing"));

        // When complaining of headache, adaptive fallback asks headache-specific location and severity
        String replyHeadache = intakeService.sendMessage(20L, "I have a severe headache.");
        assertNotNull(replyHeadache);
        assertTrue(replyHeadache.contains("headache located") || replyHeadache.contains("scale of 1 to 10"));
    }

    @Test
    void generateDoctorSummaryProducesStructuredReportViaGemini() {
        PatientIntakeSession session = new PatientIntakeSession();
        session.setId(25L);
        session.setUser(testUser);
        session.setStatus("IN_PROGRESS");
        session.setConversation("Assistant: Hello!\nPatient: I have a throbbing headache.\nAssistant: Where is it located?\nPatient: On the right temple.");

        when(repository.findByIdAndUser_Id(25L, 1L)).thenReturn(Optional.of(session));
        when(repository.save(any(PatientIntakeSession.class))).thenAnswer(i -> i.getArgument(0));
        when(aiSummaryService.generateAiText(any())).thenReturn("PATIENT INTAKE SUMMARY\n1. Chief Complaint: Right-sided throbbing headache.");

        String summary = intakeService.generateDoctorSummary(25L);

        assertNotNull(summary);
        assertEquals("COMPLETED", session.getStatus());
        assertTrue(summary.contains("PATIENT INTAKE SUMMARY"));
        assertTrue(summary.contains("Chief Complaint"));
    }

    @Test
    void generateDoctorSummaryProducesStructuredFallbackWhenGeminiFails() {
        PatientIntakeSession session = new PatientIntakeSession();
        session.setId(30L);
        session.setUser(testUser);
        session.setStatus("IN_PROGRESS");
        session.setConversation("Assistant: Hello!\nPatient: I have a headache for two days.\nAssistant: How severe is it?\nPatient: Around 4 out of 10.");

        when(repository.findByIdAndUser_Id(30L, 1L)).thenReturn(Optional.of(session));
        when(repository.save(any(PatientIntakeSession.class))).thenAnswer(i -> i.getArgument(0));
        when(aiSummaryService.generateAiText(any())).thenThrow(new RuntimeException("Gemini API connection error"));

        String summary = intakeService.generateDoctorSummary(30L);

        assertNotNull(summary);
        assertEquals("COMPLETED", session.getStatus());
        assertTrue(summary.contains("PATIENT INTAKE SUMMARY (OFFLINE MODE)"));
        assertTrue(summary.contains("1. Chief Complaint"));
        assertTrue(summary.contains("9. Missing Information"));
    }
}
