package com.shivang.crm.modules.lead.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LeadConvertResponse", description = "Result of lead conversion into account and contact")
public class LeadConvertResponse {

    @JsonProperty("leadId")
    private UUID leadId;

    @JsonProperty("accountId")
    private UUID accountId;

    @JsonProperty("contactId")
    private UUID contactId;

    @JsonProperty("dealId")
    private UUID dealId;
}
