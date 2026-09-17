package com.funchole.backend.controlplane.mapper;

import com.funchole.backend.controlplane.dto.ApiKeyResponse;
import com.funchole.backend.controlplane.entity.AppUserApiKey;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ApiKeyMapper {

    ApiKeyResponse toResponse(AppUserApiKey apiKey);
}
