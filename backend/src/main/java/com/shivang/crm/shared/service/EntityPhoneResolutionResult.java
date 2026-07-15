package com.shivang.crm.shared.service;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityPhoneResolutionResult {
    private boolean found;
    private String phone;
    private String resolvedEntityType;
    private UUID resolvedEntityId;
}
