package dev.blank.mobile.agent

import kotlin.test.Test
import kotlin.test.assertEquals

class ApprovalPolicyTest {
    @Test
    fun observeAllowedByDefault() {
        assertEquals(ApprovalDecision.ALLOW, ApprovalPolicy().decide("device_observe"))
    }

    @Test
    fun actAsksByDefault() {
        assertEquals(ApprovalDecision.ASK, ApprovalPolicy().decide("device_act"))
    }

    @Test
    fun unknownToolAsks() {
        assertEquals(ApprovalDecision.ASK, ApprovalPolicy().decide("mystery"))
    }

    @Test
    fun allowAlwaysOverrides() {
        val policy = ApprovalPolicy()
        policy.allowAlways("device_act")
        assertEquals(ApprovalDecision.ALLOW, policy.decide("device_act"))
        assertEquals(true, policy.isAlways("device_act"))
        assertEquals(ApprovalDecision.ASK, policy.decide("app_control"))
    }
}