package com.entry.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @EntityGraph(attributePaths = "entrance")
    Optional<User> findWithEntranceById(UUID id);

    Optional<User> findByIdAndEntrance_Id(UUID id, UUID entranceId);

    List<User> findByEntrance_IdAndStatusOrderByCreatedAtAsc(UUID entranceId, UserStatus status);

    List<User> findByEntrance_IdAndStatusOrderByFloorAscApartmentAscLastNameAsc(UUID entranceId, UserStatus status);

    long countByEntrance_IdAndStatus(UUID entranceId, UserStatus status);

    List<User> findByEntrance_IdAndStatusAndRole(UUID entranceId, UserStatus status, Role role);
}
