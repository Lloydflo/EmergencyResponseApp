package com.ers.emergencyresponseapp.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A reusable, locally persisted collection of short response phrases for
 * emergency documentation fields. Templates are stored per field so responders
 * can tailor them without changing server data or another responder's device.
 */
data class QuickTextTemplate(
    val id: String,
    val label: String,
    val text: String
)

private object QuickTemplateStorage {
    private const val PREFS_NAME = "ers_quick_text_templates"
    private const val KEY_PREFIX = "templates_"

    fun load(
        context: Context,
        fieldKey: String,
        defaults: List<QuickTextTemplate>
    ): List<QuickTextTemplate> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PREFIX + fieldKey, null)
            ?: return defaults

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").trim()
                    val label = item.optString("label").trim()
                    val text = item.optString("text").trim()
                    if (label.isNotBlank() && text.isNotBlank()) {
                        add(
                            QuickTextTemplate(
                                id = id.ifBlank { UUID.randomUUID().toString() },
                                label = label,
                                text = text
                            )
                        )
                    }
                }
            }
        }.getOrDefault(defaults)
    }

    fun save(context: Context, fieldKey: String, templates: List<QuickTextTemplate>) {
        val array = JSONArray()
        templates.forEach { template ->
            array.put(
                JSONObject()
                    .put("id", template.id)
                    .put("label", template.label)
                    .put("text", template.text)
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PREFIX + fieldKey, array.toString())
            .apply()
    }
}

private object QuickTemplateDefaults {
    fun forField(fieldKey: String): List<QuickTextTemplate> {
        val entries: List<Pair<String, String>> = when (fieldKey) {
            "aar_summary" -> listOf(
                "Arrival assessment" to "Arrived on scene and completed an initial hazard and casualty assessment.",
                "Scene stabilized" to "The scene was secured and immediate hazards were controlled before operations continued.",
                "No casualties" to "No casualties were identified during the initial assessment."
            )

            "aar_actions" -> listOf(
                "Standard response" to "Established scene safety, coordinated responding units, and completed the assigned operational actions.",
                "Evacuation" to "Established an evacuation route, moved affected persons to the designated safe area, and completed accountability checks.",
                "Medical support" to "Performed initial patient assessment, provided indicated first aid, and coordinated transfer to the receiving medical team."
            )

            "aar_resources_used" -> listOf(
                "Standard equipment" to "Standard responder PPE, communication equipment, and assigned unit equipment were used.",
                "Medical supplies" to "Medical assessment equipment and first-aid supplies were used and accounted for.",
                "No special resources" to "No specialized equipment or additional resources were required."
            )

            "aar_agencies" -> listOf(
                "Single agency" to "Operations were completed by the assigned department without external agency support.",
                "Inter-agency" to "Operations were coordinated with Fire, EMS, Police, and incident command as applicable.",
                "Local support" to "Coordination was maintained with local officials and on-scene support personnel."
            )

            "aar_handoff" -> listOf(
                "To incident command" to "Operational status, outstanding hazards, and responder accountability were handed over to incident command.",
                "To medical team" to "Patient condition, interventions, and relevant observations were endorsed to the receiving medical team.",
                "No handoff" to "No formal handoff was required before clearing the scene."
            )

            "aar_safety" -> listOf(
                "No safety issues" to "No responder injuries, near misses, or unmitigated safety hazards were reported.",
                "Hazard controlled" to "A scene hazard was identified, communicated to all units, and controlled before operations continued.",
                "Near miss" to "A near-miss event occurred and was reported to the unit lead for follow-up review."
            )

            "aar_followup" -> listOf(
                "Equipment check" to "Inspect, clean, replenish, and document all equipment and supplies used during the response.",
                "Command follow-up" to "Incident command follow-up is required for outstanding operational actions and documentation.",
                "Responder welfare" to "Responder welfare monitoring and post-incident support should be completed."
            )

            "aar_lessons" -> listOf(
                "Communication" to "Maintain early, concise radio updates and confirm all critical instructions using closed-loop communication.",
                "Staging" to "Identify and communicate staging and access points earlier to reduce unit movement delays.",
                "Procedure effective" to "Current procedures were effective; continue reinforcing role assignment and accountability checks."
            )

            "resource_request_notes" -> listOf(
                "Immediate field use" to "Required for immediate field use. Please prioritize available stock and advise the expected release time.",
                "Replenishment" to "Requested to replenish supplies consumed during incident operations.",
                "Replacement" to "Requested as replacement for damaged, expired, or unserviceable equipment; supporting details are recorded in this request."
            )

            else -> listOf(
                "Standard note" to "Standard operational procedure was followed and the relevant details were documented."
            )
        }

        return entries.mapIndexed { index, (label, text) ->
            QuickTextTemplate(
                id = "default_${fieldKey}_$index",
                label = label,
                text = text
            )
        }
    }
}

/**
 * Displays tappable phrases and a manager where responders can add, edit,
 * delete, or restore templates. Applying a phrase is delegated to the caller so
 * it can append rather than overwrite existing documentation.
 */
@Composable
fun QuickTemplateBar(
    fieldKey: String,
    onApply: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (!enabled) return

    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val defaults = remember(fieldKey) { QuickTemplateDefaults.forField(fieldKey) }
    var templates by remember(fieldKey) {
        mutableStateOf(QuickTemplateStorage.load(context, fieldKey, defaults))
    }
    var showManager by remember { mutableStateOf(false) }
    var editorTarget by remember { mutableStateOf<QuickTextTemplate?>(null) }
    var createNew by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<QuickTextTemplate?>(null) }

    fun persist(updated: List<QuickTextTemplate>) {
        templates = updated
        QuickTemplateStorage.save(context, fieldKey, updated)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFF4C8A89),
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Quick templates",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { showManager = true },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
            ) {
                Text("Manage", fontSize = 11.sp, color = Color(0xFF4C8A89))
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items(templates, key = { it.id }) { template ->
                AssistChip(
                    onClick = { onApply(template.text) },
                    label = {
                        Text(
                            text = template.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        leadingIconContentColor = Color(0xFF4C8A89),
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(
                        1.dp,
                        Color(0xFF4C8A89).copy(alpha = 0.30f)
                    )
                )
            }
            item(key = "add_template") {
                AssistChip(
                    onClick = {
                        createNew = true
                        editorTarget = null
                    },
                    label = { Text("Add", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                )
            }
        }
    }

    if (showManager) {
        Dialog(
            onDismissRequest = { showManager = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Quick templates",
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            )
                            Text(
                                "Tap a template in the form to insert it.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(
                            onClick = {
                                createNew = true
                                editorTarget = null
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add template")
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    if (templates.isEmpty()) {
                        Text(
                            "No templates saved for this field.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(templates, key = { it.id }) { template ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                template.label,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                template.text,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(onClick = { editorTarget = template }) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit ${template.label}",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(onClick = { pendingDelete = template }) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Delete ${template.label}",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { persist(defaults) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Restore defaults", fontSize = 11.sp)
                        }
                        Button(
                            onClick = { showManager = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }

    if (createNew || editorTarget != null) {
        val existing = editorTarget
        TemplateEditorDialog(
            initialLabel = existing?.label.orEmpty(),
            initialText = existing?.text.orEmpty(),
            title = if (existing == null) "Add quick template" else "Edit quick template",
            onDismiss = {
                createNew = false
                editorTarget = null
            },
            onSave = { label, text ->
                val updatedTemplate = QuickTextTemplate(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    label = label,
                    text = text
                )
                val updated = if (existing == null) {
                    templates + updatedTemplate
                } else {
                    templates.map { if (it.id == existing.id) updatedTemplate else it }
                }
                persist(updated)
                createNew = false
                editorTarget = null
            }
        )
    }

    pendingDelete?.let { template ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete template?") },
            text = { Text("\"${template.label}\" will be removed from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        persist(templates.filterNot { it.id == template.id })
                        pendingDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TemplateEditorDialog(
    initialLabel: String,
    initialText: String,
    title: String,
    onDismiss: () -> Unit,
    onSave: (label: String, text: String) -> Unit
) {
    var label by remember(initialLabel) { mutableStateOf(initialLabel) }
    var text by remember(initialText) { mutableStateOf(initialText) }
    val valid = label.trim().isNotBlank() && text.trim().isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(32) },
                    label = { Text("Template name") },
                    placeholder = { Text("Example: Scene stabilized") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(1000) },
                    label = { Text("Template text") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(label.trim(), text.trim()) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
