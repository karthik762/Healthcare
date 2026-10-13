
package app.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import app.model.HealthDocument;
import app.model.PatientIntakeSession;
import app.services.AiSummaryService;
import app.services.HealthDocumentService;
import app.services.PatientIntakeService;

@RestController
@RequestMapping("/api/documents")
public class HealthDocumentController {

    private final HealthDocumentService service;
    private final AiSummaryService aiSummaryService;
    private final PatientIntakeService patientIntakeService;

    public HealthDocumentController(
            HealthDocumentService service,
            AiSummaryService aiSummaryService,
            PatientIntakeService patientIntakeService) {

        this.service = service;
        this.aiSummaryService = aiSummaryService;
        this.patientIntakeService = patientIntakeService;
    }

    // Return only the fields required by the frontend
    private Map<String, Object> documentResponse(
            HealthDocument document) {

        return Map.of(
                "id", document.getId(),
                "title", document.getTitle() == null
                        ? "" : document.getTitle(),
                "documentType", document.getDocumentType() == null
                        ? "" : document.getDocumentType(),
                "content", document.getContent() == null
                        ? "" : document.getContent(),
                "createdAt", document.getCreatedAt() == null
                        ? "" : document.getCreatedAt().toString(),
                "updatedAt", document.getUpdatedAt() == null
                        ? "" : document.getUpdatedAt().toString()
        );
    }

    // Get all documents belonging to the logged-in user
    @GetMapping
    public List<Map<String, Object>> getAllDocuments() {

        return service.getAllDocuments()
                .stream()
                .map(this::documentResponse)
                .toList();
    }

    // Get one document belonging to the logged-in user
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getDocument(
            @PathVariable Long id) {

        return service.getDocumentById(id)
                .map(document ->
                        ResponseEntity.ok(documentResponse(document)))
                .orElse(ResponseEntity.notFound().build());
    }

    // Create a document for the logged-in user
    @PostMapping
    public Map<String, Object> createDocument(
            @RequestBody HealthDocument document) {

        HealthDocument savedDocument =
                service.createDocument(document);

        return documentResponse(savedDocument);
    }

    // Update only a document owned by the logged-in user
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateDocument(
            @PathVariable Long id,
            @RequestBody HealthDocument document) {

        return service.updateDocument(id, document)
                .map(updated ->
                        ResponseEntity.ok(documentResponse(updated)))
                .orElse(ResponseEntity.notFound().build());
    }

    // Delete only a document owned by the logged-in user
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long id) {

        if (!service.deleteDocument(id)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }

    // Upload a TXT or PDF file and save its extracted text
    @PostMapping(
            value = "/upload-text",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> uploadTextDocument(
            @RequestParam("title") String title,
            @RequestParam("documentType") String documentType,
            @RequestParam("file") MultipartFile file) {

        if (title == null || title.isBlank()) {
            return ResponseEntity.badRequest()
                    .body("Please enter a document title.");
        }

        if (documentType == null || documentType.isBlank()) {
            return ResponseEntity.badRequest()
                    .body("Please select a document type.");
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body("Please select a file.");
        }

        String filename = file.getOriginalFilename();

        if (filename == null) {
            return ResponseEntity.badRequest()
                    .body("Invalid filename.");
        }

        String lowerFilename = filename.toLowerCase();

        try {
            String content;

            if (lowerFilename.endsWith(".txt")) {

                content = new String(
                        file.getBytes(),
                        StandardCharsets.UTF_8
                );

            } else if (lowerFilename.endsWith(".pdf")) {

                try (PDDocument pdf =
                             Loader.loadPDF(file.getBytes())) {

                    PDFTextStripper stripper = new PDFTextStripper();
                    content = stripper.getText(pdf);
                }

            } else {
                return ResponseEntity.badRequest()
                        .body("Only TXT and PDF files are supported.");
            }

            if (content.isBlank()) {
                return ResponseEntity.badRequest()
                        .body("No readable text was found in the file.");
            }

            HealthDocument document = new HealthDocument();
            document.setTitle(title.trim());
            document.setDocumentType(documentType.trim());
            document.setContent(content);

            // The service assigns the logged-in user as owner
            HealthDocument savedDocument =
                    service.createDocument(document);

            return ResponseEntity.ok(
                    documentResponse(savedDocument));

        } catch (IOException exception) {
            return ResponseEntity.badRequest()
                    .body("Could not read the uploaded file. Please verify it is a valid TXT or PDF.");
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .body(exception.getMessage() != null ? exception.getMessage() : "Invalid document input.");
        } catch (Exception exception) {
            org.slf4j.LoggerFactory.getLogger(HealthDocumentController.class)
                    .error("Unexpected error saving uploaded health document", exception);
            return ResponseEntity.internalServerError()
                    .body("An error occurred while saving the document. Please try again.");
        }
    }

    // Generate an AI summary only for the user's own document
    @PostMapping("/{id}/summarize")
    public ResponseEntity<Map<String, String>> summarizeDocument(
            @PathVariable Long id) {

        var documentOptional = service.getDocumentById(id);

        if (documentOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            HealthDocument document = documentOptional.get();

            String summary = aiSummaryService.summarize(
                    document.getContent()
            );

            return ResponseEntity.ok(
                    Map.of(
                            "documentId", id.toString(),
                            "title", document.getTitle(),
                            "summary", summary
                    )
            );

        } catch (Exception exception) {
            return ResponseEntity.status(503).body(
                    Map.of(
                            "error",
                            "Could not generate the summary. "
                                    + "Please verify that the Gemini AI service is configured and reachable."
                    )
            );
        }
    }

    // Generate a single consolidated summary across all the user's documents and intake responses
    @PostMapping("/consolidated-summary")
    public ResponseEntity<?> generateConsolidatedSummary() {
        List<HealthDocument> documents = service.getAllDocuments();

        if (documents.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "No health documents found. Please upload or add a document first.")
            );
        }

        // Fetch patient's latest intake conversation to combine symptoms with uploaded documents
        String intakeConversation = patientIntakeService.getLatestSessionForCurrentUser()
                .map(PatientIntakeSession::getConversation)
                .orElse("");

        try {
            String summary = aiSummaryService.generateConsolidatedSummary(documents, intakeConversation);
            return ResponseEntity.ok(Map.of(
                    "summary", summary,
                    "documentCount", documents.size(),
                    "hasIntakeData", !intakeConversation.isBlank()
            ));
        } catch (Exception exception) {
            return ResponseEntity.status(503).body(
                    Map.of("error", "Could not generate consolidated summary. Check AI service connectivity.")
            );
        }
    }

    // Ask a question grounded in the user's documents using RAG
    @PostMapping("/rag-qa")
    public ResponseEntity<?> askQuestion(@RequestBody Map<String, String> request) {
        String question = request != null ? request.get("question") : null;

        if (question == null || question.trim().isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Please provide a question.")
            );
        }

        List<HealthDocument> documents = service.getAllDocuments();

        if (documents.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "answer", "You have not uploaded or saved any health documents yet. Please add a document first so I can answer questions about your records.",
                    "sources", List.of()
            ));
        }

        try {
            AiSummaryService.RagResponse ragResponse =
                    aiSummaryService.answerQuestionWithRag(question.trim(), documents);

            return ResponseEntity.ok(Map.of(
                    "question", question.trim(),
                    "answer", ragResponse.answer(),
                    "sources", ragResponse.sources()
            ));
        } catch (Exception exception) {
            return ResponseEntity.status(503).body(
                    Map.of("error", "Failed to process question with AI service. Please check AI service connectivity.")
            );
        }
    }

    // Diagnostic endpoint to securely check Gemini AI connectivity without exposing credentials
    @GetMapping("/ai-diagnostic")
    public ResponseEntity<?> checkAiStatus() {
        var result = aiSummaryService.checkGeminiConnectivity();
        return ResponseEntity.ok(Map.of(
                "successful", result.successful(),
                "statusCode", result.statusCode(),
                "model", result.model(),
                "message", result.message(),
                "snippetPresent", result.generatedSnippet() != null
        ));
    }
}

