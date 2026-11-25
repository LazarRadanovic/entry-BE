package com.entry.lostfound;

import com.entry.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A lost or found item. Responses live in {@code lost_found_response}. */
@Entity
@Table(name = "lost_found_item")
@Getter
@Setter
@NoArgsConstructor
public class LostFoundItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entrance_id", nullable = false)
    private UUID entranceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LostFoundKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LostFoundCategory category;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 1000)
    private String description;

    /** Where it was found / lost, e.g. "Hallway, 3rd floor". */
    @Column(length = 100)
    private String location;

    @Column(name = "photo_file_id")
    private UUID photoFileId;

    /** Null = still open. */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
