package com.shivang.crm.modules.commercial.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommercialDocumentResponse {
    private UUID id;
    private String externalType;
    private String externalId;
    private UUID accountId;
    private UUID contactId;
    private Instant externalUpdatedAt;
    private String pdfUrl;
}
