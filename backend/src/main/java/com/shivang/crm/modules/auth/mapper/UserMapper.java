package com.shivang.crm.modules.auth.mapper;

import com.shivang.crm.modules.auth.dto.response.UserInfo;
import com.shivang.crm.modules.auth.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    UserInfo toUserInfo(User user);
}
