package com.entry.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Push preferences. A missing row means "everything on". In-app notifications are always kept. */
@Entity
@Table(name = "notification_settings")
@Getter
@Setter
@NoArgsConstructor
public class NotificationSettings {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled = true;

    @Column(name = "new_messages", nullable = false)
    private boolean newMessages = true;

    @Column(name = "new_polls", nullable = false)
    private boolean newPolls = true;

    @Column(name = "new_posts", nullable = false)
    private boolean newPosts = true;

    public NotificationSettings(UUID userId) {
        this.userId = userId;
    }
}
