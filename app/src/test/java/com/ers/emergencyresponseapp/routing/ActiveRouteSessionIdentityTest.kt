package com.ers.emergencyresponseapp.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveRouteSessionIdentityTest {
    @Test
    fun `assignment identity takes precedence over incident identity`() {
        assertEquals(
            "assignment:9001",
            ActiveRouteSessionStore.identityFor(
                incidentId = "42",
                assignmentId = " 9001 "
            )
        )
    }

    @Test
    fun `incident identity is used when assignment is unavailable`() {
        assertEquals(
            "incident:42",
            ActiveRouteSessionStore.identityFor(
                incidentId = " 42 ",
                assignmentId = null
            )
        )
    }

    @Test
    fun `blank operational identity is rejected`() {
        assertNull(
            ActiveRouteSessionStore.identityFor(
                incidentId = " ",
                assignmentId = ""
            )
        )
    }
}
