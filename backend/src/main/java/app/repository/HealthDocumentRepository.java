
package app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import app.model.HealthDocument;

public interface HealthDocumentRepository
        extends JpaRepository<HealthDocument, Long> {

    // Find all documents belonging to one user
    List<HealthDocument> findAllByUser_IdOrderByCreatedAtDesc(Long userId);

    // Find a particular document only if it belongs to that user
    Optional<HealthDocument> findByIdAndUser_Id(Long id, Long userId);
}
