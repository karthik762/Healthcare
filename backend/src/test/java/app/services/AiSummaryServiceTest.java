package app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import app.model.HealthDocument;

class AiSummaryServiceTest {

    private AiSummaryService aiSummaryService;

    @BeforeEach
    void setUp() {
        // Point to unreachable port to test offline/fallback logic safely
        aiSummaryService = new AiSummaryService("http://127.0.0.1:59999");
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
    void answerQuestionWithRagGracefullyFallsBackWhenOllamaIsOffline() {
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
        assertTrue(summary.contains("Overall Health Overview"));
        assertTrue(summary.contains("Main Complaints or Conditions"));
        assertTrue(summary.contains("Medications"));
        assertTrue(summary.contains("Important Test Results"));
    }

    @Test
    void summarizeFallsBackToOfflinePreviewWhenOllamaOffline() {
        String summary = aiSummaryService.summarize("Hemoglobin: 14.5 g/dL. Blood Sugar: 95 mg/dL.");
        assertNotNull(summary);
        assertTrue(summary.contains("DOCUMENT SUMMARY (OFFLINE PREVIEW)"));
        assertTrue(summary.contains("Hemoglobin: 14.5"));
    }
}
