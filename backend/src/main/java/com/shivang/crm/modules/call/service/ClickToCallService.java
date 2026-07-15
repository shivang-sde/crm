package com.shivang.crm.modules.call.service;

import com.shivang.crm.modules.call.dto.ClickToCallRequest;
import com.shivang.crm.modules.call.dto.ClickToCallResponse;

public interface ClickToCallService {
    ClickToCallResponse clickToCall(ClickToCallRequest request);
}
