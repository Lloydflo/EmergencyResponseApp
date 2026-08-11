package com.ers.emergencyresponseapp.routing

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveRouteSessionStorePersistenceTest {
    @Test
    fun `conflicting valid session is rejected without changing active session`() {
        val store = newStore()
        assertEquals(PRIMARY_SESSION_ID, store.beginSession(PRIMARY_METADATA)?.sessionId)
        assertTrue(store.saveInitialOrigin(RESPONDER_ID, PRIMARY_SESSION_ID, 33.45, -112.07))
        assertTrue(store.saveOriginalRoute(RESPONDER_ID, PRIMARY_SESSION_ID, ORIGINAL_ROUTE))
        assertTrue(store.saveStepIndex(RESPONDER_ID, PRIMARY_SESSION_ID, 4))
        val originalSession = store.read(RESPONDER_ID, PRIMARY_SESSION_ID)

        val conflictingMetadata = PRIMARY_METADATA.copy(
            incidentId = "incident-84",
            assignmentId = "assignment-9002",
            destinationLat = 33.51,
            destinationLng = -112.11
        )

        assertNull(store.beginSession(conflictingMetadata))
        assertEquals(originalSession, store.read(RESPONDER_ID, PRIMARY_SESSION_ID))
        assertNull(store.read(RESPONDER_ID, requireNotNull(conflictingMetadata.sessionId)))
    }

    @Test
    fun `first origin write wins and later coordinates cannot replace it`() {
        val store = startedStore()

        assertTrue(store.saveInitialOrigin(RESPONDER_ID, PRIMARY_SESSION_ID, 33.45, -112.07))
        assertTrue(store.saveInitialOrigin(RESPONDER_ID, PRIMARY_SESSION_ID, 33.45, -112.07))
        assertFalse(store.saveInitialOrigin(RESPONDER_ID, PRIMARY_SESSION_ID, 33.46, -112.08))

        val restored = requireNotNull(store.read(RESPONDER_ID, PRIMARY_SESSION_ID))
        assertEquals(33.45, restored.initialOriginLat)
        assertEquals(-112.07, restored.initialOriginLng)
    }

    @Test
    fun `original route write wins and is idempotent only for the same route`() {
        val store = startedStore()

        assertTrue(
            store.saveOriginalRoute(
                RESPONDER_ID,
                PRIMARY_SESSION_ID,
                "  $ORIGINAL_ROUTE  "
            )
        )
        assertTrue(store.saveOriginalRoute(RESPONDER_ID, PRIMARY_SESSION_ID, ORIGINAL_ROUTE))
        assertFalse(
            store.saveOriginalRoute(
                RESPONDER_ID,
                PRIMARY_SESSION_ID,
                "{\"route\":\"replacement\"}"
            )
        )

        assertEquals(
            ORIGINAL_ROUTE,
            store.read(RESPONDER_ID, PRIMARY_SESSION_ID)?.originalRouteJson
        )
    }

    @Test
    fun `only exact pending request can approve or clear and acceptance is atomic`() {
        val store = startedStore()
        assertTrue(store.saveOriginalRoute(RESPONDER_ID, PRIMARY_SESSION_ID, ORIGINAL_ROUTE))
        assertTrue(store.saveStepIndex(RESPONDER_ID, PRIMARY_SESSION_ID, 7))
        assertTrue(
            store.savePendingAlternative(
                responderId = RESPONDER_ID,
                sessionId = PRIMARY_SESSION_ID,
                requestId = STALE_REQUEST_ID,
                startLat = 33.45,
                startLng = -112.07,
                requestedAtMillis = 1_000L
            )
        )
        assertTrue(
            store.savePendingAlternative(
                responderId = RESPONDER_ID,
                sessionId = PRIMARY_SESSION_ID,
                requestId = CURRENT_REQUEST_ID,
                startLat = 33.46,
                startLng = -112.08,
                requestedAtMillis = 2_000L
            )
        )
        val pendingSession = requireNotNull(store.read(RESPONDER_ID, PRIMARY_SESSION_ID))
        assertEquals(CURRENT_REQUEST_ID, pendingSession.pendingAlternativeRequestId)

        assertFalse(
            store.acceptApprovedAlternative(
                RESPONDER_ID,
                PRIMARY_SESSION_ID,
                STALE_REQUEST_ID,
                APPROVED_ROUTE
            )
        )
        assertFalse(
            store.clearPendingAlternative(
                RESPONDER_ID,
                PRIMARY_SESSION_ID,
                STALE_REQUEST_ID
            )
        )
        assertEquals(pendingSession, store.read(RESPONDER_ID, PRIMARY_SESSION_ID))

        assertTrue(
            store.acceptApprovedAlternative(
                RESPONDER_ID,
                PRIMARY_SESSION_ID,
                CURRENT_REQUEST_ID,
                "  $APPROVED_ROUTE  "
            )
        )

        val accepted = requireNotNull(store.read(RESPONDER_ID, PRIMARY_SESSION_ID))
        assertEquals(ORIGINAL_ROUTE, accepted.originalRouteJson)
        assertEquals(APPROVED_ROUTE, accepted.approvedRouteJson)
        assertEquals(ActiveRouteMode.ALTERNATIVE, accepted.selectedMode)
        assertNull(accepted.pendingAlternativeRequestId)
        assertNull(accepted.pendingAlternativeRequestedAtMillis)
        assertNull(accepted.pendingAlternativeStartLat)
        assertNull(accepted.pendingAlternativeStartLng)
        assertEquals(0, accepted.currentStepIndex)
    }

    @Test
    fun `session restores from the same preferences in a new store instance`() {
        val preferences = InMemorySharedPreferences()
        val firstStore = newStore(preferences)
        assertEquals(PRIMARY_SESSION_ID, firstStore.beginSession(PRIMARY_METADATA)?.sessionId)
        assertTrue(
            firstStore.saveInitialOrigin(
                RESPONDER_ID,
                PRIMARY_SESSION_ID,
                33.45,
                -112.07
            )
        )
        assertTrue(
            firstStore.saveOriginalRoute(
                RESPONDER_ID,
                PRIMARY_SESSION_ID,
                ORIGINAL_ROUTE
            )
        )
        assertTrue(firstStore.saveStepIndex(RESPONDER_ID, PRIMARY_SESSION_ID, 5))
        assertTrue(
            firstStore.savePendingAlternative(
                responderId = RESPONDER_ID,
                sessionId = PRIMARY_SESSION_ID,
                requestId = CURRENT_REQUEST_ID,
                startLat = 33.46,
                startLng = -112.08,
                requestedAtMillis = 2_000L
            )
        )

        val restoredStore = newStore(preferences)

        assertEquals(
            ActiveRouteSession(
                sessionId = PRIMARY_SESSION_ID,
                incidentId = "incident-42",
                assignmentId = "assignment-9001",
                responderId = RESPONDER_ID,
                destinationLat = 33.50,
                destinationLng = -112.10,
                destinationAddress = "10 Main St",
                initialOriginLat = 33.45,
                initialOriginLng = -112.07,
                originalRouteJson = ORIGINAL_ROUTE,
                approvedRouteJson = null,
                selectedMode = ActiveRouteMode.ORIGINAL,
                pendingAlternativeRequestId = CURRENT_REQUEST_ID,
                pendingAlternativeRequestedAtMillis = 2_000L,
                pendingAlternativeStartLat = 33.46,
                pendingAlternativeStartLng = -112.08,
                currentStepIndex = 5
            ),
            restoredStore.read(RESPONDER_ID, PRIMARY_SESSION_ID)
        )
    }

    private fun startedStore(): ActiveRouteSessionStore = newStore().also { store ->
        assertEquals(PRIMARY_SESSION_ID, store.beginSession(PRIMARY_METADATA)?.sessionId)
    }

    private fun newStore(
        preferences: SharedPreferences = InMemorySharedPreferences()
    ): ActiveRouteSessionStore = ActiveRouteSessionStore(preferences)

    private companion object {
        const val RESPONDER_ID = 77
        const val PRIMARY_SESSION_ID = "assignment:assignment-9001"
        const val STALE_REQUEST_ID = 101L
        const val CURRENT_REQUEST_ID = 202L
        const val ORIGINAL_ROUTE = "{\"route\":\"original\"}"
        const val APPROVED_ROUTE = "{\"route\":\"approved-alternative\"}"

        val PRIMARY_METADATA = ActiveRouteSessionMetadata(
            incidentId = "incident-42",
            assignmentId = "assignment-9001",
            responderId = RESPONDER_ID,
            destinationLat = 33.50,
            destinationLng = -112.10,
            destinationAddress = "10 Main St"
        )
    }
}
