package com.funchole.backend.controlplane.mapper;

import com.funchole.backend.controlplane.dto.DatabaseResponse;
import com.funchole.backend.controlplane.entity.Database;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DatabaseMapper {

    DatabaseResponse toResponse(Database database);
}
