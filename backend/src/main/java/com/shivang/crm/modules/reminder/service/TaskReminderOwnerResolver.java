package com.shivang.crm.modules.reminder.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.reminder.entity.ReminderSourceType;
import com.shivang.crm.modules.task.repository.TaskRepository;

@Component
public class TaskReminderOwnerResolver implements ReminderOwnerResolver {

    private final TaskRepository taskRepository;

    public TaskReminderOwnerResolver(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public ReminderSourceType supportedType() {
        return ReminderSourceType.TASK;
    }

    @Override
    public Optional<UUID> resolveOwner(UUID tenantId, UUID sourceId) {
        return taskRepository.findOwnerIdForReminder(sourceId, tenantId);
    }
}
