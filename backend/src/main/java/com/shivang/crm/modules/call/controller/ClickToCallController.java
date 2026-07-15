package com.shivang.crm.modules.call.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.call.dto.ClickToCallRequest;
import com.shivang.crm.modules.call.dto.ClickToCallResponse;
import com.shivang.crm.modules.call.service.ClickToCallService;
import com.shivang.crm.shared.dto.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/calls")
@RequiredArgsConstructor
public class ClickToCallController {

    private final ClickToCallService clickToCallService;

    @PostMapping("/click-to-call")
    public ResponseEntity<ApiResponse<ClickToCallResponse>> clickToCall(@RequestBody ClickToCallRequest request) {
        ClickToCallResponse resp = clickToCallService.clickToCall(request);
        return ResponseEntity.ok(ApiResponse.success(resp));
    }
}
