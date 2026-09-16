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
@Table(name = "function_version_database_attachments")
public class FunctionVersionDatabaseAttachment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "function_version_id", nullable = false)
    private FunctionVersion functionVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "database_id", nullable = false)
    private Database database;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public FunctionVersion getFunctionVersion() {
        return functionVersion;
    }

    public Database getDatabase() {
        return database;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public static FunctionVersionDatabaseAttachment create(FunctionVersion functionVersion, Database database) {
        FunctionVersionDatabaseAttachment attachment = new FunctionVersionDatabaseAttachment();
        attachment.id = UUID.randomUUID();
        attachment.functionVersion = functionVersion;
        attachment.database = database;
        attachment.createdAt = OffsetDateTime.now();
        return attachment;
    }
}
