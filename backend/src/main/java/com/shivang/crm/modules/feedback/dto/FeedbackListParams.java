package com.shivang.crm.modules.feedback.dto;

import java.util.UUID;

import com.shivang.crm.modules.feedback.entity.FeedbackType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * L3.3-P1: filters for the authenticated feedback list.
 *
 * <p>{@code accountId}, {@code ticketId} and {@code type} only. P1 deliberately
 * exposes no analytics, aggregate or NPS-scoring endpoint; those belong to L3.3-P5.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackListParams {

    /** Filter to one canonical customer. */
    private UUID accountId;

    /** Filter to feedback correlated with one support ticket. */
    private UUID ticketId;

    /** Filter to one signal type. */
    private FeedbackType type;

    /** 0-indexed. */
    private int page;

    /** Bounded to {@code MAX_PAGE_SIZE} by the service. */
    private int size;
}
