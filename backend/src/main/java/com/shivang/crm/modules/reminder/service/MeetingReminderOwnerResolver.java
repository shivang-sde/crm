package com.shivang.crm.modules.reminder.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.reminder.entity.ReminderSourceType;
import com.shivang.crm.modules.meeting.repository.MeetingRepository;

@Component
public class MeetingReminderOwnerResolver implements ReminderOwnerResolver {

    private final MeetingRepository meetingRepository;

    public MeetingReminderOwnerResolver(MeetingRepository meetingRepository) {
        this.meetingRepository = meetingRepository;
    }

    @Override
    public ReminderSourceType supportedType() {
        return ReminderSourceType.MEETING;
    }

    @Override
    public Optional<UUID> resolveOwner(UUID tenantId, UUID sourceId) {
        return meetingRepository.findOwnerIdForReminder(sourceId, tenantId);
    }
}
