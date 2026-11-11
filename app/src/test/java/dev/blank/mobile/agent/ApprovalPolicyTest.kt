package dev.blank.mobile.agent

import dev.blank.mobile.provider.ToolCallRef
import kotlin.test.Test
import kotlin.test.assertEquals

class ApprovalPolicyTest {
    private fun call(name: String, arguments: String) = ToolCallRef("c1", name, arguments)

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

    @Test
    fun callAlwaysIsPerNumber() {
        val policy = ApprovalPolicy()
        val number = call("phone_call", """{"action":"call","number":"010-1234-5678"}""")
        assertEquals(ApprovalDecision.ASK, policy.decide(number))
        policy.allowAlways(number)
        assertEquals(ApprovalDecision.ALLOW, policy.decide(call("phone_call", """{"action":"call","number":"01012345678"}""")))
        assertEquals(ApprovalDecision.ASK, policy.decide(call("phone_call", """{"action":"call","number":"01099998888"}""")))
        assertEquals(ApprovalDecision.ASK, policy.decide(call("phone_call", """{"action":"state"}""")))
    }

    @Test
    fun nonCallPhoneActionsUseToolKey() {
        val policy = ApprovalPolicy()
        policy.allowAlways(call("phone_call", """{"action":"answer"}"""))
        assertEquals(ApprovalDecision.ALLOW, policy.decide(call("phone_call", """{"action":"end"}""")))
        assertEquals(ApprovalDecision.ASK, policy.decide(call("phone_call", """{"action":"call","number":"01012345678"}""")))
    }
}