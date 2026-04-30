package com.project.wastepilot.mappers;

import com.project.wastepilot.domain.dto.material.MaterialResponse;
import com.project.wastepilot.domain.entity.MaterialEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MaterialMapper {
    MaterialResponse toResponse(MaterialEntity entity);
}
