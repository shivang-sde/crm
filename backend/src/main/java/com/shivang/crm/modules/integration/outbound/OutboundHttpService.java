package com.shivang.crm.modules.integration.outbound;

public interface OutboundHttpService {

    OutboundHttpResult execute(OutboundHttpRequest request);
}