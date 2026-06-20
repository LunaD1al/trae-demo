package com.cyrain.chatscope.membership;

import com.cyrain.chatscope.config.DemoProperties;
import com.cyrain.chatscope.openim.adapter.OpenImAdapter;
import com.cyrain.chatscope.quota.QuotaOverview;
import com.cyrain.chatscope.quota.QuotaService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Ensures a user belongs to the single Demo group.
 *
 * <p>Called after public registration succeeds, and as an idempotent top-up when an already-logged-in
 * user enters the light Web Demo. The flow is: confirm the OpenIM user exists, upsert the ChatScope
 * user mapping, idempotently join the Demo group, initialise today's quota, and return the group id
 * plus a quota overview. Re-invoking is safe and produces the same result without errors.
 */
@Service
public class MembershipService {

    private static final Logger log = LoggerFactory.getLogger(MembershipService.class);

    private final OpenImAdapter openImAdapter;
    private final CsUserRepository csUserRepository;
    private final QuotaService quotaService;
    private final DemoProperties demoProperties;

    MembershipService(
            OpenImAdapter openImAdapter,
            CsUserRepository csUserRepository,
            QuotaService quotaService,
            DemoProperties demoProperties) {
        this.openImAdapter = openImAdapter;
        this.csUserRepository = csUserRepository;
        this.quotaService = quotaService;
        this.demoProperties = demoProperties;
    }

    public EnsureMembershipResponse ensure(String openimUserId) {
        if (!openImAdapter.userExists(openimUserId)) {
            throw new OpenImUserNotFoundException(openimUserId);
        }

        String groupId = demoProperties.groupId();
        csUserRepository.upsert(openimUserId);
        openImAdapter.ensureUserInGroup(groupId, openimUserId);
        csUserRepository.markJoinedDemoGroup(openimUserId);

        QuotaOverview quota = quotaService.ensureDailyAccount(openimUserId);
        log.info("Ensured membership for {} in group {}", openimUserId, groupId);
        return new EnsureMembershipResponse(groupId, openimUserId, quota);
    }
}
