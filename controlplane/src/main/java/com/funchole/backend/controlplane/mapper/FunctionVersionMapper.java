package com.funchole.backend.controlplane.mapper;

import com.funchole.backend.controlplane.dto.FunctionVersionResponse;
import com.funchole.backend.controlplane.entity.FunctionVersion;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FunctionVersionMapper {

    @Mapping(target = "functionId", source = "function.id")
    FunctionVersionResponse toResponse(FunctionVersion functionVersion);
}
