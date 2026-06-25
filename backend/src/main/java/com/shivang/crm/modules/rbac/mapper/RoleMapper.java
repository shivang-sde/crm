package com.shivang.crm.modules.rbac.mapper;

import com.shivang.crm.modules.rbac.entity.Role;
import com.shivang.crm.modules.rbac.entity.RolePermission;
import com.shivang.crm.modules.user.dto.response.PermissionResponse;
import com.shivang.crm.modules.user.dto.response.RoleResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface RoleMapper {

    @Mapping(target = "id", source = "role.id")
    @Mapping(target = "name", source = "role.name")
    @Mapping(target = "level", source = "role.level")
    @Mapping(target = "description", source = "role.description")
    @Mapping(target = "permissions", source = "rolePermissions")
    RoleResponse toRoleResponse(Role role, List<RolePermission> rolePermissions);

    @Mapping(target = "id", source = "rolePermission.permission.id")
    @Mapping(target = "module", source = "rolePermission.permission.module")
    @Mapping(target = "action", source = "rolePermission.permission.action")
    @Mapping(target = "accessScope", source = "rolePermission.accessScope")
    @Mapping(target = "description", source = "rolePermission.permission.description")
    PermissionResponse toPermissionResponse(RolePermission rolePermission);

}

