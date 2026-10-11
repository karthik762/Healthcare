
package app.services;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class AiSummaryService {

    private final RestClient restClient;

    public AiSummaryService(
            @Value("${app.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(ollamaBaseUrl)
                .build();
    }

    public String summarize(String documentText) {

        if (documentText == null || documentText.isBlank()) {
            throw new IllegalArgumentException(
                    "Document content cannot be empty."
            );
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

        Map<?, ?> response = restClient.post()
                .uri("/api/generate")
                .body(request)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("response") == null) {
            throw new IllegalStateException(
                    "Ollama returned an empty response."
            );
        }

        return response.get("response").toString().trim();
    }
}
