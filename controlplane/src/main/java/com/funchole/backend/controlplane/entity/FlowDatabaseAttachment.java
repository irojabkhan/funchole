package com.funchole.backend.controlplane.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "flow_database_attachments")
public class FlowDatabaseAttachment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flow_id", nullable = false)
    private Flow flow;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "database_id", nullable = false)
    private Database database;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public Flow getFlow() {
        return flow;
    }

    public Database getDatabase() {
        return database;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public static FlowDatabaseAttachment create(Flow flow, Database database) {
        FlowDatabaseAttachment attachment = new FlowDatabaseAttachment();
        attachment.id = UUID.randomUUID();
        attachment.flow = flow;
        attachment.database = database;
        attachment.createdAt = OffsetDateTime.now();
        return attachment;
    }
}
