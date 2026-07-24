package com.shivang.crm.modules.call.dto;

import com.shivang.crm.modules.call.dto.CallResponse;

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
public class ClickToCallResponse {
    private java.util.UUID callId;
    private String externalCallId;
    private String status;
    private String message;
    private CallResponse call;
}
