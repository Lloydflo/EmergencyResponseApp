package com.ers.emergencyresponseapp

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.ers.emergencyresponseapp.home.IncidentStore
import com.ers.emergencyresponseapp.home.IncidentStatus
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.tooling.preview.Preview
import com.ers.emergencyresponseapp.ui.theme.EmergencyResponseAppTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.layout.ContentScale

private enum class ReviewStatus(val title: String, val color: Color) {
    Pending("Pending Review", Color(0xFFFFA000)),
    Submitted("Feedback Submitted", Color(0xFF0277BD)),
    Completed("Completed", Color(0xFF388E3C))
}

private data class ReviewableIncident(
    val id: String,
    val type: String,
    val date: String,
    val status: ReviewStatus,
    val proofUri: String? = null, // optional image proof captured when marking done
    val completionNotes: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewsTopAppBar() {
    TopAppBar(
        title = { Text("Reviews & Feedback", fontWeight = FontWeight.Bold) },
        actions = {
            IconButton(onClick = { /* UI Only */ }) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
private fun ReviewFilters(selectedFilter: ReviewStatus, onSelect: (ReviewStatus) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 17.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = (selectedFilter == ReviewStatus.Pending),
            onClick = { onSelect(ReviewStatus.Pending) },
            label = { Text("Pending") },
            leadingIcon = { Icon(Icons.Default.HourglassTop, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        FilterChip(
            selected = (selectedFilter == ReviewStatus.Submitted),
            onClick = { onSelect(ReviewStatus.Submitted) },
            label = { Text("Submitted") },
            leadingIcon = { Icon(Icons.Filled.Comment, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        FilterChip(
            selected = (selectedFilter == ReviewStatus.Completed),
            onClick = { onSelect(ReviewStatus.Completed) },
            label = { Text("Completed") },
            leadingIcon = { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
    }
}

@Composable
private fun IncidentReviewList(selectedFilter: ReviewStatus) {
    // Obtain current responder name from SharedPreferences so we only show resolved incidents assigned to them
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("ers_prefs", android.content.Context.MODE_PRIVATE)
    // Note: we build candidate names below; no single responderName variable required.

    // Dialog state for composing a review
    val showComposeDialog = remember { mutableStateOf(false) }
    val reviewText = rememberSaveable { mutableStateOf("") }
    val composeSelected = remember { mutableStateOf<ReviewableIncident?>(null) }

    // Dialog state for viewing submitted review details
    val showDetailsDialog = remember { mutableStateOf(false) }
    val detailsText = rememberSaveable { mutableStateOf("") }
    val detailsSelected = remember { mutableStateOf<ReviewableIncident?>(null) }

    // State for image preview dialog (full screen)
    val showImageDialog = remember { mutableStateOf<String?>(null) }

    // Submit handler: delegate to IncidentStore and show toast. This moves incident -> SUBMITTED_REVIEW.
    fun submitReview(incident: ReviewableIncident, text: String) {
        val plainId = incident.id.removePrefix("#")
        try {
            IncidentStore.submitReview(plainId, text)
            Toast.makeText(context, "Review submitted", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, "Failed to submit review", Toast.LENGTH_SHORT).show()
        }
    }

    // Build a set of possible names that may be stored as assignedTo (support username/full name/legacy key)
    val candidateNames = listOfNotNull(
        prefs.getString("account_username", null),
        prefs.getString("responder_name", null),
        prefs.getString("account_full_name", null)
    ).map { it.trim().lowercase() }.toSet()

    // Derive the reviewable incidents dynamically from the shared IncidentStore and submitted reviews
    val derivedIncidents = IncidentStore.incidents
        .filter { inc ->
            // include incidents that are in the review workflow and assigned to this responder
            val assigned = inc.assignedTo?.trim()?.lowercase()
            val matchesAssigned = if (candidateNames.isNotEmpty()) assigned != null && candidateNames.contains(assigned) else assigned != null
            // include only PENDING_REVIEW, SUBMITTED_REVIEW, or RESOLVED (completed)
            matchesAssigned && (inc.status == IncidentStatus.PENDING_REVIEW || inc.status == IncidentStatus.SUBMITTED_REVIEW || inc.status == IncidentStatus.RESOLVED)
        }
        .map { inc ->
            val proof = try { IncidentStore.getProofUri(inc.id) } catch (_: Exception) { null }
            val notes = try { IncidentStore.getCompletionNotes(inc.id) } catch (_: Exception) { null }
            val submittedText = try { IncidentStore.getSubmittedReview(inc.id) } catch (_: Exception) { null }
            val completionTs = try { IncidentStore.getCompletionTime(inc.id) } catch (_: Exception) { null }
            val status = when (inc.status) {
                IncidentStatus.PENDING_REVIEW -> ReviewStatus.Pending
                IncidentStatus.SUBMITTED_REVIEW -> ReviewStatus.Submitted
                IncidentStatus.RESOLVED -> if (completionTs != null) ReviewStatus.Completed else ReviewStatus.Submitted
                else -> ReviewStatus.Pending
            }
            val dateStr = completionTs?.let { java.text.SimpleDateFormat("yyyy-MM-dd | HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it)) }
                ?: java.text.SimpleDateFormat("yyyy-MM-dd | HH:mm", java.util.Locale.getDefault()).format(inc.timeReported)
            // For submitted status, prefer submittedText as details
            val detailsNotes = submittedText ?: notes
            ReviewableIncident("#${inc.id}", inc.type.displayName, dateStr, status, proof, detailsNotes)
        }

    // Keep some static submitted/completed examples after the pending ones
    val listForDisplay = derivedIncidents + listOf(
        ReviewableIncident("#C-5678", "Robbery", "2024-07-27 | 21:00", ReviewStatus.Submitted),
        ReviewableIncident("#D-1123", "Flood Zone Evac", "2024-07-26 | 11:45", ReviewStatus.Completed)
    )

    // Apply the selected filter to the display list
    val filteredForDisplay = listForDisplay.filter { it.status == selectedFilter }

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(filteredForDisplay) { incident ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = incident.type, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(text = "Incident ID: ${incident.id}", style = MaterialTheme.typography.bodySmall)
                        }
                        Badge(containerColor = incident.status.color) {
                            Text(text = incident.status.title, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    // Show date and proof thumbnail if available, and completion notes
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text(text = "Date: ${incident.date}", style = MaterialTheme.typography.bodyMedium)
                            incident.completionNotes?.let { notes ->
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(text = "Notes: $notes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                            }
                        }
                        incident.proofUri?.let { uriStr ->
                            val ctx = LocalContext.current
                            val bitmap = remember(uriStr) {
                                try {
                                    if (uriStr.startsWith("file://")) {
                                        val path = Uri.parse(uriStr).path
                                        if (path != null) BitmapFactory.decodeFile(path) else null
                                    } else {
                                        val uri = Uri.parse(uriStr)
                                        ctx.contentResolver.openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream) }
                                    }
                                } catch (_: Exception) { null }
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Proof",
                                    modifier = Modifier.size(64.dp).clickable { showImageDialog.value = uriStr },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = {
                        if (incident.status == ReviewStatus.Pending) {
                            composeSelected.value = incident
                            reviewText.value = ""
                            showComposeDialog.value = true
                        } else {
                            // For Submitted/Completed show details popup (if we have stored details)
                            val plainId = incident.id.removePrefix("#")
                            val found = IncidentStore.getSubmittedReview(plainId)
                            detailsText.value = found ?: IncidentStore.getCompletionNotes(plainId) ?: "No review details available"
                             detailsSelected.value = incident
                             showDetailsDialog.value = true
                         }
                     }, modifier = Modifier.align(Alignment.End)) {
                         Text(if (incident.status == ReviewStatus.Pending) "Write Review" else "View Details")
                     }
                 }
             }
         }
     }

     // Compose dialog: require non-empty reviewText before enabling Submit
     if (showComposeDialog.value && composeSelected.value != null) {
         AlertDialog(
             onDismissRequest = { showComposeDialog.value = false; composeSelected.value = null },
             title = { Text(text = "Write Review for ${composeSelected.value?.type}") },
             text = {
                 Column {
                     OutlinedTextField(
                         value = reviewText.value,
                         onValueChange = { reviewText.value = it },
                         label = { Text("Your feedback") },
                         modifier = Modifier.fillMaxWidth()
                     )
                     if (reviewText.value.isBlank()) {
                         Spacer(modifier = Modifier.size(6.dp))
                         Text(text = "Please enter feedback before submitting.", color = Color(0xFFD32F2F), fontWeight = FontWeight.SemiBold)
                     }
                 }
             },
             confirmButton = {
                 Button(onClick = {
                      val inc = composeSelected.value
                      if (inc != null && reviewText.value.isNotBlank()) {
                         submitReview(inc, reviewText.value)
                      }
                      reviewText.value = ""
                      composeSelected.value = null
                      showComposeDialog.value = false
                  }, enabled = reviewText.value.isNotBlank()) { Text("Submit") }
             },
             dismissButton = {
                 Button(onClick = { showComposeDialog.value = false; composeSelected.value = null }) { Text("Cancel") }
             }
         )
     }

    // Details dialog for submitted reviews
    if (showDetailsDialog.value && detailsSelected.value != null) {
        AlertDialog(
            onDismissRequest = { showDetailsDialog.value = false; detailsSelected.value = null },
            title = { Text(text = "Review Details for ${detailsSelected.value?.type}") },
            text = { Column { Text(text = detailsText.value) } },
            confirmButton = { Button(onClick = { showDetailsDialog.value = false; detailsSelected.value = null }) { Text("OK") } },
            dismissButton = { /* no-op */ }
        )
    }

    // Full image preview dialog
    val previewUri = showImageDialog.value
    if (previewUri != null) {
        AlertDialog(
            onDismissRequest = { showImageDialog.value = null },
            title = { /* no title for image */ Text("") },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val ctx = LocalContext.current
                    val fullBitmap = remember(previewUri) {
                        try {
                            if (previewUri.startsWith("file://")) {
                                val path = Uri.parse(previewUri).path
                                if (path != null) BitmapFactory.decodeFile(path) else null
                            } else {
                                val uri = Uri.parse(previewUri)
                                ctx.contentResolver.openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream) }
                            }
                        } catch (_: Exception) { null }
                    }

                    if (fullBitmap != null) {
                        Image(bitmap = fullBitmap.asImageBitmap(), contentDescription = "Full proof image", modifier = Modifier.fillMaxWidth().sizeIn(maxHeight = 600.dp), contentScale = ContentScale.Fit)
                    } else {
                        Text("Image not available")
                    }
                }
            },
            confirmButton = { Button(onClick = { showImageDialog.value = null }) { Text("Close") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewsFeedbackScreen() {
    // Filter state for Pending / Submitted / Completed
    val selectedFilterState = remember { mutableStateOf(ReviewStatus.Pending) }

    Scaffold(
        topBar = { ReviewsTopAppBar() }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            ReviewFilters(selectedFilterState.value) { selectedFilterState.value = it }
            IncidentReviewList(selectedFilterState.value)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ReviewsFeedbackScreenPreview() {
    EmergencyResponseAppTheme {
        ReviewsFeedbackScreen()
    }
}
