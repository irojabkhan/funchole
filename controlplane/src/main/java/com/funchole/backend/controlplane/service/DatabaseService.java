package com.funchole.backend.controlplane.service;

import com.funchole.backend.controlplane.dto.DatabaseCreateRequest;
import com.funchole.backend.controlplane.dto.DatabaseUpdateRequest;
import com.funchole.backend.controlplane.entity.AppUser;
import com.funchole.backend.controlplane.entity.Database;
import com.funchole.backend.controlplane.repository.DatabaseRepository;
import com.funchole.backend.core.base.exception.ResourceNotFoundException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseService {

    private final DatabaseRepository databaseRepository;
    private final FunctionSecretStore functionSecretStore;

    public DatabaseService(DatabaseRepository databaseRepository, FunctionSecretStore functionSecretStore) {
        this.databaseRepository = databaseRepository;
        this.functionSecretStore = functionSecretStore;
    }

    public Page<Database> listDatabases(UUID appUserId, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page - 1, 0),
                Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, Database::getCreatedAt)
        );
        return databaseRepository.findAllByAppUser_IdAndDeletedAtIsNull(appUserId, pageable);
    }

    public Database getDatabaseById(UUID appUserId, UUID databaseId) {
        return databaseRepository.findByIdAndAppUser_IdAndDeletedAtIsNull(databaseId, appUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Database not found: " + databaseId));
    }

    @Transactional
    public Database createDatabase(AppUser appUser, DatabaseCreateRequest request) {
        if (databaseRepository.existsByAppUser_IdAndNameAndDeletedAtIsNull(appUser.getId(), request.name())) {
            throw new IllegalArgumentException("Database name already in use: " + request.name());
        }

        UUID databaseId = UUID.randomUUID();
        String passwordSecretRef = functionSecretStore.saveForDatabase(databaseId, "password", request.password());

        Database database = Database.create(
                databaseId,
                appUser,
                request.name(),
                request.type().toUpperCase(Locale.ROOT),
                request.host(),
                request.port(),
                request.databaseName(),
                request.username(),
                passwordSecretRef,
                request.sslEnabled() == null || request.sslEnabled()
        );

        return databaseRepository.save(database);
    }

    @Transactional
    public Database updateDatabase(UUID appUserId, UUID databaseId, DatabaseUpdateRequest request) {
        Database database = getDatabaseById(appUserId, databaseId);

        database.update(
                request.name(),
                request.host(),
                request.port(),
                request.databaseName(),
                request.username(),
                request.sslEnabled() == null || request.sslEnabled()
        );

        if (request.password() != null && !request.password().isBlank()) {
            String passwordSecretRef = functionSecretStore.saveForDatabase(databaseId, "password", request.password());
            database.updatePasswordSecretRef(passwordSecretRef);
        }

        return databaseRepository.save(database);
    }

    @Transactional
    public void deleteDatabase(UUID appUserId, UUID databaseId) {
        Database database = getDatabaseById(appUserId, databaseId);
        database.softDelete();
        databaseRepository.save(database);
    }
}
