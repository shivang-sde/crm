package com.shivang.crm.modules.user.mapper;

import com.shivang.crm.modules.auth.entity.User;
import com.shivang.crm.modules.rbac.entity.UserRole;
import com.shivang.crm.modules.user.dto.response.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserManagementMapper {

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "roleId", source = "userRole.roleId")
    @Mapping(target = "roleName", source = "userRole.role.name")

    @Mapping(target = "tenantId", source = "user.tenant.id")
    @Mapping(target = "tenantName", source = "user.tenant.name")

    @Mapping(target = "resellerId", source = "user.tenant.reseller.id")
    @Mapping(target = "resellerName", expression = "java(getResellerName(user))")

    @Mapping(target = "createdAt", source = "user.createdAt")
    UserResponse toUserResponse(User user, UserRole userRole);

    default String getResellerName(User user) {
        if (user.getTenant() == null ||
                user.getTenant().getReseller() == null) {
            return null;
        }

        return user.getTenant().getReseller().getFirstName()
                + " "
                + user.getTenant().getReseller().getLastName();
    }
}