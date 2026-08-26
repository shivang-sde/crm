package com.shivang.crm.modules.reminder.service;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.shivang.crm.modules.auth.entity.User;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.meeting.entity.Meeting;
import com.shivang.crm.modules.meeting.repository.MeetingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the CRM-user recipients of a meeting reminder from the free-form
 * attendee email strings stored on {@code Meeting.attendees}.
 *
 * Resolution rules (Phase MEET-3):
 * - tenant-scoped: an attendee email can only match users of the meeting's own tenant
 * - case-insensitive matching, values trimmed and lower-cased
 * - blank values ignored, duplicates collapsed
 * - unmatched (external) emails are silently ignored
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingAttendeeRecipientResolver {

    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;

    /**
     * One batched tenant-scoped lookup for all attendees of a meeting.
     * Never throws for unmatched emails; only genuine persistence failures propagate.
     */
    public Set<UUID> resolveRecipientUserIds(UUID tenantId, UUID meetingId) {
        Meeting meeting = meetingRepository
                .findByIdAndTenantIdAndDeletedFalse(meetingId, tenantId)
                .orElse(null);

        if (meeting == null || meeting.getAttendees() == null || meeting.getAttendees().isEmpty()) {
            return Set.of();
        }

        Set<String> lowerCasedEmails = meeting.getAttendees().stream()
                .filter(attendee -> attendee != null && !attendee.isBlank())
                .map(attendee -> attendee.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (lowerCasedEmails.isEmpty()) {
            return Set.of();
        }

        return userRepository
                .findMatchingUsersByTenantIdAndLowerEmailIn(tenantId, lowerCasedEmails)
                .stream()
                .map(User::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
