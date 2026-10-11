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
    private PatientIntakeService intakeService;
    private User testUser;

    @BeforeEach
    void setUp() {
        repository = mock(PatientIntakeRepository.class);
        userRepository = mock(UserRepository.class);

        // Point to a non-existent port to simulate offline Ollama service
        intakeService = new PatientIntakeService(
                repository,
                userRepository,
                "http://127.0.0.1:59999"
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
    void sendMessageFallsBackGracefullyWhenOllamaIsOffline() {
        PatientIntakeSession session = new PatientIntakeSession();
        session.setId(20L);
        session.setUser(testUser);
        session.setStatus("IN_PROGRESS");
        session.setConversation("Assistant: What brings you in?");

        when(repository.findByIdAndUser_Id(20L, 1L)).thenReturn(Optional.of(session));
        when(repository.save(any(PatientIntakeSession.class))).thenAnswer(i -> i.getArgument(0));

        // When Ollama is offline at port 59999, it should NOT throw a 500 error; it should return a fallback question
        String reply = intakeService.sendMessage(20L, "I have had a mild headache and fever for two days.");

        assertNotNull(reply);
        assertTrue(reply.contains("symptoms begin") || reply.contains("scale of 1 to 10"));
        assertTrue(session.getConversation().contains("Patient: I have had a mild headache"));
    }

    @Test
    void generateDoctorSummaryProducesStructuredReportWhenOllamaIsOffline() {
        PatientIntakeSession session = new PatientIntakeSession();
        session.setId(30L);
        session.setUser(testUser);
        session.setStatus("IN_PROGRESS");
        session.setConversation("Assistant: Hello!\nPatient: I have a headache for two days.\nAssistant: How severe is it?\nPatient: Around 4 out of 10.");

        when(repository.findByIdAndUser_Id(30L, 1L)).thenReturn(Optional.of(session));
        when(repository.save(any(PatientIntakeSession.class))).thenAnswer(i -> i.getArgument(0));

        String summary = intakeService.generateDoctorSummary(30L);

        assertNotNull(summary);
        assertEquals("COMPLETED", session.getStatus());
        assertTrue(summary.contains("PATIENT INTAKE SUMMARY"));
        assertTrue(summary.contains("1. Chief Complaint"));
        assertTrue(summary.contains("9. Missing Information"));
    }
}
