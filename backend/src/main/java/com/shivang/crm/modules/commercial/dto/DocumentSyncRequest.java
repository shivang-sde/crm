package com.shivang.crm.modules.commercial.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSyncRequest {

    @NotBlank(message = "externalType is required")
    private String externalType; // QUOTATION or INVOICE

    @NotBlank(message = "externalId is required")
    private String externalId;

    private Instant externalUpdatedAt;

    @NotNull(message = "customer is required")
    private CustomerRef customer;

    private String documentNumber;
    private LocalDate documentDate;
    private String currency;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal total;
    private String status;

    private String pdfUrl;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerRef {
        @NotBlank(message = "externalCustomerId is required")
        private String externalCustomerId;
    }
}
