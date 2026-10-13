package app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import app.model.HealthDocument;

class AiSummaryServiceTest {

    private AiSummaryService aiSummaryService;

    @BeforeEach
    void setUp() {
        // Unauthenticated instance without API key to test offline/fallback logic safely
        aiSummaryService = new AiSummaryService();
    }

    @Test
    void chunkDocumentsSplitsContentIntoParagraphChunks() {
        HealthDocument doc1 = new HealthDocument();
        doc1.setId(1L);
        doc1.setTitle("Complete Blood Count");
        doc1.setDocumentType("Blood Report");
        doc1.setContent("Hemoglobin: 14.2 g/dL.\nWBC: 6,500 /mcL.\n\nPlatelets: 250,000 /mcL.");

        HealthDocument doc2 = new HealthDocument();
        doc2.setId(2L);
        doc2.setTitle("Cardiology Note");
        doc2.setDocumentType("Consultation");
        doc2.setContent("Blood pressure: 120/80 mmHg. Normal sinus rhythm.");

        var chunks = aiSummaryService.chunkDocuments(List.of(doc1, doc2));

        assertEquals(3, chunks.size(), "Should produce 3 paragraph chunks across the 2 documents");
        assertEquals("Complete Blood Count", chunks.get(0).documentTitle());
        assertEquals("Cardiology Note", chunks.get(2).documentTitle());
    }

    @Test
    void retrieveRelevantChunksRanksMatchingKeywordsHighest() {
        HealthDocument doc1 = new HealthDocument();
        doc1.setId(1L);
        doc1.setTitle("Lipid Panel");
        doc1.setDocumentType("Lab Report");
        doc1.setContent("Total Cholesterol: 210 mg/dL.\nTriglycerides: 150 mg/dL.\nHDL: 50 mg/dL.");

        HealthDocument doc2 = new HealthDocument();
        doc2.setId(2L);
        doc2.setTitle("Prescription Note");
        doc2.setDocumentType("Prescription");
        doc2.setContent("Prescribed Atorvastatin 10mg once daily at bedtime for cholesterol management.");

        var chunks = aiSummaryService.chunkDocuments(List.of(doc1, doc2));
        var retrieved = aiSummaryService.retrieveRelevantChunks("What dose of Atorvastatin is prescribed?", chunks);

        assertFalse(retrieved.isEmpty(), "Should retrieve matching chunks");
        assertEquals("Prescription Note", retrieved.get(0).documentTitle(), "Chunk with Atorvastatin should rank first");
        assertTrue(retrieved.get(0).score() > 0, "Top chunk score should be positive");
    }

    @Test
    void generateConsolidatedSummaryThrowsOnEmptyList() {
        assertThrows(IllegalArgumentException.class, () -> {
            aiSummaryService.generateConsolidatedSummary(List.of());
        });
    }

    @Test
    void answerQuestionWithRagReturnsEmptyDocNoticeWhenNoDocs() {
        var response = aiSummaryService.answerQuestionWithRag("What is my blood pressure?", List.of());

        assertNotNull(response);
        assertTrue(response.answer().contains("do not have any saved health documents"));
        assertTrue(response.sources().isEmpty());
    }

    @Test
    void answerQuestionWithRagGracefullyFallsBackWhenOffline() {
        HealthDocument doc = new HealthDocument();
        doc.setId(10L);
        doc.setTitle("Endocrine Consultation");
        doc.setDocumentType("Consultation");
        doc.setContent("Patient diagnosed with Type 2 Diabetes. Fasting glucose: 135 mg/dL.");

        var response = aiSummaryService.answerQuestionWithRag("What was the fasting glucose?", List.of(doc));

        assertNotNull(response);
        assertNotNull(response.answer());
        assertFalse(response.sources().isEmpty(), "Should cite Endocrine Consultation as source");
        assertEquals("Endocrine Consultation", response.sources().get(0).get("title"));
    }

    @Test
    void retrieveRelevantChunksAppliesStemmingForPlurals() {
        HealthDocument doc = new HealthDocument();
        doc.setId(3L);
        doc.setTitle("Doctor Visit");
        doc.setDocumentType("Prescription");
        doc.setContent("Active Medication: Lisinopril 20mg daily for hypertension.");

        var chunks = aiSummaryService.chunkDocuments(List.of(doc));
        // Note plural "medications" vs document singular "Medication"
        var retrieved = aiSummaryService.retrieveRelevantChunks("What medications are mentioned?", chunks);

        assertFalse(retrieved.isEmpty(), "Stemming should match 'medications' to 'medication'");
        assertTrue(retrieved.get(0).score() > 0, "Stemmed query should produce a positive score");
        assertEquals("Doctor Visit", retrieved.get(0).documentTitle());
    }

    @Test
    void answerQuestionWithRagReportsAbsenceWhenKeywordsAbsent() {
        HealthDocument doc = new HealthDocument();
        doc.setId(4L);
        doc.setTitle("Ophthalmology Exam");
        doc.setDocumentType("Vision Report");
        doc.setContent("Visual acuity 20/20 in both eyes. Intraocular pressure normal.");

        // Question about completely absent condition
        var response = aiSummaryService.answerQuestionWithRag("What was my knee MRI result?", List.of(doc));

        assertNotNull(response);
        assertTrue(response.answer().toLowerCase().contains("do not contain information"),
                "Should clearly inform the user that documents do not contain information regarding absent query");
        assertTrue(response.sources().isEmpty(), "Should not fabricate sources when information is absent");
    }

    @Test
    void generateConsolidatedSummaryCombinesMultipleDocuments() {
        HealthDocument doc1 = new HealthDocument();
        doc1.setId(1L);
        doc1.setTitle("CBC Blood Test");
        doc1.setDocumentType("Blood Report");
        doc1.setContent("Hemoglobin 13.8 g/dL. Platelets 220,000 /mcL.");

        HealthDocument doc2 = new HealthDocument();
        doc2.setId(2L);
        doc2.setTitle("Cardiology Follow-up");
        doc2.setDocumentType("Consultation");
        doc2.setContent("Diagnosis: Mild Hypertension. Prescribed Lisinopril 10mg once daily.");

        String summary = aiSummaryService.generateConsolidatedSummary(List.of(doc1, doc2));

        assertNotNull(summary);
        assertTrue(summary.contains("Patient's Current Problem"));
        assertTrue(summary.contains("Relevant Medical History"));
        assertTrue(summary.contains("Possible Explanations"));
        assertTrue(summary.contains("Important Test Results"));
        assertTrue(summary.contains("Current Medications and Documented Allergies"));
        assertTrue(summary.contains("Missing Information"));
        assertTrue(summary.contains("Suggested Next Steps"));
        assertTrue(summary.contains("Lisinopril"));
        assertTrue(summary.contains("Hemoglobin 13.8"));
    }

    @Test
    void summarizeFallsBackToOfflinePreviewWhenServiceOffline() {
        String summary = aiSummaryService.summarize("Hemoglobin: 14.5 g/dL. Blood Sugar: 95 mg/dL.");

        assertNotNull(summary);
        assertTrue(summary.contains("DOCUMENT SUMMARY (OFFLINE PREVIEW)"));
        assertTrue(summary.contains("Hemoglobin: 14.5"));
    }

    @Test
    void answerQuestionWithRagFallbackDoesNotDumpRawDocument() {
        HealthDocument bloodDoc = new HealthDocument();
        bloodDoc.setId(5L);
        bloodDoc.setTitle("Comprehensive Metabolic Panel");
        bloodDoc.setDocumentType("Blood Report");
        bloodDoc.setContent("""
                Patient: John Doe. Date: 2026-03-15.
                Sodium: 140 mEq/L (135-145).
                Potassium: 4.2 mEq/L (3.5-5.0).
                Chloride: 102 mEq/L (96-106).
                Carbon Dioxide: 24 mEq/L (22-29).
                Blood Urea Nitrogen: 15 mg/dL (7-20).
                Creatinine: 0.9 mg/dL (0.6-1.2).
                Glucose: 95 mg/dL (70-99).
                Calcium: 9.4 mg/dL (8.5-10.2).
                Total Protein: 7.1 g/dL (6.0-8.3).
                Albumin: 4.5 g/dL (3.5-5.0).
                Bilirubin: 0.8 mg/dL (0.1-1.2).
                Alkaline Phosphatase: 70 U/L (44-147).
                AST: 22 U/L (10-40).
                ALT: 25 U/L (7-56).
                Notes: Routine annual wellness screening.
                """);

        var response = aiSummaryService.answerQuestionWithRag("Summarize my latest blood test results.", List.of(bloodDoc));

        assertNotNull(response);
        assertTrue(response.answer().toLowerCase().contains("offline") || response.answer().contains("OFFLINE") || response.answer().contains("temporarily unavailable"));
        assertFalse(response.answer().contains("Bilirubin: 0.8 mg/dL (0.1-1.2)."), "Must not dump raw document panel verbatim");
        assertFalse(response.answer().contains("Alkaline Phosphatase: 70 U/L"), "Must not dump full panel verbatim");
    }

    @Test
    void answerQuestionWithRagExtractsMedicationsAccuratelyInFallback() {
        HealthDocument medDoc = new HealthDocument();
        medDoc.setId(6L);
        medDoc.setTitle("Internal Medicine Prescription");
        medDoc.setDocumentType("Prescription");
        medDoc.setContent("Rx:\n1. Metformin 500mg oral tablet twice daily with meals.\n2. Lisinopril 10mg once daily in morning.");

        var response = aiSummaryService.answerQuestionWithRag("What medications are mentioned in my documents?", List.of(medDoc));

        assertNotNull(response);
        assertTrue(response.answer().contains("Metformin 500mg"));
        assertTrue(response.answer().contains("Lisinopril 10mg"));
        assertFalse(response.sources().isEmpty());
    }

    @Test
    void answerQuestionWithRagMedicationQuestionReturnsNoneWhenNoMeds() {
        HealthDocument labDoc = new HealthDocument();
        labDoc.setId(7L);
        labDoc.setTitle("Complete Blood Count");
        labDoc.setDocumentType("Blood Report");
        labDoc.setContent("WBC: 6.2 k/uL. RBC: 4.8 M/uL. Hemoglobin: 14.1 g/dL. Platelets: 230 k/uL.");

        var response = aiSummaryService.answerQuestionWithRag("What medications are mentioned in my documents?", List.of(labDoc));

        assertNotNull(response);
        assertTrue(response.answer().toLowerCase().contains("no medications") || response.answer().toLowerCase().contains("do not contain"),
                "Should state no medications were found rather than returning the CBC report");
    }

    @Test
    void answerQuestionWithRagExtractsHemoglobinInFallback() {
        HealthDocument labDoc = new HealthDocument();
        labDoc.setId(8L);
        labDoc.setTitle("Hemogram Report");
        labDoc.setDocumentType("Blood Report");
        labDoc.setContent("Hemoglobin: 14.2 g/dL (Reference: 13.5 - 17.5 g/dL).\nHematocrit: 42.0%.\nWBC Count: 7,000 /mcL.");

        var response = aiSummaryService.answerQuestionWithRag("What was my hemoglobin?", List.of(labDoc));

        assertNotNull(response);
        assertTrue(response.answer().contains("14.2 g/dL"));
        assertTrue(response.answer().contains("Hemogram Report"));
    }

    @Test
    void retrieveRelevantChunksPrioritizesLatestDocumentWhenRequested() {
        HealthDocument olderReport = new HealthDocument();
        olderReport.setId(1L);
        olderReport.setTitle("Blood Test January");
        olderReport.setDocumentType("Blood Report");
        olderReport.setContent("Hemoglobin 12.5 g/dL. Fasting Blood Glucose: 110 mg/dL.");

        HealthDocument newerReport = new HealthDocument();
        newerReport.setId(20L);
        newerReport.setTitle("Blood Test October");
        newerReport.setDocumentType("Blood Report");
        newerReport.setContent("Hemoglobin 14.1 g/dL. Fasting Blood Glucose: 92 mg/dL.");

        var chunks = aiSummaryService.chunkDocuments(List.of(olderReport, newerReport));
        var retrieved = aiSummaryService.retrieveRelevantChunks("Summarize my latest blood test results.", chunks);

        assertFalse(retrieved.isEmpty());
        assertEquals("Blood Test October", retrieved.get(0).documentTitle(),
                "Query asking for 'latest' should prioritize the most recent document (ID 20)");
    }

    @Test
    void answerQuestionWithRagPreventsDuplicateSourceCardsFromSameDocument() {
        HealthDocument docWithMultipleSections = new HealthDocument();
        docWithMultipleSections.setId(99L);
        docWithMultipleSections.setTitle("Sample Blood Test Report");
        docWithMultipleSections.setDocumentType("Blood Report");
        docWithMultipleSections.setContent("""
                Hematology Panel
                Hemoglobin: 14.2 g/dL (Reference: 13.5 - 17.5 g/dL).
                Hematocrit: 42.0%.
                
                Red Blood Cell Count
                RBC: 4.8 M/uL (Reference: 4.5 - 5.9 M/uL).
                MCV: 88 fL.
                
                White Blood Cell Differential
                WBC: 6,800 /mcL (Reference: 4,500 - 11,000 /mcL).
                Neutrophils: 60%.
                """);

        var response = aiSummaryService.answerQuestionWithRag("Summarize my latest blood test results.", List.of(docWithMultipleSections));

        assertNotNull(response);
        assertNotNull(response.sources());
        long distinctSourceIds = response.sources().stream()
                .map(s -> s.get("documentId"))
                .distinct()
                .count();
        assertEquals(1, response.sources().size(), "Should have exactly 1 source entry for document 99, not duplicate cards");
        assertEquals(distinctSourceIds, response.sources().size());
        assertEquals("Sample Blood Test Report", response.sources().get(0).get("title"));
    }

    // --- Gemini Migration Specific Tests ---

    @Test
    void geminiConfigurationDefaultsAreVerified() {
        AiSummaryService service = new AiSummaryService(
                "",
                "gemini-2.5-flash",
                "https://generativelanguage.googleapis.com"
        );
        assertEquals("gemini-2.5-flash", service.getGeminiModel());
        assertEquals("https://generativelanguage.googleapis.com", service.getGeminiBaseUrl());
        assertFalse(service.hasGeminiApiKey());
        assertEquals("gemini", service.getActiveProvider(), "Gemini is the single provider across the application");
    }

    @Test
    void geminiProviderSelectedWhenKeyPresent() {
        AiSummaryService service = new AiSummaryService(
                "AIzaSyDummySecretKey123",
                "gemini-2.5-flash",
                "https://generativelanguage.googleapis.com"
        );
        assertTrue(service.hasGeminiApiKey());
        assertEquals("gemini", service.getActiveProvider());
    }

    @Test
    void extractGeminiTextParsesValidResponseStructure() {
        Map<String, Object> mockGeminiResponse = Map.of(
                "candidates", List.of(
                        Map.of(
                                "content", Map.of(
                                        "parts", List.of(
                                                Map.of("text", "The patient's hemoglobin is 14.2 g/dL, which is within the normal reference range.")
                                        )
                                )
                        )
                )
        );

        String parsedText = aiSummaryService.extractGeminiText(mockGeminiResponse);
        assertEquals("The patient's hemoglobin is 14.2 g/dL, which is within the normal reference range.", parsedText);
    }

    @Test
    void extractGeminiTextHandlesMalformedOrEmptyResponseSafely() {
        assertThrows(RuntimeException.class, () -> aiSummaryService.extractGeminiText(null));
        assertThrows(RuntimeException.class, () -> aiSummaryService.extractGeminiText(Map.of()));
        assertThrows(RuntimeException.class, () -> aiSummaryService.extractGeminiText(Map.of("candidates", List.of())));
    }

    @Test
    void geminiMissingKeyFallsBackCleanlyWithoutThrowing() {
        // Missing key triggers safe fallback without hard crash or leaking sensitive data
        AiSummaryService noKeyService = new AiSummaryService(
                "",
                "gemini-2.5-flash",
                "https://generativelanguage.googleapis.com"
        );

        String summary = noKeyService.summarize("Fasting Blood Sugar: 90 mg/dL.");
        assertNotNull(summary);
        assertTrue(summary.contains("DOCUMENT SUMMARY (OFFLINE PREVIEW)"));
    }

    @Test
    void generateAiTextThrowsIllegalStateExceptionWhenKeyMissing() {
        AiSummaryService noKeyService = new AiSummaryService(
                "",
                "gemini-2.5-flash",
                "https://generativelanguage.googleapis.com"
        );
        assertThrows(IllegalStateException.class, () -> noKeyService.generateAiText("Test prompt"));
    }

    @Test
    void generateConsolidatedSummaryCombinesIntakeResponsesWithDocumentFacts() {
        HealthDocument doc = new HealthDocument();
        doc.setId(10L);
        doc.setTitle("Prescription Slip");
        doc.setDocumentType("Prescription");
        doc.setContent("Rx: Metformin 500mg twice daily with meals.");

        String intakeConversation = """
                Assistant: Hello! What brings you in today?
                Patient: I have had persistent fatigue and dizziness for 3 days.
                Assistant: Are you taking any medications?
                Patient: Yes, Metformin for my diabetes.
                """;

        String summary = aiSummaryService.generateConsolidatedSummary(List.of(doc), intakeConversation);

        assertNotNull(summary);
        assertTrue(summary.contains("persistent fatigue and dizziness"), "Patient intake symptoms must be captured under Patient's Current Problem");
        assertTrue(summary.contains("Metformin 500mg"), "Uploaded document prescription must be captured under Current Medications");
        assertTrue(summary.contains("1. Patient's Current Problem"));
        assertTrue(summary.contains("8. Suggested Next Steps"));
    }

    @Test
    void generateConsolidatedSummaryIncludesEmergencyWarningAndNoDefinitiveDiagnosis() {
        HealthDocument doc = new HealthDocument();
        doc.setId(11L);
        doc.setTitle("Lab Work");
        doc.setDocumentType("Blood Report");
        doc.setContent("Fasting Glucose: 110 mg/dL.");

        String summary = aiSummaryService.generateConsolidatedSummary(List.of(doc), "");

        assertNotNull(summary);
        assertTrue(summary.contains("Possible Explanations (not a diagnosis)"));
        assertTrue(summary.contains("Findings That Support or Do Not Support Each Possibility"));
        assertTrue(summary.contains("EMERGENCY WARNING"), "Summary must provide emergency warning signs and safety guidance");
        assertTrue(summary.contains("Do not delay urgent care"), "Summary must instruct not to delay urgent care");
        assertTrue(summary.contains("Do not start, stop, or adjust any medications"), "Must instruct not to change medications independently");
    }

    @Test
    void generateConsolidatedSummaryFiltersOutDisclaimersAndWarningsFromMedicalHistory() {
        HealthDocument doc = new HealthDocument();
        doc.setId(13L);
        doc.setTitle("Lab Work with Disclaimer");
        doc.setDocumentType("Lab Report");
        doc.setContent("""
                Patient: Jane Doe
                Disclaimer: This is fabricated test data for demonstration purposes only.
                Confidentiality Notice: For reference only.
                Past Medical History: Diagnosed with Asthma in 2021.
                Test: Fasting Glucose 95 mg/dL.
                """);

        String summary = aiSummaryService.generateConsolidatedSummary(List.of(doc), "");

        assertNotNull(summary);
        assertTrue(summary.contains("Diagnosed with Asthma"), "Asthma should be captured under Medical History");
        assertFalse(summary.contains("fabricated test data (from"), "Disclaimers/fabricated data notices must not be classified as patient medical conditions");
        assertFalse(summary.contains("Confidentiality Notice (from"), "Confidentiality notices must not be classified as patient medical conditions");
    }

    @Test
    void providerSelectionReflectsKeyPresenceAndDistinguishesOfflineReason() {
        AiSummaryService noKeyService = new AiSummaryService(
                "",
                "gemini-2.5-flash",
                "https://generativelanguage.googleapis.com"
        );
        assertEquals("gemini", noKeyService.getActiveProvider());
        assertFalse(noKeyService.hasGeminiApiKey());

        HealthDocument doc = new HealthDocument();
        doc.setId(14L);
        doc.setTitle("Test Doc");
        doc.setContent("History of Hypertension.");

        String summaryNoKey = noKeyService.generateConsolidatedSummary(List.of(doc), "");
        assertTrue(summaryNoKey.contains("GEMINI_API_KEY is not configured"), "Fallback should clearly state that GEMINI_API_KEY is not configured");

        AiSummaryService withKeyService = new AiSummaryService(
                "dummy-key",
                "gemini-2.5-flash",
                "http://127.0.0.1:59999",
                null
        );
        assertEquals("gemini", withKeyService.getActiveProvider());
        assertTrue(withKeyService.hasGeminiApiKey());

        String summaryWithKey = withKeyService.generateConsolidatedSummary(List.of(doc), "");
        assertTrue(summaryWithKey.contains("AI Service Status:"), "Fallback should state the diagnosed AI Service Status");
    }

    @Test
    void ragQaReturnsGroundedGeminiAnswerWhenAiServiceSucceeds() {
        HealthDocument doc = new HealthDocument();
        doc.setId(20L);
        doc.setTitle("Blood Panel");
        doc.setDocumentType("Lab Report");
        doc.setContent("Fasting Glucose: 92 mg/dL. Normal.");

        AiSummaryService service = new AiSummaryService(
                "dummy-key",
                "gemini-2.5-flash",
                "https://generativelanguage.googleapis.com"
        ) {
            @Override
            public String generateAiText(String prompt) {
                return "Your fasting glucose was 92 mg/dL, which is within normal limits according to the Blood Panel.";
            }
        };

        var response = service.answerQuestionWithRag("What was my fasting glucose?", List.of(doc));
        assertNotNull(response);
        assertEquals("Your fasting glucose was 92 mg/dL, which is within normal limits according to the Blood Panel.", response.answer());
        assertFalse(response.sources().isEmpty());
        assertEquals("Blood Panel", response.sources().get(0).get("title"));
    }

    @Test
    void ragQaDiagnosesModelNotFoundOrOfflineReasonHonestlyInFallback() {
        HealthDocument doc = new HealthDocument();
        doc.setId(21L);
        doc.setTitle("Lab Work");
        doc.setDocumentType("Lab Report");
        doc.setContent("Hemoglobin: 13.5 g/dL.");

        AiSummaryService service = new AiSummaryService(
                "dummy-key",
                "unsupported-model-x",
                "http://127.0.0.1:59999",
                null
        );

        var response = service.answerQuestionWithRag("What was my hemoglobin?", List.of(doc));
        assertNotNull(response);
        assertTrue(response.answer().contains("OFFLINE RETRIEVAL"));
        assertTrue(response.answer().contains("Source: Lab Work"));
    }

    @Test
    void checkGeminiConnectivityReportsUnconfiguredKeySafely() {
        AiSummaryService noKeyService = new AiSummaryService(
                "",
                "gemini-2.5-flash",
                "https://generativelanguage.googleapis.com"
        );

        var result = noKeyService.checkGeminiConnectivity();
        assertNotNull(result);
        assertFalse(result.successful());
        assertEquals("gemini-2.5-flash", result.model());
        assertTrue(result.message().contains("GEMINI_API_KEY is not configured"));
    }

    @Test
    void checkGeminiConnectivityHandlesConnectionFailureGracefully() {
        AiSummaryService unreachableService = new AiSummaryService(
                "dummy-test-key",
                "gemini-2.5-flash",
                "http://127.0.0.1:59999",
                null
        );

        var result = unreachableService.checkGeminiConnectivity();
        assertNotNull(result);
        assertFalse(result.successful());
        assertEquals("gemini-2.5-flash", result.model());
        assertTrue(result.message().contains("Connection failure") || result.message().contains("HTTP"));
    }
}
