package com.shivang.crm.modules.lead.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LeadConvertRequest", description = "Request to convert a lead into an existing or new account/contact")
public class LeadConvertRequest {

    @Schema(description = "Existing account UUID to use when converting the lead")
    private UUID accountId;

    @Schema(description = "Existing contact UUID to use when converting the lead")
    private UUID contactId;
}
