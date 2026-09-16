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
@Table(name = "flow_environment_attachments")
public class FlowEnvironmentAttachment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flow_id", nullable = false)
    private Flow flow;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "environment_profile_id", nullable = false)
    private EnvironmentProfile environmentProfile;

    @Column(nullable = false)
    private int priority;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public Flow getFlow() {
        return flow;
    }

    public EnvironmentProfile getEnvironmentProfile() {
        return environmentProfile;
    }

    public int getPriority() {
        return priority;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void updatePriority(int priority) {
        this.priority = priority;
    }

    public static FlowEnvironmentAttachment create(Flow flow, EnvironmentProfile profile, int priority) {
        FlowEnvironmentAttachment attachment = new FlowEnvironmentAttachment();
        attachment.id = UUID.randomUUID();
        attachment.flow = flow;
        attachment.environmentProfile = profile;
        attachment.priority = priority;
        attachment.createdAt = OffsetDateTime.now();
        return attachment;
    }
}
