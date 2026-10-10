
package app.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import app.model.HealthDocument;
import app.model.User;
import app.repository.HealthDocumentRepository;
import app.repository.UserRepository;

@Service
public class HealthDocumentService {

    private final HealthDocumentRepository repository;
    private final UserRepository userRepository;

    public HealthDocumentService(
            HealthDocumentRepository repository,
            UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    // Get the currently authenticated user
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

    // Get only the current user's health documents
    public List<HealthDocument> getAllDocuments() {
        User user = getCurrentUser();

        return repository
                .findAllByUser_IdOrderByCreatedAtDesc(user.getId());
    }

    // Get a document only if it belongs to the current user
    public Optional<HealthDocument> getDocumentById(Long id) {
        User user = getCurrentUser();

        return repository.findByIdAndUser_Id(id, user.getId());
    }

    // Create a document and assign it to the current user
    public HealthDocument createDocument(HealthDocument document) {
        User user = getCurrentUser();

        LocalDateTime now = LocalDateTime.now();

        // Do not trust an ID or owner supplied by the frontend
        document.setId(null);
        document.setUser(user);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);

        return repository.save(document);
    }

    // Update only the current user's document
    public Optional<HealthDocument> updateDocument(
            Long id,
            HealthDocument updatedDocument) {

        User user = getCurrentUser();

        return repository
                .findByIdAndUser_Id(id, user.getId())
                .map(document -> {
                    document.setTitle(updatedDocument.getTitle());
                    document.setDocumentType(
                            updatedDocument.getDocumentType());
                    document.setContent(updatedDocument.getContent());
                    document.setUpdatedAt(LocalDateTime.now());

                    // Keep the existing owner unchanged
                    return repository.save(document);
                });
    }

    // Delete only the current user's document
    public boolean deleteDocument(Long id) {
        User user = getCurrentUser();

        Optional<HealthDocument> document =
                repository.findByIdAndUser_Id(id, user.getId());

        if (document.isEmpty()) {
            return false;
        }

        repository.delete(document.get());
        return true;
    }
}
