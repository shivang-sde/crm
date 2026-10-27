package com.shivang.crm.modules.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.shivang.crm.modules.auth.entity.User;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.deal.dto.SalesDashboardResponse;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.entity.DealStage;
import com.shivang.crm.modules.deal.entity.RecordCategory;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.deal.repository.DealStageRepository;
import com.shivang.crm.modules.lead.repository.LeadRepository;
import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

class SalesDashboardServiceTest {

    private DealRepository dealRepository;
    private LeadRepository leadRepository;
    private UserRepository userRepository;
    private SalesDashboardService service;

    private UUID tenantId;
    private UUID currentUserId;
    private UUID ownerId;
    private Deal deal;

    @BeforeEach
    void setup() {
        dealRepository = mock(DealRepository.class);
        leadRepository = mock(LeadRepository.class);
        userRepository = mock(UserRepository.class);
        PermissionEvaluatorService permissions = mock(PermissionEvaluatorService.class);
        DealStageRepository stages = mock(DealStageRepository.class);

        tenantId = UUID.randomUUID();
        currentUserId = UUID.randomUUID();
        ownerId = UUID.randomUUID();

        when(permissions.getAccessScope(currentUserId, tenantId, "deal", "read")).thenReturn("ALL");
        when(permissions.getAccessScope(currentUserId, tenantId, "lead", "read")).thenReturn("ALL");
        when(stages.findByTenantIdOrderByDisplayOrder(tenantId)).thenReturn(List.of());
        when(leadRepository.count(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(0L);

        DealStage stage = new DealStage();
        stage.setId(UUID.randomUUID());
        stage.setTenantId(tenantId);
        stage.setName("Open");
        stage.setRecordCategory(RecordCategory.OPEN);

        deal = new Deal();
        deal.setId(UUID.randomUUID());
        deal.setTenantId(tenantId);
        deal.setName("Deal");
        deal.setOwnerId(ownerId);
        deal.setStage(stage);
        deal.setCreatedAt(Instant.now());
        deal.setStageEnteredAt(Instant.now());
        deal.setAmount(java.math.BigDecimal.TEN);

        when(dealRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(deal));

        service = new SalesDashboardService(dealRepository, stages, leadRepository,
                userRepository, permissions);
    }

    @Test
    void ownerNamesAreResolvedInsideTheDashboardTenantOnly() {
        User owner = new User();
        owner.setId(ownerId);
        owner.setTenantId(tenantId);
        owner.setFirstName("Local");
        owner.setLastName("Owner");
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(ownerId, tenantId))
                .thenReturn(Optional.of(owner));

        SalesDashboardResponse response = service.getSalesDashboard(tenantId, currentUserId);

        assertThat(response.getOwners()).hasSize(1);
        assertThat(response.getOwners().get(0).getOwnerName()).isEqualTo("Local Owner");
        verify(userRepository).findByIdAndTenantIdAndDeletedFalse(ownerId, tenantId);
        verify(userRepository, never()).findById(any());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void dashboardReadsExcludeSoftDeletedRows() {
        when(userRepository.findByIdAndTenantIdAndDeletedFalse(ownerId, tenantId))
                .thenReturn(Optional.empty());

        service.getSalesDashboard(tenantId, currentUserId);

        ArgumentCaptor<org.springframework.data.jpa.domain.Specification<Deal>> captor =
                ArgumentCaptor.forClass(org.springframework.data.jpa.domain.Specification.class);
        verify(dealRepository).findAll(captor.capture());

        Root<Deal> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> tenantPath = mock(Path.class);
        Path<Object> deletedPath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        when(root.get("tenantId")).thenReturn((Path) tenantPath);
        when(root.get("deleted")).thenReturn((Path) deletedPath);
        when(cb.equal(any(jakarta.persistence.criteria.Expression.class), any(Object.class)))
                .thenReturn(predicate);
        when(cb.isFalse(any(jakarta.persistence.criteria.Expression.class))).thenReturn(predicate);
        when(cb.conjunction()).thenReturn(predicate);
        when(cb.and(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);

        captor.getValue().toPredicate(root, query, cb);

        verify(root).get("deleted");
    }
}
