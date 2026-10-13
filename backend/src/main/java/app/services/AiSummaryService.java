package app.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import app.model.HealthDocument;

@Service
public class AiSummaryService {

    private static final Logger log = LoggerFactory.getLogger(AiSummaryService.class);

    private final RestClient restClient;
    private final String geminiApiKey;
    private final String geminiModel;
    private final String geminiBaseUrl;

    public record DocumentChunk(
            Long documentId,
            String documentTitle,
            String documentType,
            String text,
            int score
    ) {}

    public record RagResponse(
            String answer,
            List<Map<String, Object>> sources
    ) {}

    @Autowired
    public AiSummaryService(
            @Value("${gemini.api-key:}") String geminiApiKey,
            @Value("${gemini.model:gemini-2.5-flash}") String geminiModel,
            @Value("${gemini.base-url:https://generativelanguage.googleapis.com}") String geminiBaseUrl) {
        String cleanKey = geminiApiKey != null ? geminiApiKey.trim() : "";
        if (cleanKey.startsWith("\"") && cleanKey.endsWith("\"") && cleanKey.length() >= 2) {
            cleanKey = cleanKey.substring(1, cleanKey.length() - 1).trim();
        }
        if (cleanKey.startsWith("'") && cleanKey.endsWith("'") && cleanKey.length() >= 2) {
            cleanKey = cleanKey.substring(1, cleanKey.length() - 1).trim();
        }
        this.geminiApiKey = cleanKey;
        this.geminiModel = (geminiModel != null && !geminiModel.isBlank()) ? geminiModel.trim() : "gemini-2.5-flash";
        this.geminiBaseUrl = (geminiBaseUrl != null && !geminiBaseUrl.isBlank()) ? geminiBaseUrl.trim() : "https://generativelanguage.googleapis.com";

        // Configure timeouts: 10 seconds connect timeout, 30 seconds read timeout
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10000);
        requestFactory.setReadTimeout(30000);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    // Constructor for testing with custom Gemini credentials and URL
    public AiSummaryService(String geminiApiKey, String geminiModel, String geminiBaseUrl, RestClient customClient) {
        String cleanKey = geminiApiKey != null ? geminiApiKey.trim() : "";
        if (cleanKey.startsWith("\"") && cleanKey.endsWith("\"") && cleanKey.length() >= 2) {
            cleanKey = cleanKey.substring(1, cleanKey.length() - 1).trim();
        }
        if (cleanKey.startsWith("'") && cleanKey.endsWith("'") && cleanKey.length() >= 2) {
            cleanKey = cleanKey.substring(1, cleanKey.length() - 1).trim();
        }
        this.geminiApiKey = cleanKey;
        this.geminiModel = (geminiModel != null && !geminiModel.isBlank()) ? geminiModel.trim() : "gemini-2.5-flash";
        this.geminiBaseUrl = (geminiBaseUrl != null && !geminiBaseUrl.isBlank()) ? geminiBaseUrl.trim() : "https://generativelanguage.googleapis.com";
        this.restClient = customClient != null ? customClient : RestClient.builder().build();
    }

    // Default constructor for testing without API key
    public AiSummaryService() {
        this("", "gemini-2.5-flash", "https://generativelanguage.googleapis.com");
    }

    public String getGeminiModel() {
        return geminiModel;
    }

    public String getGeminiBaseUrl() {
        return geminiBaseUrl;
    }

    public boolean hasGeminiApiKey() {
        return !geminiApiKey.isBlank();
    }

    public String getActiveProvider() {
        return "gemini";
    }

    public record GeminiConnectivityResult(
            boolean successful,
            int statusCode,
            String model,
            String message,
            String generatedSnippet
    ) {}

    /**
     * Safe live diagnostic test for Gemini API connectivity.
     * Uses configured environment key and model with a minimal harmless prompt.
     * Never logs or exposes the API key or authorization header.
     */
    public GeminiConnectivityResult checkGeminiConnectivity() {
        if (!hasGeminiApiKey()) {
            return new GeminiConnectivityResult(
                    false,
                    0,
                    geminiModel,
                    "GEMINI_API_KEY is not configured in this environment (missing or empty).",
                    null
            );
        }

        String uri = String.format("%s/v1beta/models/%s:generateContent", geminiBaseUrl, geminiModel);
        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", "Reply with exactly: GEMINI_OK")
                        ))
                )
        );

        try {
            var responseEntity = restClient.post()
                    .uri(uri)
                    .header("x-goog-api-key", geminiApiKey)
                    .body(body)
                    .retrieve()
                    .toEntity(Map.class);

            int status = responseEntity.getStatusCode().value();
            Map<?, ?> respBody = responseEntity.getBody();
            String parsedText = extractGeminiText(respBody);

            return new GeminiConnectivityResult(
                    true,
                    status,
                    geminiModel,
                    "Gemini API answered successfully with HTTP " + status + ".",
                    parsedText
            );
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            String explanation = "HTTP " + status + ": " + (status == 404
                    ? "Model not found or unsupported endpoint for '" + geminiModel + "'."
                    : ex.getStatusText());
            return new GeminiConnectivityResult(false, status, geminiModel, explanation, null);
        } catch (Exception ex) {
            return new GeminiConnectivityResult(false, 0, geminiModel, "Connection failure: " + ex.getMessage(), null);
        }
    }

    /**
     * Dispatch prompt generation to Google Gemini API.
     * Throws an exception if the API key is unconfigured, unreachable, or returns an error,
     * so callers can fall back safely and honestly to structured offline data without fabrication.
     */
    public String generateAiText(String prompt) {
        if (!hasGeminiApiKey()) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured in this environment (missing or empty).");
        }
        return callGemini(prompt);
    }

    /**
     * Call Google Gemini API (v1beta/models/{model}:generateContent)
     */
    private String callGemini(String prompt) {
        String uri = String.format("%s/v1beta/models/%s:generateContent", geminiBaseUrl, geminiModel);

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

        try {
            Map<?, ?> response = restClient.post()
                    .uri(uri)
                    .header("x-goog-api-key", geminiApiKey)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            return extractGeminiText(response);
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            int statusCode = ex.getStatusCode().value();
            String errorCategory;
            if (statusCode == 401 || statusCode == 403) {
                errorCategory = "Authentication failure: invalid or unauthorized Gemini API key.";
            } else if (statusCode == 404) {
                errorCategory = "Model not found or unsupported endpoint for '" + geminiModel + "'. Check that the model name is supported on Google Gemini API.";
            } else if (statusCode == 429) {
                errorCategory = "Rate limit or quota exceeded for Gemini API.";
            } else if (statusCode == 400) {
                errorCategory = "Invalid request payload format for Gemini API.";
            } else {
                errorCategory = "HTTP " + statusCode + " (" + ex.getStatusText() + ").";
            }
            log.warn("Gemini API call failed [Status: {}, Category: {}]", statusCode, errorCategory);
            throw new RuntimeException("Gemini API error (" + statusCode + "): " + errorCategory, ex);
        } catch (org.springframework.web.client.ResourceAccessException ex) {
            String errorCategory = "Network failure or timeout connecting to Gemini API (" + geminiBaseUrl + ").";
            log.warn("Gemini API connection error: {}", errorCategory);
            throw new RuntimeException("Gemini API network error: " + errorCategory, ex);
        } catch (Exception ex) {
            log.warn("Gemini API call failed: {}", ex.getMessage());
            throw new RuntimeException("Gemini API call failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Helper to parse the Gemini candidates[0].content.parts[0].text JSON response structure.
     */
    public String extractGeminiText(Map<?, ?> response) {
        if (response == null) {
            throw new RuntimeException("Empty response received from Gemini API");
        }

        Object candidatesObj = response.get("candidates");
        if (!(candidatesObj instanceof List<?> candidates) || candidates.isEmpty()) {
            throw new RuntimeException("No candidates returned in Gemini API response");
        }

        Object candidateObj = candidates.get(0);
        if (!(candidateObj instanceof Map<?, ?> candidate)) {
            throw new RuntimeException("Malformed candidate object in Gemini response");
        }

        Object contentObj = candidate.get("content");
        if (!(contentObj instanceof Map<?, ?> content)) {
            throw new RuntimeException("Malformed content in Gemini candidate");
        }

        Object partsObj = content.get("parts");
        if (!(partsObj instanceof List<?> parts) || parts.isEmpty()) {
            throw new RuntimeException("No parts returned in Gemini content");
        }

        Object partObj = parts.get(0);
        if (!(partObj instanceof Map<?, ?> part)) {
            throw new RuntimeException("Malformed part in Gemini response");
        }

        Object textObj = part.get("text");
        if (textObj == null) {
            throw new RuntimeException("No text field found in Gemini part");
        }

        return textObj.toString().trim();
    }


    // Single document summary
    public String summarize(String documentText) {
        if (documentText == null || documentText.isBlank()) {
            throw new IllegalArgumentException("Document content cannot be empty.");
        }

        String prompt = """
                You are a health document summarization assistant.
                Summarize the following health document in simple language.
                
                Include:
                1. Type of report
                2. Important findings and values
                3. Values outside reference ranges, if provided
                4. A short overall summary
                
                Do not invent medical values or diagnoses.
                Mention when information is missing.
                Explain that this summary is for information only
                and does not replace professional medical advice.
                
                Document:
                """ + documentText;

        try {
            String text = generateAiText(prompt);
            if (text != null && !text.isBlank()) {
                return text;
            }
        } catch (Exception e) {
            log.warn("AI document summarization failed (provider: {}): {}", getActiveProvider(), e.getMessage());
            return generateFallbackSingleDocumentSummary(documentText);
        }

        return generateFallbackSingleDocumentSummary(documentText);
    }

    private String generateFallbackSingleDocumentSummary(String text) {
        String excerpt = text.trim();
        if (excerpt.length() > 300) {
            excerpt = excerpt.substring(0, 300) + "...";
        }

        String providerName = hasGeminiApiKey() ? "Gemini" : "AI service";
        return "DOCUMENT SUMMARY (OFFLINE PREVIEW)\n\n"
                + "Recorded Content:\n"
                + excerpt + "\n\n"
                + "(Note: " + providerName + " is currently offline or unreachable; displaying stored document preview directly from your records).\n\n"
                + "Notice: This summary is for reference and does not replace professional medical advice.";
    }

    // Consolidated summary across all documents belonging to the user
    public String generateConsolidatedSummary(List<HealthDocument> documents) {
        return generateConsolidatedSummary(documents, "");
    }

    // Consolidated patient-centered summary combining uploaded documents and intake responses
    public String generateConsolidatedSummary(List<HealthDocument> documents, String intakeConversation) {
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("No documents available to summarize.");
        }

        // If total document content is large, process in manageable chunks and combine intermediate findings
        String combinedClinicalText = summarizeInChunksIfNeeded(documents);

        String intakeSection = (intakeConversation != null && !intakeConversation.isBlank())
                ? "\n\nPATIENT INTAKE CONVERSATION (Reported Current Symptoms & Answers):\n" + intakeConversation.trim()
                : "\n\nPATIENT INTAKE CONVERSATION:\n(No intake questionnaire recorded for this session. Patient has not yet submitted intake chat answers.)";

        String prompt = """
                You are a careful medical summary assistant helping a patient organize their health records.
                Synthesize a clear, patient-centered clinical summary by combining:
                1. The patient's uploaded health documents (lab reports, doctor notes, prescriptions).
                2. The patient's current symptoms and answers from their intake dialogue.

                You MUST organize the summary into EXACTLY these eight sections:
                1. Patient's Current Problem
                2. Relevant Medical History
                3. Possible Explanations (not a diagnosis)
                4. Findings That Support or Do Not Support Each Possibility
                5. Important Test Results
                6. Current Medications and Documented Allergies
                7. Missing Information
                8. Suggested Next Steps

                CRITICAL CLINICAL & SAFETY RULES:
                1. GROUNDING: Ground every patient-specific statement strictly in the available records or intake answers.
                   - NEVER invent symptoms, lab values, medications, allergies, diagnoses, or history.
                   - If a detail was not provided or is absent in records, clearly write "Not documented in available records" or "Not provided by patient".
                2. METADATA VS CLINICAL HISTORY: Treat document disclaimers, simulated/fabricated-data notices, test labels, or confidentiality warnings as document metadata, NOT as patient medical history or health conditions.
                3. NO DEFINITIVE DIAGNOSES: 'Possible Explanations' must be cautious, tentative possibilities to discuss with a clinician, NEVER definitive diagnoses.
                   - Explain the evidence and uncertainty for each possibility.
                   - Do NOT claim that a symptom or test result caused another condition unless direct evidence in the source documents explicitly states that conclusion.
                4. NO MEDICATION CHANGES: Do NOT recommend starting, stopping, or changing any medication or dosage.
                5. ACCURATE LAB VALUES: Extract and present relevant laboratory values with their exact numbers, units, and reference ranges whenever present in the source.
                   - Preserve original values accurately.
                   - Do NOT independently invent a reference range or declare a result abnormal without adequate evidence in the source document.
                6. NO RAW TEXT DUMPING OR DUPLICATION: Do not repeat raw document passages under multiple headings. Remove duplicate records/passages, select relevant facts, and organize them concisely into bullet points.
                7. CONFLICTING RECORDS: If multiple documents or records conflict (e.g., conflicting dates, test values, or dosages), explicitly highlight the discrepancy rather than silently picking one.
                8. EMERGENCY WARNING SIGNS: Under 'Suggested Next Steps', include a clear safety notice advising professional medical evaluation, and explicitly identify red-flag emergency symptoms (such as sudden severe chest pain, shortness of breath, loss of consciousness, sudden numbness/weakness, heavy uncontrolled bleeding) that warrant immediate emergency medical care without waiting or finishing questionnaires.

                DOCUMENTS ON FILE:
                %s
                %s
                """.formatted(combinedClinicalText, intakeSection);

        try {
            String text = generateAiText(prompt);
            if (text != null && !text.isBlank()) {
                return text;
            }
        } catch (Exception e) {
            log.warn("AI consolidated summarization failed (provider: {}): {}", getActiveProvider(), e.getMessage());
            return generateFallbackConsolidatedSummary(documents, intakeConversation, e.getMessage());
        }

        return generateFallbackConsolidatedSummary(documents, intakeConversation, "Empty response received from Gemini API");
    }

    // Process large document sets in manageable chunks to avoid context truncation
    private String summarizeInChunksIfNeeded(List<HealthDocument> documents) {
        int totalLength = documents.stream()
                .mapToInt(d -> d.getContent() != null ? d.getContent().length() : 0)
                .sum();

        // If reasonably small, pass directly without intermediate multi-step summarization
        if (totalLength <= 6000) {
            StringBuilder combined = new StringBuilder();
            for (int i = 0; i < documents.size(); i++) {
                HealthDocument doc = documents.get(i);
                combined.append("=== DOCUMENT ").append(i + 1).append(": ")
                        .append(doc.getTitle() != null ? doc.getTitle() : "Untitled")
                        .append(" (Category: ")
                        .append(doc.getDocumentType() != null ? doc.getDocumentType() : "General")
                        .append(") ===\n")
                        .append(doc.getContent() != null ? doc.getContent().trim() : "")
                        .append("\n\n");
            }
            return combined.toString();
        }

        // For large content, chunk into segments and compile intermediate key findings
        List<String> segments = new ArrayList<>();
        StringBuilder currentSegment = new StringBuilder();

        for (int i = 0; i < documents.size(); i++) {
            HealthDocument doc = documents.get(i);
            String docText = "=== DOCUMENT " + (i + 1) + ": "
                    + (doc.getTitle() != null ? doc.getTitle() : "Untitled")
                    + " (" + (doc.getDocumentType() != null ? doc.getDocumentType() : "General") + ") ===\n"
                    + (doc.getContent() != null ? doc.getContent().trim() : "")
                    + "\n\n";

            if (currentSegment.length() + docText.length() > 3000 && currentSegment.length() > 0) {
                segments.add(currentSegment.toString());
                currentSegment = new StringBuilder();
            }
            currentSegment.append(docText);
        }
        if (currentSegment.length() > 0) {
            segments.add(currentSegment.toString());
        }

        StringBuilder combinedIntermediates = new StringBuilder();
        for (int s = 0; s < segments.size(); s++) {
            try {
                String chunkPrompt = """
                        Extract all key clinical findings, diagnoses, medications, test values, and physician recommendations from this document segment:
                        
                        """ + segments.get(s);

                String intermediate = generateAiText(chunkPrompt);
                if (intermediate != null && !intermediate.isBlank()) {
                    combinedIntermediates.append("[INTERMEDIATE FINDINGS BATCH ")
                            .append(s + 1).append("]\n")
                            .append(intermediate)
                            .append("\n\n");
                    continue;
                }
            } catch (Exception ignored) {
                // Fall back to original segment text if intermediate call fails
            }
            combinedIntermediates.append(segments.get(s));
        }

        return combinedIntermediates.toString();
    }

    // RAG: Answer questions grounded strictly in the user's document text
    public RagResponse answerQuestionWithRag(String question, List<HealthDocument> documents) {
        if (question == null || question.trim().isBlank()) {
            throw new IllegalArgumentException("Question cannot be empty.");
        }

        if (documents == null || documents.isEmpty()) {
            return new RagResponse(
                    "You do not have any saved health documents. Please upload or add a document first.",
                    List.of()
            );
        }

        // 1. Chunk documents into reasonably sized passages
        List<DocumentChunk> allChunks = chunkDocuments(documents);

        // 2. Retrieve top-scoring relevant chunks using query keywords and recency
        List<DocumentChunk> topChunks = retrieveRelevantChunks(question, allChunks);

        // If no relevant chunks contain information regarding the query
        if (topChunks.isEmpty() || topChunks.stream().allMatch(c -> c.score() == 0)) {
            return new RagResponse(
                    "The provided health documents do not contain information regarding: \"" + question.trim() + "\".",
                    List.of()
            );
        }

        // Filter out zero-score chunks and limit to the most relevant passages
        List<DocumentChunk> relevantChunks = topChunks.stream()
                .filter(c -> c.score() > 0)
                .limit(3)
                .toList();

        if (relevantChunks.isEmpty()) {
            return new RagResponse(
                    "The provided health documents do not contain information regarding: \"" + question.trim() + "\".",
                    List.of()
            );
        }

        // 3. Deduplicate passages: prevent duplicate or overlapping passages
        List<DocumentChunk> deduplicatedChunks = new ArrayList<>();
        Set<String> seenNormalizedTexts = new HashSet<>();

        for (DocumentChunk chunk : relevantChunks) {
            String normalized = chunk.text().replaceAll("\\s+", " ").trim().toLowerCase();
            if (normalized.length() > 60) {
                normalized = normalized.substring(0, 60);
            }
            if (!seenNormalizedTexts.contains(normalized)) {
                seenNormalizedTexts.add(normalized);
                deduplicatedChunks.add(chunk);
            }
        }

        // 4. Build focused context with clear source boundaries
        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < deduplicatedChunks.size(); i++) {
            DocumentChunk chunk = deduplicatedChunks.get(i);
            contextBuilder.append("--- EXCERPT ").append(i + 1).append(" (Source: \"")
                    .append(chunk.documentTitle()).append("\", Category: ")
                    .append(chunk.documentType()).append(") ---\n")
                    .append(chunk.text().trim()).append("\n\n");
        }

        String prompt = """
                You are a health-document assistant. Answer the user's question directly using only the supplied document excerpts. Do not reproduce the full document. Summarize, compare, or extract only the information needed to answer the question. If the user asks about medications, report only medications explicitly mentioned in the evidence. Do not interpret test values as medications. If the evidence does not contain the requested information, clearly state that it was not found. Never invent medical facts, diagnoses, medication names, dosages, or results. Distinguish reported facts from general explanations.

                DOCUMENT EXCERPTS:
                %s
                USER QUESTION:
                %s

                ANSWER:
                """.formatted(contextBuilder.toString(), question.trim());

        // Extract clean unique sources for citation (strictly one source card per document ID)
        Map<Long, Map<String, Object>> uniqueSourcesMap = new java.util.LinkedHashMap<>();
        for (DocumentChunk chunk : deduplicatedChunks) {
            if (chunk.documentId() != null && !uniqueSourcesMap.containsKey(chunk.documentId())) {
                Map<String, Object> s = new HashMap<>();
                s.put("documentId", chunk.documentId());
                s.put("title", chunk.documentTitle());
                s.put("documentType", chunk.documentType());
                String snippet = chunk.text().trim();
                if (snippet.length() > 180) {
                    snippet = snippet.substring(0, 180) + "...";
                }
                s.put("excerpt", snippet);
                uniqueSourcesMap.put(chunk.documentId(), s);
            }
        }
        List<Map<String, Object>> sources = new ArrayList<>(uniqueSourcesMap.values());

        try {
            String answer = generateAiText(prompt);
            if (answer != null && !answer.isBlank()) {
                return new RagResponse(answer, sources);
            }
        } catch (Exception e) {
            log.warn("AI generation failed for question: '{}' (provider: {}): {}", question.trim(), getActiveProvider(), e.getMessage());
            return generateFallbackRagAnswer(question, deduplicatedChunks, sources, e.getMessage());
        }

        return generateFallbackRagAnswer(question, deduplicatedChunks, sources, "Empty response received from Gemini API");
    }

    // Helper: Split documents into paragraph chunks
    public List<DocumentChunk> chunkDocuments(List<HealthDocument> documents) {
        List<DocumentChunk> chunks = new ArrayList<>();

        for (HealthDocument doc : documents) {
            String content = doc.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }

            String[] paragraphs = content.split("(\\r?\\n){2,}");
            for (String para : paragraphs) {
                String cleanPara = para.trim();
                if (!cleanPara.isEmpty()) {
                    chunks.add(new DocumentChunk(
                            doc.getId(),
                            doc.getTitle() != null ? doc.getTitle() : "Untitled Document",
                            doc.getDocumentType() != null ? doc.getDocumentType() : "General",
                            cleanPara,
                            0
                    ));
                }
            }
        }

        return chunks;
    }

    // Helper: Keyword-based retrieval with stemming, stopword filtering, and recency priority
    public List<DocumentChunk> retrieveRelevantChunks(String question, List<DocumentChunk> chunks) {
        Set<String> stopWords = new HashSet<>(Arrays.asList(
                "the", "and", "for", "with", "that", "this", "what", "when", "where",
                "which", "who", "whom", "whose", "why", "how", "are", "was", "were",
                "been", "being", "have", "has", "had", "does", "did", "doing", "would",
                "could", "should", "from", "about", "into", "through", "during", "before",
                "after", "above", "below", "between", "under", "again", "further", "then",
                "once", "here", "there", "any", "both", "each", "few", "more", "most",
                "other", "some", "such", "only", "own", "same", "than", "too", "very",
                "can", "will", "just", "now", "tell", "show", "give", "list", "please",
                "my", "me", "our", "all", "get", "say", "find",
                "document", "documents", "record", "records", "file", "files", "mentioned",
                "report", "reports", "result", "results", "summarize", "summary", "test", "tests"
        ));

        String lowerQuestion = question.toLowerCase();
        boolean queryAsksLatest = lowerQuestion.contains("latest") || lowerQuestion.contains("recent") || lowerQuestion.contains("newest");

        Set<String> rawTerms = Arrays.stream(lowerQuestion.split("[^a-zA-Z0-9]+"))
                .filter(term -> term.length() >= 3)
                .filter(term -> !stopWords.contains(term))
                .collect(Collectors.toSet());

        Set<String> queryTerms = new HashSet<>();
        for (String term : rawTerms) {
            queryTerms.add(term);
            if (term.endsWith("ies") && term.length() > 4) {
                queryTerms.add(term.substring(0, term.length() - 3) + "y");
                queryTerms.add(term.substring(0, term.length() - 3));
            } else if (term.endsWith("es") && term.length() > 4) {
                queryTerms.add(term.substring(0, term.length() - 2));
            } else if (term.endsWith("s") && term.length() > 3) {
                queryTerms.add(term.substring(0, term.length() - 1));
            } else if (term.endsWith("ing") && term.length() > 5) {
                queryTerms.add(term.substring(0, term.length() - 3));
            } else if (term.endsWith("ed") && term.length() > 4) {
                queryTerms.add(term.substring(0, term.length() - 2));
            }
        }

        if (queryTerms.contains("medication") || queryTerms.contains("medicine") || queryTerms.contains("drug")) {
            queryTerms.add("prescription");
            queryTerms.add("prescribed");
            queryTerms.add("tablet");
            queryTerms.add("capsule");
            queryTerms.add("dose");
            queryTerms.add("dosage");
            queryTerms.add("rx");
            queryTerms.add("mg");
        }
        if (queryTerms.contains("blood")) {
            queryTerms.add("cbc");
            queryTerms.add("hemoglobin");
            queryTerms.add("glucose");
            queryTerms.add("panel");
            queryTerms.add("lab");
        }

        long maxDocId = chunks.stream()
                .mapToLong(c -> c.documentId() != null ? c.documentId() : 0L)
                .max()
                .orElse(0L);

        List<DocumentChunk> scoredChunks = new ArrayList<>();

        for (DocumentChunk chunk : chunks) {
            int score = 0;
            String lowerText = chunk.text().toLowerCase();
            String lowerTitle = chunk.documentTitle().toLowerCase();
            String lowerType = chunk.documentType().toLowerCase();

            if (!queryTerms.isEmpty()) {
                for (String term : queryTerms) {
                    if (lowerText.contains(term)) {
                        score += 3;
                    }
                    if (lowerTitle.contains(term)) {
                        score += 4;
                    }
                    if (lowerType.contains(term)) {
                        score += 2;
                    }
                }
            }

            if (queryAsksLatest && score > 0 && chunk.documentId() != null && chunk.documentId() == maxDocId) {
                score += 5;
            }

            scoredChunks.add(new DocumentChunk(
                    chunk.documentId(),
                    chunk.documentTitle(),
                    chunk.documentType(),
                    chunk.text(),
                    score
            ));
        }

        scoredChunks.sort((a, b) -> {
            int scoreCompare = Integer.compare(b.score(), a.score());
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            long idA = a.documentId() != null ? a.documentId() : 0L;
            long idB = b.documentId() != null ? b.documentId() : 0L;
            return Long.compare(idB, idA);
        });

        return scoredChunks.stream()
                .filter(c -> c.score() > 0)
                .limit(4)
                .toList();
    }

    public String generateFallbackConsolidatedSummary(List<HealthDocument> documents) {
        return generateFallbackConsolidatedSummary(documents, "", "");
    }

    public String generateFallbackConsolidatedSummary(List<HealthDocument> documents, String intakeConversation) {
        return generateFallbackConsolidatedSummary(documents, intakeConversation, "");
    }

    public String generateFallbackConsolidatedSummary(List<HealthDocument> documents, String intakeConversation, String failureReason) {
        StringBuilder sb = new StringBuilder();

        sb.append("CONSOLIDATED HEALTH SUMMARY (OFFLINE MODE)\n\n");
        if (failureReason != null && !failureReason.isBlank()) {
            sb.append("AI Service Status: ").append(failureReason).append("\n\n");
        } else if (!hasGeminiApiKey()) {
            sb.append("Note: GEMINI_API_KEY is not configured in this environment. ")
              .append("Displaying structured medical overview extracted directly from your saved database records and intake dialogue. ")
              .append("It is not an AI-generated clinical diagnosis or interpretation.\n\n");
        } else {
            sb.append("Note: The AI service (Gemini API) was unreachable or returned an error. ")
              .append("Displaying structured medical overview extracted directly from your saved database records and intake dialogue. ")
              .append("It is not an AI-generated clinical diagnosis or interpretation.\n\n");
        }

        // 1. Patient's Current Problem
        sb.append("1. Patient's Current Problem\n");
        if (intakeConversation != null && !intakeConversation.isBlank()) {
            List<String> patientStatements = extractPatientStatements(intakeConversation);
            if (!patientStatements.isEmpty()) {
                for (String statement : patientStatements) {
                    sb.append("• Reported by patient: \"").append(statement).append("\"\n");
                }
            } else {
                sb.append("• Recorded in intake dialogue session.\n");
            }
        } else {
            sb.append("• No patient intake questionnaire submitted. Information not provided by patient.\n");
        }
        sb.append("\n");

        // 2. Relevant Medical History
        sb.append("2. Relevant Medical History\n");
        List<String> historyItems = new ArrayList<>();
        for (HealthDocument doc : documents) {
            String content = doc.getContent() != null ? doc.getContent() : "";
            for (String line : content.split("\\r?\\n")) {
                String lineLower = line.toLowerCase();
                // Treat disclaimers and test warnings as metadata, not clinical history
                if (lineLower.contains("disclaimer") || lineLower.contains("fabricated") || lineLower.contains("fictional")
                        || lineLower.contains("simulation") || lineLower.contains("confidentiality notice")
                        || lineLower.contains("for reference only")) {
                    continue;
                }
                if ((lineLower.contains("history") || lineLower.contains("diagnos") || lineLower.contains("hypertension")
                        || lineLower.contains("diabetes") || lineLower.contains("asthma") || lineLower.contains("past medical"))
                        && !lineLower.contains("reference range") && line.trim().length() > 3) {
                    String clean = line.trim();
                    if (!historyItems.contains(clean)) {
                        historyItems.add(clean + " (from " + doc.getTitle() + ")");
                    }
                }
            }
        }
        if (!historyItems.isEmpty()) {
            for (String item : historyItems) {
                sb.append("• ").append(item).append("\n");
            }
        } else {
            sb.append("• No past medical conditions documented in available health records.\n");
        }
        sb.append("\n");

        // 3. Possible Explanations (not a diagnosis)
        sb.append("3. Possible Explanations (not a diagnosis)\n");
        sb.append("• Automated offline mode cannot generate clinical differential interpretations. ")
          .append("Any possible medical explanations must be evaluated in person by a qualified physician.\n\n");

        // 4. Findings That Support or Do Not Support Each Possibility
        sb.append("4. Findings That Support or Do Not Support Each Possibility\n");
        sb.append("• Evaluation of supporting and non-supporting clinical evidence requires active physician review.\n\n");

        // 5. Important Test Results
        sb.append("5. Important Test Results\n");
        List<String> labLines = new ArrayList<>();
        for (HealthDocument doc : documents) {
            String content = doc.getContent() != null ? doc.getContent() : "";
            for (String line : content.split("\\r?\\n")) {
                String lineLower = line.toLowerCase();
                if ((lineLower.contains("hemoglobin") || lineLower.contains("glucose") || lineLower.contains("cholesterol")
                        || lineLower.contains("wbc") || lineLower.contains("rbc") || lineLower.contains("platelet")
                        || lineLower.contains("creatinine") || lineLower.contains("potassium") || lineLower.contains("sodium")
                        || lineLower.contains("blood pressure") || lineLower.contains("mg/dl") || lineLower.contains("g/dl"))
                        && line.trim().length() > 3) {
                    String clean = line.trim();
                    if (!labLines.contains(clean)) {
                        labLines.add(clean + " (Source: " + doc.getTitle() + ")");
                    }
                }
            }
        }
        if (!labLines.isEmpty()) {
            for (String lab : labLines) {
                sb.append("• ").append(lab).append("\n");
            }
        } else {
            sb.append("• No specific laboratory or diagnostic test results recorded in uploaded records.\n");
        }
        sb.append("\n");

        // 6. Current Medications and Documented Allergies
        sb.append("6. Current Medications and Documented Allergies\n");
        List<String> medLines = new ArrayList<>();
        List<String> allergyLines = new ArrayList<>();
        for (HealthDocument doc : documents) {
            String content = doc.getContent() != null ? doc.getContent() : "";
            for (String line : content.split("\\r?\\n")) {
                String lineLower = line.toLowerCase();
                if ((lineLower.contains("tablet") || lineLower.contains("capsule") || lineLower.contains("mg")
                        || lineLower.contains("prescribed") || lineLower.contains("rx") || lineLower.contains("daily")
                        || lineLower.contains("atorvastatin") || lineLower.contains("metformin") || lineLower.contains("lisinopril"))
                        && !lineLower.contains("reference range") && !lineLower.contains("mg/dl") && line.trim().length() > 3) {
                    String clean = line.trim();
                    if (!medLines.contains(clean)) {
                        medLines.add(clean + " (from " + doc.getTitle() + ")");
                    }
                }
                if (lineLower.contains("allerg") && line.trim().length() > 3) {
                    String clean = line.trim();
                    if (!allergyLines.contains(clean)) {
                        allergyLines.add(clean + " (from " + doc.getTitle() + ")");
                    }
                }
            }
        }
        sb.append("Medications:\n");
        if (!medLines.isEmpty()) {
            for (String med : medLines) {
                sb.append("• ").append(med).append("\n");
            }
        } else {
            sb.append("• No prescription medications documented in uploaded records.\n");
        }
        sb.append("Allergies:\n");
        if (!allergyLines.isEmpty()) {
            for (String allergy : allergyLines) {
                sb.append("• ").append(allergy).append("\n");
            }
        } else {
            sb.append("• No known allergies documented in available records.\n");
        }
        sb.append("\n");

        // 7. Missing Information
        String providerName = hasGeminiApiKey() ? "Gemini API" : "AI service";
        sb.append("7. Missing Information\n");
        sb.append("• Automated AI clinical interpretation is temporarily offline (" + providerName + " unavailable).\n");
        if (intakeConversation == null || intakeConversation.isBlank()) {
            sb.append("• Patient intake symptom questionnaire has not been submitted for this session.\n");
        }
        sb.append("• Full physician physical examination, updated vital signs, and complete clinical history are pending.\n\n");

        // 8. Suggested Next Steps
        sb.append("8. Suggested Next Steps\n");
        sb.append("• Schedule an in-person appointment with your licensed healthcare provider to review these findings and discuss treatment options.\n");
        sb.append("• Do not start, stop, or adjust any medications without consulting your prescribing physician.\n");
        sb.append("• EMERGENCY WARNING: If you or the patient experience severe red-flag warning signs—including sudden chest pain or pressure, severe shortness of breath, loss of consciousness, facial drooping, sudden weakness/numbness, or heavy uncontrollable bleeding—seek immediate emergency medical care (call your local emergency number or go to the nearest emergency room). Do not delay urgent care by completing questionnaires or waiting for computer summaries.\n\n");

        sb.append("Notice: This summary is generated for personal reference only and does not substitute for clinical judgment or professional medical care.");

        return sb.toString();
    }

    // Helper to pull patient responses from the intake multi-turn conversation string
    private List<String> extractPatientStatements(String conversation) {
        List<String> statements = new ArrayList<>();
        if (conversation == null) return statements;

        for (String line : conversation.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Patient:")) {
                String statement = trimmed.substring(8).trim();
                if (!statement.isEmpty() && !statements.contains(statement)) {
                    statements.add(statement);
                }
            }
        }
        return statements;
    }

    public RagResponse generateFallbackRagAnswer(String question, List<DocumentChunk> chunks, List<Map<String, Object>> sources) {
        return generateFallbackRagAnswer(question, chunks, sources, "");
    }

    public RagResponse generateFallbackRagAnswer(String question, List<DocumentChunk> chunks, List<Map<String, Object>> sources, String failureReason) {
        if (sources.isEmpty() || chunks.isEmpty()) {
            return new RagResponse(
                    "The provided health documents do not contain information regarding: \"" + question + "\".",
                    List.of()
            );
        }

        String diagnosticNotice = (failureReason != null && !failureReason.isBlank())
                ? "[AI Status: " + failureReason + "]\n\n"
                : "";

        String lowerQ = question.toLowerCase();

        // 1. Check for specific medication inquiries
        if (lowerQ.contains("medicat") || lowerQ.contains("prescription") || lowerQ.contains("drug")) {
            List<String> foundMedLines = new ArrayList<>();
            for (DocumentChunk chunk : chunks) {
                for (String line : chunk.text().split("\\r?\\n")) {
                    String lineLower = line.toLowerCase();
                    if (lineLower.contains("tablet") || lineLower.contains("capsule") || lineLower.contains("mg")
                            || lineLower.contains("prescribed") || lineLower.contains("take ") || lineLower.contains("dosage")) {
                        if (!lineLower.contains("mg/dl") && !lineLower.contains("reference range") && !lineLower.contains("test result")) {
                            foundMedLines.add(line.trim() + " (from " + chunk.documentTitle() + ")");
                        }
                    }
                }
            }

            if (!foundMedLines.isEmpty()) {
                String answer = "OFFLINE RETRIEVAL (Extracted Medications):\n"
                        + diagnosticNotice
                        + String.join("\n", foundMedLines)
                        + "\n\n(Note: Generated via offline document search because AI service is unavailable).";
                return new RagResponse(answer, sources);
            } else {
                return new RagResponse(
                        "OFFLINE RETRIEVAL: No medications were found in the available document excerpts.\n\n" + diagnosticNotice,
                        sources
                );
            }
        }

        // 2. Check for hemoglobin inquiry
        if (lowerQ.contains("hemoglobin") || lowerQ.contains("hb")) {
            for (DocumentChunk chunk : chunks) {
                for (String line : chunk.text().split("\\r?\\n")) {
                    if (line.toLowerCase().contains("hemoglobin") || line.toLowerCase().contains("hb ")) {
                        String answer = "OFFLINE RETRIEVAL (Extracted Test Result):\n"
                                + diagnosticNotice
                                + line.trim() + " (Source: " + chunk.documentTitle() + ")"
                                + "\n\n(Note: Extracted from database records because AI service is unavailable).";
                        return new RagResponse(answer, sources);
                    }
                }
            }
        }

        // 3. For all other queries, return a clean offline message with the diagnosed status
        DocumentChunk topChunk = chunks.get(0);
        String answer = "OFFLINE RETRIEVAL (AI Synthesis Unavailable):\n"
                + diagnosticNotice
                + "Relevant information was located in: \"" + topChunk.documentTitle() + "\" (" + topChunk.documentType() + ").\n\n"
                + "Please refer to the cited source excerpts below for details.";

        return new RagResponse(answer, sources);
    }
}
