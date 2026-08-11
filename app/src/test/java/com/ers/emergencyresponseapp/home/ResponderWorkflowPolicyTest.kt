package com.ers.emergencyresponseapp.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponderWorkflowPolicyTest {
    @Test
    fun `assignment status exposes only its valid next action`() {
        assertEquals(
            IncidentPrimaryAction.START_RESPONSE,
            ResponderWorkflowPolicy.primaryAction(IncidentStatus.DISPATCHED)
        )
        assertEquals(
            IncidentPrimaryAction.RESUME_NAVIGATION,
            ResponderWorkflowPolicy.primaryAction(IncidentStatus.ON_ROUTE)
        )
        assertEquals(
            IncidentPrimaryAction.COMPLETE_INCIDENT,
            ResponderWorkflowPolicy.primaryAction(IncidentStatus.ON_SCENE)
        )
        assertEquals(
            IncidentPrimaryAction.NONE,
            ResponderWorkflowPolicy.primaryAction(IncidentStatus.RESOLVED)
        )
    }

    @Test
    fun `coordinates must be finite and inside earth bounds`() {
        assertTrue(ResponderWorkflowPolicy.hasValidCoordinates(14.5995, 120.9842))
        assertTrue(ResponderWorkflowPolicy.hasValidCoordinates(-90.0, 180.0))
        assertFalse(ResponderWorkflowPolicy.hasValidCoordinates(null, 120.0))
        assertFalse(ResponderWorkflowPolicy.hasValidCoordinates(91.0, 120.0))
        assertFalse(ResponderWorkflowPolicy.hasValidCoordinates(14.0, -181.0))
        assertFalse(ResponderWorkflowPolicy.hasValidCoordinates(Double.NaN, 120.0))
    }

    @Test
    fun `stored coordinates are resumable only for the same incident`() {
        assertTrue(ResponderWorkflowPolicy.canResumeStoredRoute("42", "42"))
        assertTrue(ResponderWorkflowPolicy.canResumeStoredRoute(" 42 ", "42"))
        assertFalse(ResponderWorkflowPolicy.canResumeStoredRoute("41", "42"))
        assertFalse(ResponderWorkflowPolicy.canResumeStoredRoute("", "42"))
    }

    @Test
    fun `arrival radius is accuracy aware and bounded`() {
        assertEquals(null, ResponderWorkflowPolicy.arrivalEnterRadiusMeters(null))
        assertEquals(40.0, ResponderWorkflowPolicy.arrivalEnterRadiusMeters(8f)!!, 0.0)
        assertEquals(48.0, ResponderWorkflowPolicy.arrivalEnterRadiusMeters(48f)!!, 0.0)
        assertEquals(null, ResponderWorkflowPolicy.arrivalEnterRadiusMeters(72f))
        assertEquals(68.0, ResponderWorkflowPolicy.arrivalExitRadiusMeters(48f)!!, 0.0)
    }
}
