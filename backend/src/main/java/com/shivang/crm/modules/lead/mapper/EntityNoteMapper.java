package com.shivang.crm.modules.lead.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.shivang.crm.modules.lead.dto.EntityNoteResponse;
import com.shivang.crm.modules.lead.entity.EntityNote;

@Mapper(componentModel = "spring")
public interface EntityNoteMapper {


    EntityNoteResponse toResponse(EntityNote note);

    List<EntityNoteResponse> toResponseList(List<EntityNote> notes);
}
