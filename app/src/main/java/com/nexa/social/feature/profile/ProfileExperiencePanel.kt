package com.nexa.social.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.nexa.social.data.remote.SupabaseProfileExperienceDataSource
import kotlinx.coroutines.launch
import java.time.LocalDate

private data class LanguageChoice(val code: String, val name: String, val flag: String)

private val profileLanguageChoices = listOf(
    LanguageChoice("ar", "العربية", "🇾🇪"),
    LanguageChoice("en", "الإنجليزية", "🇬🇧"),
    LanguageChoice("fr", "الفرنسية", "🇫🇷"),
    LanguageChoice("es", "الإسبانية", "🇪🇸"),
    LanguageChoice("de", "الألمانية", "🇩🇪"),
    LanguageChoice("tr", "التركية", "🇹🇷"),
    LanguageChoice("ru", "الروسية", "🇷🇺"),
    LanguageChoice("zh", "الصينية", "🇨🇳"),
    LanguageChoice("ja", "اليابانية", "🇯🇵"),
    LanguageChoice("ko", "الكورية", "🇰🇷"),
    LanguageChoice("it", "الإيطالية", "🇮🇹"),
    LanguageChoice("hi", "الهندية", "🇮🇳"),
    LanguageChoice("pt", "البرتغالية", "🇵🇹"),
    LanguageChoice("ur", "الأردية", "🇵🇰"),
    LanguageChoice("fa", "الفارسية", "🇮🇷")
)

private val languageLevels = listOf(
    "NATIVE" to "لغة أم · 100%",
    "FLUENT" to "طليق · 90%",
    "ADVANCED" to "متقدم · 75%",
    "INTERMEDIATE" to "متوسط · 50%",
    "BEGINNER" to "مبتدئ · 25%"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileExperiencePanel(
    profileId: String,
    isOwner: Boolean,
    dataSource: SupabaseProfileExperienceDataSource
) {
    val timeline = remember(profileId) { mutableStateListOf<JsonObject>() }
    val languages = remember(profileId) { mutableStateListOf<JsonObject>() }
    val scope = rememberCoroutineScope()
    var showEventDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var eventTitle by remember { mutableStateOf("") }
    var eventDescription by remember { mutableStateOf("") }
    var eventDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var eventType by remember { mutableStateOf("EDUCATION") }
    var selectedLanguage by remember { mutableStateOf(profileLanguageChoices.first()) }
    var selectedLevel by remember { mutableStateOf("BEGINNER") }
    var expandedLevel by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            timeline.clear()
            timeline.addAll(dataSource.loadRows("nexa_profile_timeline", profileId))
            languages.clear()
            languages.addAll(dataSource.loadRows("nexa_profile_languages", profileId))
        }
    }

    LaunchedEffect(profileId, dataSource) { refresh() }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timeline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("رحلتي الزمنية", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (isOwner) IconButton(onClick = { eventTitle = ""; eventDescription = ""; eventDate = LocalDate.now().toString(); eventType = "EDUCATION"; showEventDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "إضافة حدث")
                    }
                }
                if (timeline.isEmpty()) {
                    Text(if (isOwner) "أضف محطاتك التعليمية والمهنية والشخصية." else "لا توجد أحداث ظاهرة في الرحلة بعد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    timeline.sortedWith(compareByDescending<JsonObject> { it.get("is_pinned")?.asBoolean == true }.thenByDescending { it.get("event_date")?.asString.orEmpty() }).forEach { row ->
                        val type = row.get("event_type")?.asString.orEmpty()
                        val marker = when (type) { "EDUCATION" -> "🎓"; "WORK" -> "💼"; "ACHIEVEMENT" -> "🏆"; "TRAVEL" -> "✈️"; else -> "💍" }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(marker)
                            Column(Modifier.weight(1f)) {
                                Text(row.get("title")?.asString.orEmpty(), style = MaterialTheme.typography.titleSmall)
                                Text(row.get("event_date")?.asString.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                row.get("description")?.asString?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                            if (isOwner) IconButton(onClick = {
                                val id = row.get("id")?.asString
                                if (id != null) scope.launch { dataSource.deleteRow("nexa_profile_timeline", id); refresh() }
                            }) { Icon(Icons.Default.Delete, contentDescription = "حذف الحدث", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("اللغات", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (isOwner) IconButton(onClick = { selectedLanguage = profileLanguageChoices.first(); selectedLevel = "BEGINNER"; showLanguageDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "إضافة لغة")
                    }
                }
                if (languages.isEmpty()) {
                    Text(if (isOwner) "أضف اللغات التي تتحدثها ومستوى إتقانك." else "لا توجد لغات ظاهرة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    languages.sortedWith(compareByDescending<JsonObject> { it.get("is_native")?.asBoolean == true }.thenBy { it.get("sort_order")?.asInt ?: 0 }).forEach { row ->
                        val level = row.get("proficiency")?.asString ?: "BEGINNER"
                        val percent = when (level) { "NATIVE" -> 100; "FLUENT" -> 90; "ADVANCED" -> 75; "INTERMEDIATE" -> 50; else -> 25 }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(row.get("flag_emoji")?.asString.orEmpty().ifBlank { "🌐" })
                            Column(Modifier.weight(1f)) {
                                Text(row.get("language_name")?.asString.orEmpty(), style = MaterialTheme.typography.titleSmall)
                                Text(languageLevels.firstOrNull { it.first == level }?.second.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                androidx.compose.material3.LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                            }
                            if (isOwner) IconButton(onClick = {
                                val id = row.get("id")?.asString
                                if (id != null) scope.launch { dataSource.deleteRow("nexa_profile_languages", id); refresh() }
                            }) { Icon(Icons.Default.Delete, contentDescription = "حذف اللغة", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }

    if (showEventDialog) {
        AlertDialog(
            onDismissRequest = { if (!busy) showEventDialog = false },
            title = { Text("إضافة حدث للرحلة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(eventTitle, { eventTitle = it.take(120) }, label = { Text("عنوان الحدث") }, singleLine = true)
                    OutlinedTextField(eventDate, { eventDate = it.take(10) }, label = { Text("التاريخ YYYY-MM-DD") }, singleLine = true)
                    OutlinedTextField(eventDescription, { eventDescription = it.take(600) }, label = { Text("وصف مختصر") })
                    Text("نوع الحدث", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("EDUCATION" to "تعليم", "WORK" to "عمل", "ACHIEVEMENT" to "إنجاز", "TRAVEL" to "سفر", "PERSONAL" to "شخصي").forEach { (code,label) ->
                            FilterChip(selected = eventType == code, onClick = { eventType = code }, label = { Text(label) })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && eventTitle.isNotBlank() && runCatching { LocalDate.parse(eventDate); true }.getOrDefault(false), onClick = {
                    busy = true
                    scope.launch {
                        val body = JsonObject().apply {
                            addProperty("title", eventTitle.trim()); addProperty("event_date", eventDate)
                            addProperty("event_type", eventType); addProperty("description", eventDescription.trim())
                        }
                        if (dataSource.saveRow("nexa_profile_timeline", body)) { showEventDialog = false; refresh() }
                        busy = false
                    }
                }) { Text(if (busy) "جارٍ الحفظ…" else "حفظ") }
            },
            dismissButton = { TextButton(onClick = { showEventDialog = false }) { Text("إلغاء") } }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { if (!busy) showLanguageDialog = false },
            title = { Text("إضافة لغة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اختر اللغة", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        profileLanguageChoices.forEach { choice ->
                            FilterChip(selected = selectedLanguage.code == choice.code, onClick = { selectedLanguage = choice }, label = { Text("${choice.flag} ${choice.name}") })
                        }
                    }
                    Text("مستوى الإتقان", style = MaterialTheme.typography.labelMedium)
                    BoxForLevel(
                        selected = languageLevels.first { it.first == selectedLevel }.second,
                        expanded = expandedLevel,
                        onExpanded = { expandedLevel = it },
                        onSelect = { selectedLevel = it; expandedLevel = false }
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val body = JsonObject().apply {
                            addProperty("language_code", selectedLanguage.code); addProperty("language_name", selectedLanguage.name)
                            addProperty("flag_emoji", selectedLanguage.flag); addProperty("proficiency", selectedLevel)
                            addProperty("is_native", selectedLevel == "NATIVE")
                        }
                        if (dataSource.saveRow("nexa_profile_languages", body)) { showLanguageDialog = false; refresh() }
                        busy = false
                    }
                }) { Text(if (busy) "جارٍ الحفظ…" else "إضافة") }
            },
            dismissButton = { TextButton(onClick = { showLanguageDialog = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun BoxForLevel(
    selected: String,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    onSelect: (String) -> Unit
) {
    androidx.compose.foundation.layout.Box {
        TextButton(onClick = { onExpanded(true) }) { Text(selected) }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpanded(false) }) {
            languageLevels.forEach { (code,label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(code) })
            }
        }
    }
}
