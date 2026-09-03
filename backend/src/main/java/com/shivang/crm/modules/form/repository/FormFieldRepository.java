package com.shivang.crm.modules.form.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.form.entity.FormField;

@Repository
public interface FormFieldRepository extends JpaRepository<FormField, UUID> {

    List<FormField> findByFormIdAndDeletedFalseOrderByOrderIndexAsc(UUID formId);

    Optional<FormField> findByIdAndFormIdAndDeletedFalse(UUID id, UUID formId);

    Optional<FormField> findByFormIdAndFieldKeyAndDeletedFalse(UUID formId, String fieldKey);

    void deleteByFormId(UUID formId);
}
