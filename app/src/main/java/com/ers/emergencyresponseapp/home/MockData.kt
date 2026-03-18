package com.ers.emergencyresponseapp.home

import java.util.Date

// Shared mock incidents used by multiple screens for UI-only demos.
fun getMockIncidents(): List<Incident> {
    val now = Date()
    return listOf(
        Incident("1", IncidentType.FIRE, IncidentPriority.HIGH, "123 Main St", Date(now.time - 1000L * 60 * 5), IncidentStatus.REPORTED, "Building fire on 2nd floor", latitude = 14.5995, longitude = 120.9842),
        Incident("2", IncidentType.MEDICAL, IncidentPriority.MEDIUM, "456 Oak Ave", Date(now.time - 1000L * 60 * 30), IncidentStatus.DISPATCHED, "Possible stroke, unconscious", latitude = 14.6005, longitude = 120.9852),
        Incident("3", IncidentType.CRIME, IncidentPriority.LOW, "789 Pine Ln", Date(now.time - 1000L * 60 * 90), IncidentStatus.ON_SCENE, "Shoplifting incident - suspect detained", latitude = 14.6015, longitude = 120.9862),
        Incident("4", IncidentType.FIRE, IncidentPriority.MEDIUM, "22 Elm St", Date(now.time - 1000L * 60 * 12), IncidentStatus.DISPATCHED, "Kitchen fire - contained", latitude = 14.6025, longitude = 120.9872),
        Incident("5", IncidentType.MEDICAL, IncidentPriority.HIGH, "100 River Rd", Date(now.time - 1000L * 60 * 3), IncidentStatus.ON_SCENE, "Multiple injuries from vehicle crash", latitude = 14.6035, longitude = 120.9882),
        Incident("6", IncidentType.CRIME, IncidentPriority.MEDIUM, "5th Ave & Main", Date(now.time - 1000L * 60 * 45), IncidentStatus.DISPATCHED, "Assault reported, witness on scene", latitude = 14.6045, longitude = 120.9892),
        Incident("7", IncidentType.FIRE, IncidentPriority.LOW, "Green Park", Date(now.time - 1000L * 60 * 240), IncidentStatus.RESOLVED, "Small grass fire - extinguished", assignedTo = "Name", latitude = 14.6055, longitude = 120.9902),
        Incident("8", IncidentType.DISASTER, IncidentPriority.HIGH, "Harbor Area", Date(now.time - 1000L * 60 * 8), IncidentStatus.REPORTED, "Flooding reported near docks", latitude = 14.6065, longitude = 120.9912),
        Incident("9", IncidentType.MEDICAL, IncidentPriority.LOW, "12 Oak Blvd", Date(now.time - 1000L * 60 * 200), IncidentStatus.RESOLVED, "Non-emergency - resolved on arrival", assignedTo = "Name", latitude = 14.6075, longitude = 120.9922),
        Incident("10", IncidentType.CRIME, IncidentPriority.HIGH, "Central Station", Date(now.time - 1000L * 60 * 2), IncidentStatus.REPORTED, "Armed robbery in progress", latitude = 14.6085, longitude = 120.9932)
    )
}
