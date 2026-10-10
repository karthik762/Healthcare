
package app.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import app.model.PatientIntakeSession;

public interface PatientIntakeRepository
        extends JpaRepository<PatientIntakeSession, Long> {

    List<PatientIntakeSession> findAllByUser_IdOrderByCreatedAtDesc(
            Long userId);

    Optional<PatientIntakeSession> findByIdAndUser_Id(
            Long id, Long userId);
}
