package com.ers.emergencyresponseapp.home

import java.util.Date

// Shared mock incidents used by multiple screens for UI-only demos.
fun getMockIncidents(): List<Incident> {
    val now = Date()
    return listOf(
        Incident("1", IncidentType.FIRE, IncidentPriority.HIGH, "123 Main St", Date(now.time - 1000L * 60 * 5), IncidentStatus.REPORTED, "Building fire on 2nd floor"),
        Incident("2", IncidentType.MEDICAL, IncidentPriority.MEDIUM, "456 Oak Ave", Date(now.time - 1000L * 60 * 30), IncidentStatus.DISPATCHED, "Possible stroke, unconscious"),
        Incident("3", IncidentType.CRIME, IncidentPriority.LOW, "789 Pine Ln", Date(now.time - 1000L * 60 * 90), IncidentStatus.ON_SCENE, "Shoplifting incident - suspect detained"),
        Incident("4", IncidentType.FIRE, IncidentPriority.MEDIUM, "22 Elm St", Date(now.time - 1000L * 60 * 12), IncidentStatus.DISPATCHED, "Kitchen fire - contained"),
        Incident("5", IncidentType.MEDICAL, IncidentPriority.HIGH, "100 River Rd", Date(now.time - 1000L * 60 * 3), IncidentStatus.ON_SCENE, "Multiple injuries from vehicle crash"),
        Incident("6", IncidentType.CRIME, IncidentPriority.MEDIUM, "5th Ave & Main", Date(now.time - 1000L * 60 * 45), IncidentStatus.DISPATCHED, "Assault reported, witness on scene"),
        Incident("7", IncidentType.FIRE, IncidentPriority.LOW, "Green Park", Date(now.time - 1000L * 60 * 240), IncidentStatus.RESOLVED, "Small grass fire - extinguished", assignedTo = "Name"),
        Incident("8", IncidentType.DISASTER, IncidentPriority.HIGH, "Harbor Area", Date(now.time - 1000L * 60 * 8), IncidentStatus.REPORTED, "Flooding reported near docks"),
        Incident("9", IncidentType.MEDICAL, IncidentPriority.LOW, "12 Oak Blvd", Date(now.time - 1000L * 60 * 200), IncidentStatus.RESOLVED, "Non-emergency - resolved on arrival", assignedTo = "Name"),
        Incident("10", IncidentType.CRIME, IncidentPriority.HIGH, "Central Station", Date(now.time - 1000L * 60 * 2), IncidentStatus.REPORTED, "Armed robbery in progress")
    )
}
