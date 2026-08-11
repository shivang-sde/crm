package com.shivang.crm.modules.demo.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.account.dto.AccountCreateRequest;
import com.shivang.crm.modules.account.dto.AccountResponse;
import com.shivang.crm.modules.account.service.AccountService;
import com.shivang.crm.modules.auth.entity.User;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.call.dto.CallCreateRequest;
import com.shivang.crm.modules.call.entity.Call.CallStatus;
import com.shivang.crm.modules.call.entity.Call.CallType;
import com.shivang.crm.modules.call.service.CallService;
import com.shivang.crm.modules.catalog.dto.OfferingCreateRequest;
import com.shivang.crm.modules.catalog.dto.OfferingResponse;
import com.shivang.crm.modules.catalog.enums.BillingInterval;
import com.shivang.crm.modules.catalog.enums.BillingType;
import com.shivang.crm.modules.catalog.enums.OfferingType;
import com.shivang.crm.modules.catalog.service.OfferingService;
import com.shivang.crm.modules.contact.dto.ContactCreateRequest;
import com.shivang.crm.modules.contact.dto.ContactResponse;
import com.shivang.crm.modules.contact.service.ContactService;
import com.shivang.crm.modules.deal.dto.DealCreateRequest;
import com.shivang.crm.modules.deal.dto.DealLineItemCreateRequest;
import com.shivang.crm.modules.deal.dto.DealResponse;
import com.shivang.crm.modules.deal.dto.DealStageCreateRequest;
import com.shivang.crm.modules.deal.dto.DealStageResponse;
import com.shivang.crm.modules.deal.entity.ForecastCategory;
import com.shivang.crm.modules.deal.entity.RecordCategory;
import com.shivang.crm.modules.deal.service.DealLineItemService;
import com.shivang.crm.modules.deal.service.DealService;
import com.shivang.crm.modules.deal.service.DealStageService;
import com.shivang.crm.modules.demo.dto.DemoDataStatusResponse;
import com.shivang.crm.modules.demo.dto.DemoInstallationResponse;
import com.shivang.crm.modules.demo.entity.DemoDataRecord;
import com.shivang.crm.modules.demo.entity.TenantDemoInstallation;
import com.shivang.crm.modules.demo.repository.DemoDataRecordRepository;
import com.shivang.crm.modules.demo.repository.TenantDemoInstallationRepository;
import com.shivang.crm.modules.lead.dto.LeadCreateRequest;
import com.shivang.crm.modules.lead.dto.LeadResponse;
import com.shivang.crm.modules.lead.dto.LeadSourceCreateRequest;
import com.shivang.crm.modules.lead.dto.LeadSourceResponse;
import com.shivang.crm.modules.lead.dto.LeadStatusCreateRequest;
import com.shivang.crm.modules.lead.dto.LeadStatusResponse;
import com.shivang.crm.modules.lead.service.LeadService;
import com.shivang.crm.modules.lead.service.LeadSourceService;
import com.shivang.crm.modules.lead.service.LeadStatusService;
import com.shivang.crm.modules.meeting.dto.MeetingCreateRequest;
import com.shivang.crm.modules.meeting.entity.Meeting.MeetingType;
import com.shivang.crm.modules.meeting.service.MeetingService;
import com.shivang.crm.modules.task.dto.TaskCreateRequest;
import com.shivang.crm.modules.task.service.TaskService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DemoDataService {

    private final TenantDemoInstallationRepository installationRepository;
    private final DemoDataRecordRepository recordRepository;
    private final UserRepository userRepository;
    
    private final LeadStatusService leadStatusService;
    private final LeadSourceService leadSourceService;
    private final LeadService leadService;
    
    private final AccountService accountService;
    private final ContactService contactService;
    
    private final DealStageService dealStageService;
    private final DealService dealService;
    private final DealLineItemService dealLineItemService;
    
    private final OfferingService offeringService;
    
    private final TaskService taskService;
    private final CallService callService;
    private final MeetingService meetingService;

    private static final String TEMPLATE_KEY = "generic-sales";
    private static final Integer TEMPLATE_VERSION = 1;

    private record DemoReferenceData(
            Map<String, UUID> leadStatuses,
            Map<String, UUID> leadSources,
            Map<String, UUID> dealStages
    ) {}

    public DemoDataStatusResponse getDemoStatus(UUID tenantId) {
        log.info("Checking demo data status for tenant {}", tenantId);
        Optional<TenantDemoInstallation> existingOpt =
                installationRepository.findByTenantIdAndTemplateKeyAndTemplateVersion(tenantId, TEMPLATE_KEY, TEMPLATE_VERSION);

        if (existingOpt.isPresent()) {
            TenantDemoInstallation inst = existingOpt.get();
            Map<String, Integer> counts = new HashMap<>();
            if (inst.getSummary() != null) {
                for (Map.Entry<String, Object> entry : inst.getSummary().entrySet()) {
                    counts.put(entry.getKey(), (Integer) entry.getValue());
                }
            }
            return DemoDataStatusResponse.builder()
                    .templateKey(TEMPLATE_KEY)
                    .templateVersion(TEMPLATE_VERSION)
                    .installed(true)
                    .installedAt(inst.getInstalledAt())
                    .installedBy(inst.getInstalledBy())
                    .counts(counts)
                    .build();
        }

        return DemoDataStatusResponse.builder()
                .templateKey(TEMPLATE_KEY)
                .templateVersion(TEMPLATE_VERSION)
                .installed(false)
                .counts(new HashMap<>())
                .build();
    }

    @Transactional
    public DemoInstallationResponse installGenericSalesDemo(UUID tenantId, UUID adminUserId) {
        log.info("Installing demo data for tenant {}", tenantId);

        // Idempotency check
        Optional<TenantDemoInstallation> existing = installationRepository
                .findByTenantIdAndTemplateKeyAndTemplateVersion(tenantId, TEMPLATE_KEY, TEMPLATE_VERSION);

        if (existing.isPresent()) {
            TenantDemoInstallation inst = existing.get();
            Map<String, Integer> counts = new HashMap<>();
            if (inst.getSummary() != null) {
                for (Map.Entry<String, Object> entry : inst.getSummary().entrySet()) {
                    counts.put(entry.getKey(), (Integer) entry.getValue());
                }
            }
            return DemoInstallationResponse.builder()
                    .templateKey(TEMPLATE_KEY)
                    .templateVersion(TEMPLATE_VERSION)
                    .alreadyInstalled(true)
                    .installedAt(inst.getInstalledAt())
                    .counts(counts)
                    .build();
        }

        List<UUID> activeUserIds = userRepository.findAll().stream()
                .filter(u -> tenantId.equals(u.getTenantId()) && Boolean.TRUE.equals(u.getIsActive()))
                .map(User::getId)
                .collect(Collectors.toList());
        if (activeUserIds.isEmpty()) {
            activeUserIds.add(adminUserId);
        }

        Map<String, Integer> counts = new HashMap<>();
        List<DemoDataRecord> records = new ArrayList<>();

        // Phase A: Reference Data
        DemoReferenceData referenceData = ensureDemoReferenceData(tenantId, adminUserId, records, counts);

        // Phase B: Business Data
        generateDemoBusinessData(tenantId, adminUserId, activeUserIds, referenceData, records, counts);

        // Validate
        validateInstallation(counts);

        // Save records
        recordRepository.saveAll(records);

        // Save installation
        TenantDemoInstallation installation = TenantDemoInstallation.builder()
                .tenantId(tenantId)
                .templateKey(TEMPLATE_KEY)
                .templateVersion(TEMPLATE_VERSION)
                .installedBy(adminUserId)
                .summary(new HashMap<>(counts))
                .build();
        installationRepository.save(installation);

        return DemoInstallationResponse.builder()
                .templateKey(TEMPLATE_KEY)
                .templateVersion(TEMPLATE_VERSION)
                .alreadyInstalled(false)
                .installedAt(installation.getInstalledAt())
                .counts(counts)
                .build();
    }

    private DemoReferenceData ensureDemoReferenceData(UUID tenantId, UUID adminUserId, List<DemoDataRecord> records, Map<String, Integer> counts) {
        Map<String, UUID> leadStatuses = ensureLeadStatuses(tenantId, records, counts);
        Map<String, UUID> leadSources = ensureLeadSources(tenantId, records, counts);
        Map<String, UUID> dealStages = ensureDealStages(tenantId, adminUserId, records, counts);
        
        return new DemoReferenceData(leadStatuses, leadSources, dealStages);
    }
    
    private Map<String, UUID> ensureLeadStatuses(UUID tenantId, List<DemoDataRecord> records, Map<String, Integer> counts) {
        List<LeadStatusResponse> existing = leadStatusService.getStatusesByTenant(tenantId);
        Map<String, UUID> statusMap = new HashMap<>();

        String[][] requiredStatuses = {
            {"New", "true", "false", "10", "#00BCD4"},
            {"Contacted", "false", "false", "20", "#9C27B0"},
            {"Qualified", "false", "false", "30", "#4CAF50"},
            {"Nurturing", "false", "false", "40", "#FFC107"},
            {"Disqualified", "false", "true", "50", "#455A64"}
        };

        for (String[] req : requiredStatuses) {
            String name = req[0];
            Optional<LeadStatusResponse> match = existing.stream()
                .filter(s -> s.getName().equalsIgnoreCase(name))
                .findFirst();

            if (match.isPresent()) {
                statusMap.put(name, match.get().getId());
                counts.put("leadStatuses", counts.getOrDefault("leadStatuses", 0) + 1);
            } else {
                LeadStatusCreateRequest createReq = LeadStatusCreateRequest.builder()
                    .name(name)
                    .isDefault(Boolean.parseBoolean(req[1]))
                    .isClosed(Boolean.parseBoolean(req[2]))
                    .displayOrder(Integer.parseInt(req[3]))
                    .color((String) req[4])
                    .build();
                LeadStatusResponse created = leadStatusService.createStatus(tenantId, createReq);
                statusMap.put(name, created.getId());
                trackRecord(tenantId, "LEAD_STATUS", created.getId(), records, counts);
            }
        }
        return statusMap;
    }

    private Map<String, UUID> ensureLeadSources(UUID tenantId, List<DemoDataRecord> records, Map<String, Integer> counts) {
        List<LeadSourceResponse> existing = leadSourceService.getActiveSources(tenantId);
        Map<String, UUID> sourceMap = new HashMap<>();

        String[] requiredSources = {"Website", "Referral", "LinkedIn", "Cold Call", "Email Campaign", "Partner", "Trade Show"};

        for (String name : requiredSources) {
            Optional<LeadSourceResponse> match = existing.stream()
                .filter(s -> s.getName().equalsIgnoreCase(name))
                .findFirst();

            if (match.isPresent()) {
                sourceMap.put(name, match.get().getId());
                counts.put("leadSources", counts.getOrDefault("leadSources", 0) + 1);
            } else {
                LeadSourceCreateRequest createReq = LeadSourceCreateRequest.builder()
                    .name(name)
                    .isActive(true)
                    .build();
                LeadSourceResponse created = leadSourceService.createSource(tenantId, createReq);
                sourceMap.put(name, created.getId());
                trackRecord(tenantId, "LEAD_SOURCE", created.getId(), records, counts);
            }
        }
        return sourceMap;
    }

    private Map<String, UUID> ensureDealStages(UUID tenantId, UUID adminUserId, List<DemoDataRecord> records, Map<String, Integer> counts) {
        List<DealStageResponse> existing = dealStageService.listDealStages(tenantId);
        Map<String, UUID> stageMap = new HashMap<>();

        Object[][] requiredStages = {
            {"Qualification", RecordCategory.OPEN, 10, ForecastCategory.PIPELINE, true, false, "#B0BEC5"},
            {"Discovery", RecordCategory.OPEN, 25, ForecastCategory.PIPELINE, false, false, "#03A9F4"},
            {"Proposal", RecordCategory.OPEN, 50, ForecastCategory.BEST_CASE, false, false, "#FFC107"},
            {"Negotiation", RecordCategory.OPEN, 75, ForecastCategory.COMMIT, false, false, "#FF9800"},
            {"Closed Won", RecordCategory.CLOSED_WON, 100, ForecastCategory.CLOSED, false, true, "#4CAF50"},
            {"Closed Lost", RecordCategory.CLOSED_LOST, 0, ForecastCategory.OMITTED, false, true, "#E53935"}
        };

        for (Object[] req : requiredStages) {
            String name = (String) req[0];
            RecordCategory category = (RecordCategory) req[1];
            
            Optional<DealStageResponse> match = existing.stream()
                .filter(s -> s.getName().equalsIgnoreCase(name) || s.getRecordCategory() == category)
                .findFirst();

            if (match.isPresent()) {
                stageMap.put(name, match.get().getId());
                counts.put("dealStages", counts.getOrDefault("dealStages", 0) + 1);
            } else {
                DealStageCreateRequest createReq = DealStageCreateRequest.builder()
                    .name(name)
                    .recordCategory(category)
                    .defaultProbability((Integer) req[2])
                    .defaultForecastCategory((ForecastCategory) req[3])
                    .isDefault((Boolean) req[4])
                    .isClosed((Boolean) req[5])
                    .color((String) req[6])
                    .displayOrder(stageMap.size() * 10)
                    .build();
                DealStageResponse created = dealStageService.createDealStage(tenantId, adminUserId, createReq);
                stageMap.put(name, created.getId());
                trackRecord(tenantId, "DEAL_STAGE", created.getId(), records, counts);
            }
        }
        return stageMap;
    }
    
    private void generateDemoBusinessData(UUID tenantId, UUID adminUserId, List<UUID> userIds, DemoReferenceData ref, List<DemoDataRecord> records, Map<String, Integer> counts) {
        List<LeadResponse> leads = generateLeads(tenantId, adminUserId, userIds, ref, records, counts);
        List<AccountResponse> accounts = generateAccountsAndContacts(tenantId, adminUserId, userIds, records, counts);
        List<OfferingResponse> offerings = generateOfferings(tenantId, adminUserId, records, counts);
        generateDeals(tenantId, adminUserId, userIds, accounts, offerings, ref, records, counts);
    }

    private List<LeadResponse> generateLeads(UUID tenantId, UUID adminUserId, List<UUID> userIds, DemoReferenceData ref, List<DemoDataRecord> records, Map<String, Integer> counts) {
        List<LeadResponse> leadsList = new ArrayList<>();
        
        String[][] leadData = {
            {"Alice", "Johnson", "alice.j@software.com", "Software Co", "New", "Website"},
            {"Bob", "Smith", "bsmith@logistics.net", "Fast Logistics", "Contacted", "LinkedIn"},
            {"Charlie", "Brown", "charlie@manufacturing.org", "Brown Mfg", "Qualified", "Referral"},
            {"Diana", "Prince", "diana.p@healthcare.com", "First Health", "Nurturing", "Email Campaign"},
            {"Evan", "Wright", "ewright@education.edu", "Wright Education", "New", "Trade Show"},
            {"Fiona", "Gallagher", "fiona@retail.co", "Retail Plus", "Contacted", "Cold Call"},
            {"George", "Miller", "gmiller@consulting.com", "Miller Consulting", "Qualified", "Website"},
            {"Hannah", "Abbott", "hannah@software.com", "Tech Solutions", "Disqualified", "Partner"},
            {"Ian", "Malcolm", "ian@logistics.net", "Global Transit", "Nurturing", "Referral"},
            {"Julia", "Roberts", "julia@manufacturing.org", "BuildIt Inc", "New", "LinkedIn"}
        };

        for (String[] data : leadData) {
            UUID statusId = ref.leadStatuses().get(data[4]);
            UUID sourceId = ref.leadSources().get(data[5]);
            
            LeadCreateRequest req = LeadCreateRequest.builder()
                .firstName(data[0])
                .lastName(data[1])
                .email(data[2])
                .company(data[3])
                .statusId(statusId)
                .sourceId(sourceId)
                .ownerUserId(randomUser(userIds))
                .build();
            
            LeadResponse lead = leadService.createLead(tenantId, adminUserId, req);
            leadsList.add(lead);
            trackRecord(tenantId, "LEAD", lead.getId(), records, counts);
            
            // Add a Task linked to this Lead
            TaskCreateRequest taskReq = TaskCreateRequest.builder()
                .subject("Follow up with " + data[0])
                .dueDate(Instant.now().plus(2, ChronoUnit.DAYS))
                .entityType("LEAD")
                .entityId(lead.getId())
                .ownerUserId(req.getOwnerUserId())
                .build();
            var task = taskService.createTask(tenantId, adminUserId, taskReq);
            trackRecord(tenantId, "TASK", task.getId(), records, counts);
            
            // Add a Call linked to this Lead
            CallCreateRequest callReq = CallCreateRequest.builder()
                .subject("Introductory call")
                .callType(CallType.OUTGOING)
                .status(CallStatus.HELD)
                .entityType("LEAD")
                .entityId(lead.getId())
                .startTime(Instant.now().minus(2, ChronoUnit.DAYS))
                .endTime(Instant.now().minus(2, ChronoUnit.DAYS).plus(15, ChronoUnit.MINUTES))
                .assignedTo(req.getOwnerUserId())
                .build();
            var call = callService.createCall(tenantId, adminUserId, callReq);
            trackRecord(tenantId, "CALL", call.getId(), records, counts);
        }
        return leadsList;
    }

    private List<AccountResponse> generateAccountsAndContacts(UUID tenantId, UUID adminUserId, List<UUID> userIds, List<DemoDataRecord> records, Map<String, Integer> counts) {
        List<AccountResponse> createdAccounts = new ArrayList<>();
        String[][] accountData = {
            {"Acme Technologies", "Software"},
            {"Northstar Logistics", "Logistics"},
            {"BluePeak Consulting", "Professional Services"},
            {"GreenLeaf Healthcare", "Healthcare"},
            {"Vertex Manufacturing", "Manufacturing"},
            {"BrightPath Education", "Education"}
        };
        
        String[] firstNames = {"Oliver", "Emma", "Liam", "Olivia", "Noah", "Ava", "William", "Sophia", "James", "Isabella", "Benjamin", "Mia"};
        String[] lastNames = {"Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Rodriguez", "Martinez", "Hernandez", "Lopez"};
        int contactIdx = 0;

        for (String[] ad : accountData) {
            AccountCreateRequest accReq = AccountCreateRequest.builder()
                .name(ad[0])
                .industry(ad[1])
                .ownerUserId(randomUser(userIds))
                .build();
            AccountResponse account = accountService.createAccount(tenantId, adminUserId, accReq);
            createdAccounts.add(account);
            trackRecord(tenantId, "ACCOUNT", account.getId(), records, counts);

            // 2 contacts per account
            for (int i = 1; i <= 2; i++) {
                String fName = firstNames[contactIdx % firstNames.length];
                String lName = lastNames[contactIdx % lastNames.length];
                contactIdx++;
                
                ContactCreateRequest conReq = ContactCreateRequest.builder()
                    .firstName(fName)
                    .lastName(lName)
                    .email(fName.toLowerCase() + "." + lName.toLowerCase() + "@" + ad[0].split(" ")[0].toLowerCase() + ".com")
                    .accountId(account.getId())
                    .build();
                ContactResponse contact = contactService.createContact(tenantId, adminUserId, conReq);
                trackRecord(tenantId, "CONTACT", contact.getId(), records, counts);
            }
        }
        return createdAccounts;
    }

    private List<OfferingResponse> generateOfferings(UUID tenantId, UUID adminUserId, List<DemoDataRecord> records, Map<String, Integer> counts) {
        List<OfferingResponse> createdOfferings = new ArrayList<>();
        
        Object[][] offeringData = {
            {"CRM Professional License", "LIC-PRO", new BigDecimal("1000.00"), OfferingType.LICENSE, BillingType.RECURRING, BillingInterval.MONTHLY, true, 30},
            {"CRM Enterprise License", "LIC-ENT", new BigDecimal("10000.00"), OfferingType.LICENSE, BillingType.RECURRING, BillingInterval.YEARLY, true, 365},
            {"Implementation Service", "SVC-IMP", new BigDecimal("5000.00"), OfferingType.SERVICE, BillingType.ONE_TIME, null, false, null},
            {"Annual Support Plan", "SUB-SUP", new BigDecimal("2500.00"), OfferingType.SUBSCRIPTION, BillingType.RECURRING, BillingInterval.YEARLY, true, 365},
            {"Premium Maintenance", "MNT-PRM", new BigDecimal("1500.00"), OfferingType.MAINTENANCE, BillingType.RECURRING, BillingInterval.YEARLY, true, 365},
            {"Onboarding Package", "SVC-ONB", new BigDecimal("3000.00"), OfferingType.SERVICE, BillingType.ONE_TIME, null, false, null}
        };

        for (Object[] od : offeringData) {
            OfferingCreateRequest offReq = OfferingCreateRequest.builder()
                .name((String) od[0])
                .code((String) od[1])
                .defaultPrice((BigDecimal) od[2])
                .offeringType((OfferingType) od[3])
                .billingType((BillingType) od[4])
                .billingInterval((BillingInterval) od[5])
                .renewable((Boolean) od[6])
                .defaultTermDays((Integer) od[7])
                .active(true)
                .build();
            
            OfferingResponse off = offeringService.createOffering(tenantId, adminUserId, offReq);
            createdOfferings.add(off);
            trackRecord(tenantId, "OFFERING", off.getId(), records, counts);
        }
        return createdOfferings;
    }

    private void generateDeals(UUID tenantId, UUID adminUserId, List<UUID> userIds, List<AccountResponse> accounts, List<OfferingResponse> offerings, DemoReferenceData ref, List<DemoDataRecord> records, Map<String, Integer> counts) {
        if (accounts.isEmpty()) return;
        
        // Distribution targets: 2 Qualification, 2 Discovery, 1 Proposal, 2 Negotiation, 2 Closed Won, 1 Closed Lost
        String[] stageTargets = {
            "Qualification", "Qualification",
            "Discovery", "Discovery",
            "Proposal",
            "Negotiation", "Negotiation",
            "Closed Won", "Closed Won",
            "Closed Lost"
        };
        
        int i = 0;
        for (String targetStage : stageTargets) {
            AccountResponse account = accounts.get(i % accounts.size());
            UUID owner = randomUser(userIds);
            
            // Map target stage name to ID, defaulting to an OPEN stage if matching fails (it shouldn't)
            UUID targetStageId = ref.dealStages().getOrDefault(targetStage, ref.dealStages().values().iterator().next());
            
            // If it's intended to be won or lost, start it in an OPEN stage first to allow lifecycle events
            boolean willWin = targetStage.equals("Closed Won");
            boolean willLose = targetStage.equals("Closed Lost");
            UUID initialStageId = (willWin || willLose) ? ref.dealStages().get("Negotiation") : targetStageId;
            if (initialStageId == null) initialStageId = ref.dealStages().values().iterator().next(); // fallback
            
            DealCreateRequest dealReq = DealCreateRequest.builder()
                .name(account.getName() + " - Deal " + (i + 1))
                .accountId(account.getId())
                .stageId(initialStageId)
                .ownerUserId(owner)
                .expectedCloseDate(LocalDate.now().plusDays(15 + (i * 5)))
                .amount(new BigDecimal("0")) // Will be recalculated by line items
                .build();
            
            DealResponse deal = dealService.createDeal(tenantId, adminUserId, dealReq);
            trackRecord(tenantId, "DEAL", deal.getId(), records, counts);
            
            // Add Line Items
            if (!offerings.isEmpty()) {
                OfferingResponse off1 = offerings.get(i % offerings.size());
                DealLineItemCreateRequest liReq1 = DealLineItemCreateRequest.builder()
                    .offeringId(off1.getId())
                    .quantity(new BigDecimal("5"))
                    .unitPrice(off1.getDefaultPrice() != null ? off1.getDefaultPrice() : new BigDecimal("500.00"))
                    .build();
                var createdLi1 = dealLineItemService.createLineItem(tenantId, deal.getId(), adminUserId, liReq1);
                // Fix tracking: use createdLi1.getId() instead of deal.getId()
                trackRecord(tenantId, "DEAL_LINE_ITEM", createdLi1.getId(), records, counts);
                
                // Add second line item for some deals
                if (i % 2 == 0) {
                    OfferingResponse off2 = offerings.get((i + 1) % offerings.size());
                    DealLineItemCreateRequest liReq2 = DealLineItemCreateRequest.builder()
                        .offeringId(off2.getId())
                        .quantity(new BigDecimal("1"))
                        .unitPrice(off2.getDefaultPrice() != null ? off2.getDefaultPrice() : new BigDecimal("1000.00"))
                        .build();
                    var createdLi2 = dealLineItemService.createLineItem(tenantId, deal.getId(), adminUserId, liReq2);
                    trackRecord(tenantId, "DEAL_LINE_ITEM", createdLi2.getId(), records, counts);
                }
            }

            // Create Meeting linked to deal
            MeetingCreateRequest meetingReq = MeetingCreateRequest.builder()
                .subject("Deal Review: " + deal.getName())
                .meetingType(MeetingType.VIDEO)
                .startTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .entityType("DEAL")
                .entityId(deal.getId())
                .assignedTo(owner)
                .build();
            var meeting = meetingService.createMeeting(tenantId, adminUserId, meetingReq);
            trackRecord(tenantId, "MEETING", meeting.getId(), records, counts);

            // Trigger Won/Lost lifecycle
            if (willWin) {
                dealService.markDealWon(deal.getId(), tenantId, adminUserId, "Excellent presentation");
            } else if (willLose) {
                dealService.markDealLost(deal.getId(), tenantId, adminUserId, "Price too high");
            }
            i++;
        }
    }

    private void validateInstallation(Map<String, Integer> counts) {
        int leadStatuses = counts.getOrDefault("leadStatuses", 0);
        int leadSources = counts.getOrDefault("leadSources", 0);
        int leads = counts.getOrDefault("leads", 0);
        int accounts = counts.getOrDefault("accounts", 0);
        int contacts = counts.getOrDefault("contacts", 0);
        int dealStages = counts.getOrDefault("dealStages", 0);
        int deals = counts.getOrDefault("deals", 0);
        int dealLineItems = counts.getOrDefault("dealLineItems", 0);
        int offerings = counts.getOrDefault("offerings", 0);
        int tasks = counts.getOrDefault("tasks", 0);
        int calls = counts.getOrDefault("calls", 0);
        int meetings = counts.getOrDefault("meetings", 0);

        if (leadStatuses < 5) throw new IllegalStateException("Validation failed: Insufficient lead statuses");
        if (leadSources < 7) throw new IllegalStateException("Validation failed: Insufficient lead sources");
        if (leads < 8) throw new IllegalStateException("Validation failed: Insufficient leads");
        if (accounts < 5) throw new IllegalStateException("Validation failed: Insufficient accounts");
        if (contacts < 10) throw new IllegalStateException("Validation failed: Insufficient contacts");
        if (dealStages < 6) throw new IllegalStateException("Validation failed: Insufficient deal stages");
        if (offerings < 6) throw new IllegalStateException("Validation failed: Insufficient offerings");
        if (deals < 8) throw new IllegalStateException("Validation failed: Insufficient deals");
        if (dealLineItems < 8) throw new IllegalStateException("Validation failed: Insufficient deal line items");
        if (tasks < 1) throw new IllegalStateException("Validation failed: Insufficient tasks");
        if (calls < 1) throw new IllegalStateException("Validation failed: Insufficient calls");
        if (meetings < 1) throw new IllegalStateException("Validation failed: Insufficient meetings");
    }

    private void trackRecord(UUID tenantId, String type, UUID entityId, List<DemoDataRecord> records, Map<String, Integer> counts) {
        records.add(DemoDataRecord.builder()
                .tenantId(tenantId)
                .templateKey(TEMPLATE_KEY)
                .entityType(type)
                .entityId(entityId)
                .build());
        
        String key = type.toLowerCase() + "s";
        if (type.equals("DEAL_LINE_ITEM")) key = "dealLineItems";
        if (type.equals("LEAD_STATUS")) key = "leadStatuses";
        if (type.equals("LEAD_SOURCE")) key = "leadSources";
        if (type.equals("DEAL_STAGE")) key = "dealStages";

        counts.put(key, counts.getOrDefault(key, 0) + 1);
    }
    
    private UUID randomUser(List<UUID> users) {
        return users.get(new Random().nextInt(users.size()));
    }
}
