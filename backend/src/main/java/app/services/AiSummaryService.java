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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import app.model.HealthDocument;

@Service
public class AiSummaryService {

    private final RestClient restClient;
    private final String ollamaBaseUrl;

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

    public AiSummaryService(
            @Value("${app.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl) {
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.restClient = RestClient.builder()
                .baseUrl(ollamaBaseUrl)
                .build();
    }

    // Single document summary (retained for backward compatibility)
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

        Map<String, Object> request = new HashMap<>();
        request.put("model", "llama3.2:3b");
        request.put("prompt", prompt);
        request.put("stream", false);

        try {
            Map<?, ?> response = restClient.post()
                    .uri("/api/generate")
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.get("response") != null) {
                return response.get("response").toString().trim();
            }
        } catch (Exception e) {
            return generateFallbackSingleDocumentSummary(documentText);
        }

        return generateFallbackSingleDocumentSummary(documentText);
    }

    private String generateFallbackSingleDocumentSummary(String text) {
        String excerpt = text.trim();
        if (excerpt.length() > 300) {
            excerpt = excerpt.substring(0, 300) + "...";
        }

        return "DOCUMENT SUMMARY (OFFLINE PREVIEW)\n\n"
                + "Recorded Content:\n"
                + excerpt + "\n\n"
                + "(Note: Ollama is currently offline; displaying stored document preview directly from your records).\n\n"
                + "Notice: This summary is for reference and does not replace professional medical advice.";
    }

    // Consolidated summary across all documents belonging to the user
    // Consolidated summary across all documents belonging to the user
    public String generateConsolidatedSummary(List<HealthDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("No documents available to summarize.");
        }

        // If total document content is large, process in manageable chunks and combine intermediate findings
        String combinedClinicalText = summarizeInChunksIfNeeded(documents);

        String prompt = """
                You are a medical record summarization assistant.
                Create ONE unified, consolidated clinical summary based ONLY on the health documents provided below.
                
                Organize the summary into these clear sections where information is present in the source documents:
                - Overall Health Overview
                - Main Complaints or Conditions
                - Medical History
                - Medications
                - Allergies
                - Important Test Results
                - Symptoms and Observations
                - Follow-up Recommendations Mentioned in the Documents
                - Missing or Unclear Information
                
                CRITICAL RULES:
                1. Use ONLY facts explicitly written in the provided documents.
                2. Do not invent medical conditions, diagnoses, medications, test values, or physician recommendations.
                3. Only include a section if there is actual information for it in the documents.
                4. In 'Missing or Unclear Information', note any gaps (e.g. missing dates, incomplete lab units, unmentioned allergies).
                5. Add a clear closing notice that this summary is for informational reference only and does not replace a doctor's consultation.
                
                DOCUMENTS:
                %s
                """.formatted(combinedClinicalText);

        Map<String, Object> request = new HashMap<>();
        request.put("model", "llama3.2:3b");
        request.put("prompt", prompt);
        request.put("stream", false);

        try {
            Map<?, ?> response = restClient.post()
                    .uri("/api/generate")
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.get("response") != null) {
                return response.get("response").toString().trim();
            }
        } catch (Exception e) {
            // Fallback structured overview when Ollama is offline
            return generateFallbackConsolidatedSummary(documents);
        }

        return generateFallbackConsolidatedSummary(documents);
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

                Map<String, Object> req = new HashMap<>();
                req.put("model", "llama3.2:3b");
                req.put("prompt", chunkPrompt);
                req.put("stream", false);

                Map<?, ?> resp = restClient.post()
                        .uri("/api/generate")
                        .body(req)
                        .retrieve()
                        .body(Map.class);

                if (resp != null && resp.get("response") != null) {
                    combinedIntermediates.append("[INTERMEDIATE FINDINGS BATCH ")
                            .append(s + 1).append("]\n")
                            .append(resp.get("response").toString().trim())
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

        // 3. Build focused context for Ollama with clear source boundaries
        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < relevantChunks.size(); i++) {
            DocumentChunk chunk = relevantChunks.get(i);
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

        // Extract clean unique sources for citation
        List<Map<String, Object>> sources = relevantChunks.stream()
                .map(chunk -> {
            Map<String, Object> s = new HashMap<>();
            s.put("documentId", chunk.documentId());
            s.put("title", chunk.documentTitle());
            s.put("documentType", chunk.documentType());
            String snippet = chunk.text().trim();
            if (snippet.length() > 180) {
                snippet = snippet.substring(0, 180) + "...";
            }
            s.put("excerpt", snippet);
            return s;
        })
                .collect(Collectors.toMap(
                        m -> (Long) m.get("documentId"),
                        m -> m,
                        (existing, replacement) -> existing
                ))
                .values()
                .stream()
                .toList();

        try {
            Map<String, Object> request = new HashMap<>();
            request.put("model", "llama3.2:3b");
            request.put("prompt", prompt);
            request.put("stream", false);

            Map<?, ?> response = restClient.post()
                    .uri("/api/generate")
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.get("response") != null) {
                String answer = response.get("response").toString().trim();
                if (!answer.isBlank()) {
                    return new RagResponse(answer, sources);
                }
            }
        } catch (Exception e) {
            // Log technical details safely on backend without exposing secrets
            org.slf4j.LoggerFactory.getLogger(AiSummaryService.class)
                    .warn("Ollama AI generation failed for question: {} (service may be offline: {})", question.trim(), e.getMessage());
            return generateFallbackRagAnswer(question, relevantChunks, sources);
        }

        return generateFallbackRagAnswer(question, relevantChunks, sources);
    }

    // Helper: Split documents into paragraph chunks
    public List<DocumentChunk> chunkDocuments(List<HealthDocument> documents) {
        List<DocumentChunk> chunks = new ArrayList<>();

        for (HealthDocument doc : documents) {
            String content = doc.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }

            // Split by paragraph or double newlines
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
                // Conversational/meta words that should not inflate chunk relevance across documents
                "document", "documents", "record", "records", "file", "files", "mentioned",
                "report", "reports", "result", "results", "summarize", "summary", "test", "tests"
        ));

        String lowerQuestion = question.toLowerCase();
        boolean queryAsksLatest = lowerQuestion.contains("latest") || lowerQuestion.contains("recent") || lowerQuestion.contains("newest");

        // Extract query terms (alphanumeric, >= 3 chars, not in stopWords)
        Set<String> rawTerms = Arrays.stream(lowerQuestion.split("[^a-zA-Z0-9]+"))
                .filter(term -> term.length() >= 3)
                .filter(term -> !stopWords.contains(term))
                .collect(Collectors.toSet());

        // Expand terms with simple English stemming
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

        // Domain synonyms for common healthcare questions
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

        // Find the maximum document ID among chunks to represent the most recently created/uploaded record
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
                        score += 4; // Title matches carry high relevance
                    }
                    if (lowerType.contains(term)) {
                        score += 2;
                    }
                }
            }

            // If user asked about "latest" and this chunk has matching relevance, grant recency bonus
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

        // Sort descending by score, tie-breaking by higher document ID (recency)
        scoredChunks.sort((a, b) -> {
            int scoreCompare = Integer.compare(b.score(), a.score());
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            long idA = a.documentId() != null ? a.documentId() : 0L;
            long idB = b.documentId() != null ? b.documentId() : 0L;
            return Long.compare(idB, idA);
        });

        // Return top chunks with positive matches
        return scoredChunks.stream()
                .filter(c -> c.score() > 0)
                .limit(4)
                .toList();
    }

    private String generateFallbackConsolidatedSummary(List<HealthDocument> documents) {
        StringBuilder sb = new StringBuilder();
        sb.append("CONSOLIDATED HEALTH SUMMARY (OFFLINE PREVIEW)\n\n");

        sb.append("Overall Health Overview:\n");
        sb.append("Consolidated review of ").append(documents.size())
                .append(" archived medical record(s) on file.\n\n");

        sb.append("Main Complaints or Conditions:\n");
        boolean foundConditions = false;
        for (HealthDocument doc : documents) {
            String lower = doc.getContent() != null ? doc.getContent().toLowerCase() : "";
            if (lower.contains("diagnos") || lower.contains("condition") || lower.contains("complaint") || lower.contains("history")) {
                sb.append("• From [").append(doc.getTitle()).append("]: ");
                String c = doc.getContent().trim();
                sb.append(c.length() > 140 ? c.substring(0, 140) + "..." : c).append("\n");
                foundConditions = true;
            }
        }
        if (!foundConditions) {
            sb.append("• Recorded document records: ");
            for (HealthDocument d : documents) {
                sb.append(d.getTitle()).append(" (").append(d.getDocumentType()).append("); ");
            }
            sb.append("\n");
        }
        sb.append("\n");

        sb.append("Medications & Prescriptions:\n");
        boolean foundMeds = false;
        for (HealthDocument doc : documents) {
            String lower = doc.getContent() != null ? doc.getContent().toLowerCase() : "";
            if (lower.contains("medicat") || lower.contains("prescri") || lower.contains("dose") || lower.contains("mg") || "Prescription".equalsIgnoreCase(doc.getDocumentType())) {
                sb.append("• [").append(doc.getTitle()).append("]: ");
                String c = doc.getContent().trim();
                sb.append(c.length() > 140 ? c.substring(0, 140) + "..." : c).append("\n");
                foundMeds = true;
            }
        }
        if (!foundMeds) {
            sb.append("• No specific active medications listed across current documents.\n");
        }
        sb.append("\n");

        sb.append("Important Test Results:\n");
        boolean foundTests = false;
        for (HealthDocument doc : documents) {
            String lower = doc.getContent() != null ? doc.getContent().toLowerCase() : "";
            if (lower.contains("test") || lower.contains("report") || lower.contains("result") || lower.contains("level") || "Blood Report".equalsIgnoreCase(doc.getDocumentType()) || "Diagnostic Report".equalsIgnoreCase(doc.getDocumentType())) {
                sb.append("• [").append(doc.getTitle()).append("]: ");
                String c = doc.getContent().trim();
                sb.append(c.length() > 140 ? c.substring(0, 140) + "..." : c).append("\n");
                foundTests = true;
            }
        }
        if (!foundTests) {
            sb.append("• No specific laboratory or diagnostic values recorded.\n");
        }
        sb.append("\n");

        sb.append("Follow-up Recommendations Mentioned in the Documents:\n");
        sb.append("• Consult primary healthcare provider for updated evaluation and diagnostic review.\n\n");

        sb.append("Missing or Unclear Information:\n");
        sb.append("• Full LLM clinical synthesis is temporarily waiting for Ollama service connection. Summary displayed from stored database records.\n\n");

        sb.append("Notice: This clinical summary is generated for personal health reference and does not replace professional medical diagnosis or consultation.");
        return sb.toString();
    }

    private RagResponse generateFallbackRagAnswer(String question, List<DocumentChunk> chunks, List<Map<String, Object>> sources) {
        if (sources.isEmpty() || chunks.isEmpty()) {
            return new RagResponse(
                    "The provided health documents do not contain information regarding: \"" + question + "\".",
                    List.of()
            );
        }

        String lowerQ = question.toLowerCase();

        // 1. Check for specific medication inquiries
        if (lowerQ.contains("medicat") || lowerQ.contains("prescription") || lowerQ.contains("drug")) {
            List<String> foundMedLines = new ArrayList<>();
            for (DocumentChunk chunk : chunks) {
                for (String line : chunk.text().split("\\r?\\n")) {
                    String lineLower = line.toLowerCase();
                    if (lineLower.contains("tablet") || lineLower.contains("capsule") || lineLower.contains("mg")
                            || lineLower.contains("prescribed") || lineLower.contains("take ") || lineLower.contains("dosage")) {
                        // Guard against test results that have mg/dL or numeric values
                        if (!lineLower.contains("mg/dl") && !lineLower.contains("reference range") && !lineLower.contains("test result")) {
                            foundMedLines.add(line.trim() + " (from " + chunk.documentTitle() + ")");
                        }
                    }
                }
            }

            if (!foundMedLines.isEmpty()) {
                String answer = "Offline Extracted Medications (AI answering is temporarily offline):\n"
                        + String.join("\n", foundMedLines)
                        + "\n\n(Note: Generated via offline keyword extraction because Ollama is temporarily unavailable).";
                return new RagResponse(answer, sources);
            } else {
                return new RagResponse(
                        "No medications were found in the available document excerpts. (Note: AI answering is temporarily offline).",
                        sources
                );
            }
        }

        // 2. Check for hemoglobin inquiry
        if (lowerQ.contains("hemoglobin") || lowerQ.contains("hb")) {
            for (DocumentChunk chunk : chunks) {
                for (String line : chunk.text().split("\\r?\\n")) {
                    if (line.toLowerCase().contains("hemoglobin") || line.toLowerCase().contains("hb ")) {
                        String answer = "Offline Extracted Result (AI answering is temporarily offline):\n"
                                + line.trim() + " (Source: " + chunk.documentTitle() + ")"
                                + "\n\n(Note: Generated via offline extraction because Ollama is temporarily unavailable).";
                        return new RagResponse(answer, sources);
                    }
                }
            }
        }

        // 3. For all other queries, return a clear, user-friendly offline message indicating AI answering is offline
        // instead of concatenating or dumping the raw document text
        DocumentChunk topChunk = chunks.get(0);
        String answer = "AI answering is temporarily unavailable because the Ollama service is offline.\n\n"
                + "Relevant information was located in: \"" + topChunk.documentTitle() + "\" (" + topChunk.documentType() + ").\n"
                + "Please refer to the cited source excerpts below for details, or ensure Ollama is running to get synthesized AI answers.";

        return new RagResponse(answer, sources);
    }
}
