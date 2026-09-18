package com.shivang.crm.modules.commercial.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSyncResponse {
    private boolean success;
    private UUID accountId;
    private UUID contactId;
    private boolean created;
    private String pdfUrl;
}
