package com.shivang.crm.modules.activity.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.shivang.crm.modules.activity.dto.ActivityResponse;
import com.shivang.crm.modules.activity.entity.Activity;

@Mapper(componentModel = "spring")
public interface ActivityMapper {

    @Mapping(target = "entityType", source = "entityType")
    @Mapping(target = "entityId", source = "entityId")
    @Mapping(target = "activityType", source = "activityType")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "performedBy", source = "performedBy")
    @Mapping(target = "metadata", source = "metadata")
    @Mapping(target = "id", source = "id")
    @Mapping(target = "createdAt", source = "createdAt")
    ActivityResponse toResponse(Activity activity);

    List<ActivityResponse> toResponseList(List<Activity> activities);
}
