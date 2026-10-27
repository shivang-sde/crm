package com.shivang.crm.modules.account.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * L3.2-P2: one paginated child collection inside a Customer 360 response.
 *
 * <p>Field names follow the repository's existing pagination meta convention
 * ({@code page}, {@code size}, {@code total}, {@code totalPages}) as used by every
 * list endpoint, so a consumer already reading list metadata needs no second shape.
 * Spring's {@code Page} is accepted as a construction input but is not part of the
 * wire contract.
 *
 * @param <T> the domain response type of the child collection
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A paginated child collection within a Customer 360 view")
public class Customer360Section<T> {

    @Schema(description = "Records on this page")
    private List<T> items;

    @Schema(description = "Zero-based page index that was returned")
    private int page;

    @Schema(description = "Page size applied after clamping to the supported maximum")
    private int size;

    @Schema(description = "Total records visible to the caller in this collection, "
            + "which may be less than the number that exist if their scope is narrower")
    private long total;

    @Schema(description = "Total pages available at this size")
    private int totalPages;

    @Schema(description = "True when further pages exist")
    private boolean hasNext;

    /**
     * Builds a section from a domain page.
     *
     * <p>{@code getNumber()} is echoed rather than forced to zero, so requesting a page
     * beyond the end returns an empty list with accurate metadata instead of silently
     * returning the first page.
     */
    public static <T> Customer360Section<T> of(Page<T> page) {
        return Customer360Section.<T>builder()
                .items(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .total(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.getNumber() + 1 < page.getTotalPages())
                .build();
    }
}
