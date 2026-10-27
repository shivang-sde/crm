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
    // L3.1-P5: a system-generated activity has a null performedBy, which on its own is
    // indistinguishable from a missing value. These make the actor explicit.
    @Mapping(target = "actorType", source = "actorType")
    @Mapping(target = "actorSource", source = "actorSource")
    ActivityResponse toResponse(Activity activity);

    List<ActivityResponse> toResponseList(List<Activity> activities);
}
