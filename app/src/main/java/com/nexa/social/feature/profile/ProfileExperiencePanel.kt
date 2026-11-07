package com.nexa.social.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Recommend
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.nexa.social.data.remote.SupabaseProfileExperienceDataSource
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

private data class LanguageChoice(val code: String, val name: String, val flag: String)
private val profileLanguageChoices = listOf(
    LanguageChoice("ar", "العربية", "🇾🇪"), LanguageChoice("en", "الإنجليزية", "🇬🇧"),
    LanguageChoice("fr", "الفرنسية", "🇫🇷"), LanguageChoice("es", "الإسبانية", "🇪🇸"),
    LanguageChoice("de", "الألمانية", "🇩🇪"), LanguageChoice("tr", "التركية", "🇹🇷"),
    LanguageChoice("ru", "الروسية", "🇷🇺"), LanguageChoice("zh", "الصينية", "🇨🇳"),
    LanguageChoice("ja", "اليابانية", "🇯🇵"), LanguageChoice("ko", "الكورية", "🇰🇷"),
    LanguageChoice("it", "الإيطالية", "🇮🇹"), LanguageChoice("hi", "الهندية", "🇮🇳"),
    LanguageChoice("pt", "البرتغالية", "🇵🇹"), LanguageChoice("ur", "الأردية", "🇵🇰"),
    LanguageChoice("fa", "الفارسية", "🇮🇷")
)
private val languageLevels = listOf(
    "NATIVE" to "لغة أم · 100%", "FLUENT" to "طليق · 90%", "ADVANCED" to "متقدم · 75%",
    "INTERMEDIATE" to "متوسط · 50%", "BEGINNER" to "مبتدئ · 25%"
)
private val statusChoices = listOf(
    "AVAILABLE" to "🟢 متاح للتعاون", "LOOKING" to "🔵 أبحث عن فرصة",
    "BUSY" to "🟣 مشغول حالياً", "VACATION" to "🟡 في إجازة",
    "UNAVAILABLE" to "🟠 غير متاح مؤقتاً", "IDEA" to "💭 أفكر في فكرة جديدة",
    "PROJECT" to "🎯 أعمل على مشروع", "LEARNING" to "📚 أتعلم شيئاً جديداً",
    "CONSULTING" to "🌟 متاح للاستشارات", "REST" to "😴 في راحة"
)
private val favoriteCategories = listOf(
    "BOOK" to "📚 كتاب", "MOVIE" to "🎬 فيلم", "MUSIC" to "🎵 موسيقى",
    "FOOD" to "🍜 طعام", "GAME" to "🎮 لعبة", "DESTINATION" to "✈️ وجهة",
    "INSPIRATION" to "🎨 ملهم", "QUOTE" to "💡 اقتباس"
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
    val credentials = remember(profileId) { mutableStateListOf<JsonObject>() }
    val statuses = remember(profileId) { mutableStateListOf<JsonObject>() }
    val statusHistory = remember(profileId) { mutableStateListOf<JsonObject>() }
    val testimonials = remember(profileId) { mutableStateListOf<JsonObject>() }
    val favorites = remember(profileId) { mutableStateListOf<JsonObject>() }
    val achievements = remember(profileId) { mutableStateListOf<JsonObject>() }
    val analytics = remember(profileId) { mutableStateListOf<JsonObject>() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }

    var eventTitle by remember { mutableStateOf("") }
    var eventDate by remember { mutableStateOf("") }
    var eventDescription by remember { mutableStateOf("") }
    var eventType by remember { mutableStateOf("EDUCATION") }
    var selectedLanguage by remember { mutableStateOf(profileLanguageChoices.first()) }
    var selectedLevel by remember { mutableStateOf("BEGINNER") }
    var languagePrivate by remember { mutableStateOf(false) }

    var credentialTitle by remember { mutableStateOf("") }
    var credentialIssuer by remember { mutableStateOf("") }
    var credentialType by remember { mutableStateOf("CERTIFICATE") }
    var credentialCategory by remember { mutableStateOf("ACADEMIC") }
    var credentialStart by remember { mutableStateOf("") }
    var credentialEnd by remember { mutableStateOf("") }
    var credentialCurrent by remember { mutableStateOf(false) }
    var credentialLocation by remember { mutableStateOf("") }
    var credentialDescription by remember { mutableStateOf("") }
    var credentialVerifyUrl by remember { mutableStateOf("") }
    var credentialDocumentUrl by remember { mutableStateOf("") }
    var credentialLogoUrl by remember { mutableStateOf("") }

    var statusCode by remember { mutableStateOf("AVAILABLE") }
    var statusCustom by remember { mutableStateOf("") }
    var statusEmoji by remember { mutableStateOf("💭") }
    var statusDuration by remember { mutableStateOf("24") }
    var statusVisible by remember { mutableStateOf(true) }
    var statusNotifyExpiry by remember { mutableStateOf(false) }

    var testimonialName by remember { mutableStateOf("") }
    var testimonialTitle by remember { mutableStateOf("") }
    var testimonialCompany by remember { mutableStateOf("") }
    var testimonialBody by remember { mutableStateOf("") }
    var testimonialRating by remember { mutableStateOf("5") }
    var testimonialLinkedIn by remember { mutableStateOf("") }
    var requestEmail by remember { mutableStateOf("") }
    var requestToken by remember { mutableStateOf("") }

    var favoriteTitle by remember { mutableStateOf("") }
    var favoriteCreator by remember { mutableStateOf("") }
    var favoriteCategory by remember { mutableStateOf("BOOK") }
    var favoriteYear by remember { mutableStateOf("") }
    var favoriteImage by remember { mutableStateOf("") }
    var favoriteUrl by remember { mutableStateOf("") }
    var favoriteQuote by remember { mutableStateOf("") }
    var favoriteDescription by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch {
            timeline.clear(); timeline.addAll(dataSource.loadRows("nexa_profile_timeline", profileId))
            languages.clear(); languages.addAll(dataSource.loadRows("nexa_profile_languages", profileId))
            credentials.clear(); credentials.addAll(dataSource.loadRows("nexa_profile_credentials", profileId))
            statuses.clear(); statuses.addAll(dataSource.loadRows("nexa_profile_status", profileId))
            if (isOwner) {
                val expired = statuses.firstOrNull()?.get("expires_at")?.takeIf { !it.isJsonNull }?.asString
                    ?.let { runCatching { Instant.parse(it).isBefore(Instant.now()) }.getOrDefault(false) } == true
                if (expired) {
                    dataSource.deleteRow("nexa_profile_status", profileId)
                    statuses.clear()
                    statuses.addAll(dataSource.loadRows("nexa_profile_status", profileId))
                }
            }
            testimonials.clear(); testimonials.addAll(dataSource.loadRows("nexa_profile_testimonials", profileId))
            favorites.clear(); favorites.addAll(dataSource.loadRows("nexa_profile_favorites", profileId))
            achievements.clear(); achievements.addAll(dataSource.loadRows("nexa_profile_achievements", profileId))
            if (isOwner) {
                statusHistory.clear(); statusHistory.addAll(dataSource.loadRows("nexa_profile_status_history", profileId))
                analytics.clear(); analytics.addAll(dataSource.loadRows("nexa_profile_analytics_events", profileId))
            }
        }
    }

    LaunchedEffect(profileId, dataSource, isOwner) { refresh() }

    fun clearCredentialForm() {
        editingId = null; credentialTitle = ""; credentialIssuer = ""; credentialType = "CERTIFICATE"
        credentialCategory = "ACADEMIC"; credentialStart = ""; credentialEnd = ""; credentialCurrent = false
        credentialLocation = ""; credentialDescription = ""; credentialVerifyUrl = ""
        credentialDocumentUrl = ""; credentialLogoUrl = ""
    }
    fun clearFavoriteForm() {
        editingId = null; favoriteTitle = ""; favoriteCreator = ""; favoriteCategory = "BOOK"
        favoriteYear = ""; favoriteImage = ""; favoriteUrl = ""; favoriteQuote = ""; favoriteDescription = ""
    }
    fun openCredential(row: JsonObject?) {
        clearCredentialForm()
        if (row != null) {
            editingId = row.get("id")?.asString
            credentialTitle = row.get("title")?.asString.orEmpty()
            credentialIssuer = row.get("issuer")?.asString.orEmpty()
            credentialType = row.get("record_type")?.asString ?: "CERTIFICATE"
            credentialCategory = row.get("category")?.asString ?: "ACADEMIC"
            credentialStart = row.get("start_date")?.asString.orEmpty()
            credentialEnd = row.get("end_date")?.asString.orEmpty()
            credentialCurrent = row.get("is_current")?.asBoolean == true
            credentialLocation = row.get("location")?.asString.orEmpty()
            credentialDescription = row.get("description")?.asString.orEmpty()
            credentialVerifyUrl = row.get("verification_url")?.asString.orEmpty()
            credentialDocumentUrl = row.get("document_url")?.asString.orEmpty()
            credentialLogoUrl = row.get("organization_logo_url")?.asString.orEmpty()
        }
        dialog = "credential"
    }
    fun openFavorite(row: JsonObject?) {
        clearFavoriteForm()
        if (row != null) {
            editingId = row.get("id")?.asString
            favoriteTitle = row.get("title")?.asString.orEmpty()
            favoriteCreator = row.get("creator")?.asString.orEmpty()
            favoriteCategory = row.get("category")?.asString ?: "BOOK"
            favoriteYear = row.get("year")?.asString.orEmpty()
            favoriteImage = row.get("image_url")?.asString.orEmpty()
            favoriteUrl = row.get("external_url")?.asString.orEmpty()
            favoriteQuote = row.get("quote")?.asString.orEmpty()
            favoriteDescription = row.get("description")?.asString.orEmpty()
        }
        dialog = "favorite"
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Professional timeline
        ExperienceCard("🕰️ رحلتي الزمنية", isOwner, { eventTitle = ""; eventDate = ""; eventDescription = ""; eventType = "EDUCATION"; dialog = "timeline" }) {
            if (timeline.isEmpty()) Text("لا توجد أحداث ظاهرة بعد.", style = MaterialTheme.typography.bodySmall)
            timeline.sortedWith(compareByDescending<JsonObject> { it.get("is_pinned")?.asBoolean == true }.thenByDescending { it.get("event_date")?.asString.orEmpty() }).forEach { row ->
                val icon = when (row.get("event_type")?.asString) { "EDUCATION" -> "🎓"; "WORK" -> "💼"; "ACHIEVEMENT" -> "🏆"; "TRAVEL" -> "✈️"; else -> "💍" }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(icon)
                    Column(Modifier.weight(1f)) {
                        Text(row.get("title")?.asString.orEmpty(), style = MaterialTheme.typography.titleSmall)
                        Text(row.get("event_date")?.asString.orEmpty(), style = MaterialTheme.typography.labelSmall)
                        Text(row.get("description")?.asString.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                    if (isOwner) {
                        IconButton(onClick = {
                            val id = row.get("id")?.asString
                            if (id != null) scope.launch { dataSource.deleteRow("nexa_profile_timeline", id); refresh() }
                        }) { Icon(Icons.Default.Delete, contentDescription = "حذف") }
                    }
                }
            }
        }

        // Languages
        ExperienceCard("🌍 اللغات", isOwner, { selectedLanguage = profileLanguageChoices.first(); selectedLevel = "BEGINNER"; languagePrivate = false; dialog = "language" }) {
            languages.sortedWith(compareByDescending<JsonObject> { it.get("is_native")?.asBoolean == true }.thenBy { it.get("sort_order")?.asInt ?: 0 })
                .filter { isOwner || it.get("is_private")?.asBoolean != true }
                .forEach { row ->
                    val level = row.get("proficiency")?.asString ?: "BEGINNER"
                    val percent = when (level) { "NATIVE" -> 100; "FLUENT" -> 90; "ADVANCED" -> 75; "INTERMEDIATE" -> 50; else -> 25 }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(row.get("flag_emoji")?.asString.orEmpty().ifBlank { "🌐" })
                        Column(Modifier.weight(1f)) {
                            Text(row.get("language_name")?.asString.orEmpty(), style = MaterialTheme.typography.titleSmall)
                            Text(languageLevels.firstOrNull { it.first == level }?.second.orEmpty(), style = MaterialTheme.typography.labelSmall)
                            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                        }
                        if (isOwner) IconButton(onClick = {
                            val id = row.get("id")?.asString
                            if (id != null) scope.launch { dataSource.deleteRow("nexa_profile_languages", id); refresh() }
                        }) { Icon(Icons.Default.Delete, contentDescription = "حذف اللغة") }
                    }
                }
        }

        // Certificates and experience
        ExperienceCard("🏅 الشهادات والخبرات", isOwner, { openCredential(null) }) {
            if (credentials.isEmpty()) Text("أضف شهاداتك وخبراتك المهنية لعرضها هنا.", style = MaterialTheme.typography.bodySmall)
            credentials.filter { isOwner || it.get("is_visible")?.asBoolean != false }
                .sortedWith(compareByDescending<JsonObject> { it.get("is_pinned")?.asBoolean == true }.thenByDescending { it.get("end_date")?.asString.orEmpty() })
                .forEach { row ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text((if (row.get("record_type")?.asString == "EXPERIENCE") "💼 " else "🎓 ") + row.get("title")?.asString.orEmpty(), style = MaterialTheme.typography.titleSmall)
                            Text(row.get("issuer")?.asString.orEmpty(), style = MaterialTheme.typography.bodySmall)
                            Text(
                                (row.get("start_date")?.asString.orEmpty()) + " – " +
                                    if (row.get("is_current")?.asBoolean == true) "حتى الآن" else row.get("end_date")?.asString.orEmpty(),
                                style = MaterialTheme.typography.labelSmall
                            )
                            if (row.get("is_verified")?.asBoolean == true) Text("✓ موثّقة", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                            row.get("description")?.asString?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            row.get("verification_url")?.asString?.takeIf { it.isNotBlank() }?.let { Text("رابط التحقق مرفق", style = MaterialTheme.typography.labelSmall) }
                        }
                        if (isOwner) {
                            IconButton(onClick = { openCredential(row) }) { Icon(Icons.Default.Edit, contentDescription = "تعديل") }
                            IconButton(onClick = {
                                val id = row.get("id")?.asString ?: return@IconButton
                                val patch = JsonObject().apply { addProperty("is_visible", row.get("is_visible")?.asBoolean != true) }
                                scope.launch { dataSource.updateRow("nexa_profile_credentials", id, patch); refresh() }
                            }) { Text(if (row.get("is_visible")?.asBoolean == false) "🙈" else "👁") }
                            IconButton(onClick = {
                                val id = row.get("id")?.asString ?: return@IconButton
                                val patch = JsonObject().apply { addProperty("is_pinned", row.get("is_pinned")?.asBoolean != true) }
                                scope.launch {
                                    val ok = dataSource.updateRow("nexa_profile_credentials", id, patch)
                                    if (!ok) message = "يمكن تثبيت شهادة أو خبرة أساسية واحدة فقط."
                                    refresh()
                                }
                            }) { Text(if (row.get("is_pinned")?.asBoolean == true) "📌" else "☆") }
                            IconButton(onClick = {
                                val id = row.get("id")?.asString
                                if (id != null) scope.launch { dataSource.deleteRow("nexa_profile_credentials", id); refresh() }
                            }) { Icon(Icons.Default.Delete, contentDescription = "حذف") }
                        }
                    }
                }
            if (isOwner) Text("شارة التوثيق لا تُمنح ذاتياً؛ يلزم تحقق إداري من المستند أو الجهة.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Status
        ExperienceCard("💭 حالتي الآن", isOwner, { statusCode = "AVAILABLE"; statusCustom = ""; statusDuration = "24"; statusVisible = true; dialog = "status" }) {
            val current = statuses.firstOrNull()?.takeIf {
                val expiry = it.get("expires_at")?.takeIf { v -> !v.isJsonNull }?.asString
                expiry.isNullOrBlank() || runCatching { Instant.parse(expiry).isAfter(Instant.now()) }.getOrDefault(false)
            }
            if (current == null) Text("لا توجد حالة نشطة.", style = MaterialTheme.typography.bodySmall)
            else {
                val code = current.get("status_code")?.asString.orEmpty()
                Text(
                    "${current.get("emoji")?.asString.orEmpty()} " +
                        (current.get("custom_text")?.asString?.takeIf { it.isNotBlank() }
                            ?: statusChoices.firstOrNull { it.first == code }?.second ?: code),
                    style = MaterialTheme.typography.titleSmall
                )
                current.get("expires_at")?.takeIf { !it.isJsonNull }?.asString?.let { Text("تنتهي: $it", style = MaterialTheme.typography.labelSmall) }
            }
            if (isOwner && current != null) TextButton(onClick = {
                scope.launch { dataSource.deleteRow("nexa_profile_status", profileId); refresh() }
            }) { Text("مسح الحالة") }
            if (isOwner && statusHistory.isNotEmpty()) Text("سجل الحالات: ${statusHistory.size}", style = MaterialTheme.typography.labelSmall)
        }

        // Recommendations
        ExperienceCard("💬 التوصيات", isOwner, {
            testimonialName = ""; testimonialTitle = ""; testimonialCompany = ""; testimonialBody = ""
            testimonialRating = "5"; testimonialLinkedIn = ""; editingId = null; dialog = "testimonial"
        }) {
            val visible = testimonials.filter { (isOwner || it.get("is_visible")?.asBoolean != false) && (isOwner || it.get("is_approved")?.asBoolean != false) }
            val rated = visible.mapNotNull { it.get("rating")?.takeIf { v -> !v.isJsonNull }?.asInt }
            if (rated.isNotEmpty()) Text("⭐ ${"%.1f".format(rated.average())} من ${rated.size} توصية", style = MaterialTheme.typography.titleSmall)
            if (visible.isEmpty()) Text("لا توجد توصيات بعد.", style = MaterialTheme.typography.bodySmall)
            visible.forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.get("author_name")?.asString.orEmpty(), style = MaterialTheme.typography.titleSmall)
                        Text(row.get("body")?.asString.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        Text("⭐".repeat((row.get("rating")?.asInt ?: 0).coerceIn(0, 5)), style = MaterialTheme.typography.labelSmall)
                        if (row.get("is_verified")?.asBoolean == true) Text("✓ توصية مؤكدة من صاحبها", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    }
                    if (isOwner) {
                        IconButton(onClick = {
                            val id = row.get("id")?.asString
                            val patch = JsonObject().apply { addProperty("is_visible", row.get("is_visible")?.asBoolean != true) }
                            if (id != null) scope.launch { dataSource.updateRow("nexa_profile_testimonials", id, patch); refresh() }
                        }) { Text(if (row.get("is_visible")?.asBoolean == false) "👁" else "🙈") }
                        IconButton(onClick = {
                            val id = row.get("id")?.asString
                            if (id != null) scope.launch { dataSource.deleteRow("nexa_profile_testimonials", id); refresh() }
                        }) { Icon(Icons.Default.Delete, contentDescription = "حذف") }
                    }
                }
            }
            if (isOwner) TextButton(onClick = { requestEmail = ""; dialog = "request" }) { Text("✉️ إنشاء طلب توصية") }
            else TextButton(onClick = { requestToken = ""; testimonialName = ""; testimonialTitle = ""; testimonialCompany = ""; testimonialBody = ""; testimonialRating = "5"; dialog = "submit" }) { Text("لديك رمز طلب؟ أرسل توصية") }
        }

        // Achievements
        ExperienceCard("🏆 الإنجازات والشارات", isOwner, null) {
            achievements.filter { isOwner || it.get("is_visible")?.asBoolean != false }
                .sortedWith(compareByDescending<JsonObject> { it.get("is_featured")?.asBoolean == true }.thenByDescending { it.get("unlocked_at")?.takeIf { v -> !v.isJsonNull } != null })
                .forEach { row ->
                    val target = row.get("target")?.asInt ?: 1
                    val progress = row.get("progress")?.asInt ?: 0
                    val unlocked = row.get("unlocked_at")?.takeIf { !it.isJsonNull } != null
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(achievementTitle(row.get("achievement_code")?.asString.orEmpty()), style = MaterialTheme.typography.titleSmall)
                            LinearProgressIndicator(progress = { (progress.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            Text(if (unlocked) "مفتوحة ✓" else "$progress / $target", style = MaterialTheme.typography.labelSmall)
                        }
                        if (isOwner) {
                            IconButton(onClick = {
                                val code = row.get("achievement_code")?.asString ?: return@IconButton
                                val patch = JsonObject().apply { addProperty("is_featured", row.get("is_featured")?.asBoolean != true) }
                                scope.launch { dataSource.updateRow("nexa_profile_achievements", code, patch); refresh() }
                            }) { Text(if (row.get("is_featured")?.asBoolean == true) "📌" else "☆") }
                            IconButton(onClick = {
                                val code = row.get("achievement_code")?.asString ?: return@IconButton
                                val patch = JsonObject().apply { addProperty("is_visible", row.get("is_visible")?.asBoolean != true) }
                                scope.launch { dataSource.updateRow("nexa_profile_achievements", code, patch); refresh() }
                            }) { Text(if (row.get("is_visible")?.asBoolean == false) "🙈" else "👁") }
                        }
                    }
                }
        }

        // Private analytics dashboard
        if (isOwner) {
            ExperienceCard("📊 إحصائيات ملفك (خاصة بك)", false, {}) {
                val views = analytics.count { it.get("event_type")?.asString == "PROFILE_VIEW" }
                val skills = analytics.count { it.get("event_type")?.asString == "SKILL_CLICK" }
                val links = analytics.count { it.get("event_type")?.asString == "SOCIAL_LINK_CLICK" }
                val downloads = analytics.count { it.get("event_type")?.asString == "CV_DOWNLOAD" }
                val qr = analytics.count { it.get("event_type")?.asString == "QR_SCAN" }
                Text("👁️ مشاهدات الملف: $views")
                Text("🎯 نقرات المهارات: $skills")
                Text("🔗 نقرات الروابط: $links")
                Text("📄 تحميلات السيرة: $downloads")
                Text("📱 مسحات QR: $qr")
                Text("💬 التوصيات المستلمة: ${testimonials.size}")
                Text("البيانات المعروضة هي الأحداث المسجلة منذ تفعيل التتبع.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Favorites
        ExperienceCard("🎬 مفضلاتي", isOwner, { openFavorite(null) }) {
            favorites.filter { isOwner || it.get("is_visible")?.asBoolean != false }
                .sortedWith(compareByDescending<JsonObject> { it.get("is_pinned")?.asBoolean == true }.thenBy { it.get("sort_order")?.asInt ?: 0 })
                .forEach { row ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            val category = favoriteCategories.firstOrNull { it.first == row.get("category")?.asString }?.second.orEmpty()
                            Text("$category · ${row.get("title")?.asString.orEmpty()}", style = MaterialTheme.typography.titleSmall)
                            Text(row.get("creator")?.asString.orEmpty(), style = MaterialTheme.typography.bodySmall)
                            Text(row.get("quote")?.asString.orEmpty(), style = MaterialTheme.typography.labelSmall)
                        }
                        if (isOwner) {
                            IconButton(onClick = { openFavorite(row) }) { Icon(Icons.Default.Edit, contentDescription = "تعديل") }
                            IconButton(onClick = {
                                val id = row.get("id")?.asString ?: return@IconButton
                                val patch = JsonObject().apply { addProperty("is_visible", row.get("is_visible")?.asBoolean != true) }
                                scope.launch { dataSource.updateRow("nexa_profile_favorites", id, patch); refresh() }
                            }) { Text(if (row.get("is_visible")?.asBoolean == false) "🙈" else "👁") }
                            IconButton(onClick = {
                                val id = row.get("id")?.asString ?: return@IconButton
                                val patch = JsonObject().apply { addProperty("is_pinned", row.get("is_pinned")?.asBoolean != true) }
                                scope.launch {
                                    val ok = dataSource.updateRow("nexa_profile_favorites", id, patch)
                                    if (!ok) message = "يمكن تثبيت 3 مفضلات كحد أقصى."
                                    refresh()
                                }
                            }) { Text(if (row.get("is_pinned")?.asBoolean == true) "📌" else "☆") }
                            IconButton(onClick = {
                                val id = row.get("id")?.asString
                                if (id != null) scope.launch { dataSource.deleteRow("nexa_profile_favorites", id); refresh() }
                            }) { Icon(Icons.Default.Delete, contentDescription = "حذف") }
                        }
                    }
                }
        }
    }

    fun runSave(block: suspend () -> Boolean) {
        busy = true
        scope.launch {
            val ok = runCatching { block() }.getOrDefault(false)
            if (!ok) message = "تعذر الحفظ. تحقق من البيانات واتصال Supabase."
            else { dialog = ""; refresh() }
            busy = false
        }
    }

    if (message != null) AlertDialog(
        onDismissRequest = { message = null },
        title = { Text("Nexa") },
        text = { Text(message.orEmpty()) },
        confirmButton = { TextButton(onClick = { message = null }) { Text("حسناً") } }
    )

    when (dialog) {
        "timeline" -> FormDialog("إضافة محطة للرحلة", { dialog = "" }, {
            OutlinedTextField(eventTitle, { eventTitle = it.take(120) }, label = { Text("عنوان الحدث") })
            OutlinedTextField(eventDate, { eventDate = it.take(10) }, label = { Text("التاريخ YYYY-MM-DD") })
            OutlinedTextField(eventDescription, { eventDescription = it.take(600) }, label = { Text("الوصف") })
            ChoiceChips(listOf("EDUCATION" to "تعليم", "WORK" to "عمل", "ACHIEVEMENT" to "إنجاز", "TRAVEL" to "سفر", "PERSONAL" to "شخصي"), eventType) { eventType = it }
        }, !busy && eventTitle.isNotBlank() && eventDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")), "حفظ") {
            runSave { JsonObject().apply { addProperty("title", eventTitle.trim()); addProperty("event_date", eventDate); addProperty("description", eventDescription.trim()); addProperty("event_type", eventType) }.let { dataSource.saveRow("nexa_profile_timeline", it) } }
        }
        "language" -> FormDialog("إضافة لغة", { dialog = "" }, {
            ChoiceChips(profileLanguageChoices.map { it.code to "${it.flag} ${it.name}" }, selectedLanguage.code) { code -> selectedLanguage = profileLanguageChoices.first { it.code == code } }
            ChoiceChips(languageLevels, selectedLevel) { selectedLevel = it }
            TextButton(onClick = { languagePrivate = !languagePrivate }) { Text(if (languagePrivate) "🔒 خاصة (اضغط للإظهار)" else "🌐 ظاهرة (اضغط للإخفاء)") }
        }, !busy, "إضافة") {
            runSave {
                val body = JsonObject().apply {
                    addProperty("language_code", selectedLanguage.code); addProperty("language_name", selectedLanguage.name)
                    addProperty("flag_emoji", selectedLanguage.flag); addProperty("proficiency", selectedLevel)
                    addProperty("is_native", selectedLevel == "NATIVE"); addProperty("is_private", languagePrivate)
                }
                dataSource.saveRow("nexa_profile_languages", body)
            }
        }
        "credential" -> FormDialog(if (editingId == null) "إضافة شهادة أو خبرة" else "تعديل الشهادة أو الخبرة", { dialog = "" }, {
            OutlinedTextField(credentialTitle, { credentialTitle = it.take(160) }, label = { Text("الشهادة / المسمى الوظيفي") })
            OutlinedTextField(credentialIssuer, { credentialIssuer = it.take(160) }, label = { Text("الجهة / الشركة") })
            ChoiceChips(listOf("CERTIFICATE" to "🎓 شهادة", "EXPERIENCE" to "💼 خبرة"), credentialType) { credentialType = it }
            ChoiceChips(listOf("ACADEMIC" to "أكاديمي", "PROFESSIONAL" to "مهني", "VOLUNTEER" to "تطوعي", "PERSONAL" to "شخصي"), credentialCategory) { credentialCategory = it }
            OutlinedTextField(credentialStart, { credentialStart = it.take(10) }, label = { Text("تاريخ البداية YYYY-MM-DD") })
            OutlinedTextField(credentialEnd, { credentialEnd = it.take(10) }, label = { Text("تاريخ النهاية YYYY-MM-DD") })
            TextButton(onClick = { credentialCurrent = !credentialCurrent }) { Text(if (credentialCurrent) "✓ مستمر حتى الآن" else "تحديد: حتى الآن") }
            OutlinedTextField(credentialLocation, { credentialLocation = it.take(120) }, label = { Text("الموقع (اختياري)") })
            OutlinedTextField(credentialDescription, { credentialDescription = it.take(1200) }, label = { Text("الوصف") })
            OutlinedTextField(credentialVerifyUrl, { credentialVerifyUrl = it.take(500) }, label = { Text("رابط التحقق") })
            OutlinedTextField(credentialDocumentUrl, { credentialDocumentUrl = it.take(500) }, label = { Text("رابط صورة/ملف الشهادة") })
            OutlinedTextField(credentialLogoUrl, { credentialLogoUrl = it.take(500) }, label = { Text("رابط شعار الجهة") })
        }, !busy && credentialTitle.isNotBlank() && credentialIssuer.isNotBlank(), "حفظ") {
            runSave {
                val body = JsonObject().apply {
                    editingId?.let { addProperty("id", it) }
                    addProperty("title", credentialTitle.trim()); addProperty("issuer", credentialIssuer.trim())
                    addProperty("record_type", credentialType); addProperty("category", credentialCategory)
                    addProperty("start_date", credentialStart.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) })
                    addProperty("end_date", if (credentialCurrent) null else credentialEnd.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) })
                    addProperty("is_current", credentialCurrent); addProperty("location", credentialLocation)
                    addProperty("description", credentialDescription); addProperty("verification_url", credentialVerifyUrl)
                    addProperty("document_url", credentialDocumentUrl); addProperty("organization_logo_url", credentialLogoUrl)
                }
                dataSource.saveRow("nexa_profile_credentials", body)
            }
        }
        "status" -> FormDialog("تحديث حالتك", { dialog = "" }, {
            ChoiceChips(statusChoices, statusCode) { statusCode = it }
            OutlinedTextField(statusCustom, { statusCustom = it.take(80) }, label = { Text("حالة مخصصة (اختياري)") })
            OutlinedTextField(statusEmoji, { statusEmoji = it.take(8) }, label = { Text("إيموجي") })
            ChoiceChips(listOf("1" to "ساعة", "8" to "8 ساعات", "24" to "24 ساعة", "168" to "أسبوع", "0" to "دائم"), statusDuration) { statusDuration = it }
            TextButton(onClick = { statusVisible = !statusVisible }) { Text(if (statusVisible) "👁 ظاهرة للآخرين" else "🔒 مخفية عن الآخرين") }
            TextButton(onClick = { statusNotifyExpiry = !statusNotifyExpiry }) { Text(if (statusNotifyExpiry) "🔔 تنبيه عند الانتهاء مفعّل" else "🔕 تنبيه عند الانتهاء متوقف") }
        }, !busy, "نشر الحالة") {
            runSave {
                val expiry = statusDuration.toLongOrNull()?.takeIf { it > 0 }?.let { Instant.now().plus(it, ChronoUnit.HOURS).toString() }
                val body = JsonObject().apply {
                    addProperty("status_code", statusCode); addProperty("custom_text", statusCustom.takeIf { it.isNotBlank() })
                    addProperty("emoji", statusEmoji); addProperty("color_key", statusColor(statusCode))
                    addProperty("expires_at", expiry); addProperty("is_visible", statusVisible)
                    addProperty("notify_on_expiry", statusNotifyExpiry)
                }
                dataSource.saveRow("nexa_profile_status", body)
            }
        }
        "testimonial" -> FormDialog(if (editingId == null) "إضافة توصية يدوياً" else "تعديل التوصية", { dialog = "" }, {
            OutlinedTextField(testimonialName, { testimonialName = it.take(100) }, label = { Text("اسم صاحب التوصية") })
            OutlinedTextField(testimonialTitle, { testimonialTitle = it.take(120) }, label = { Text("المسمى الوظيفي") })
            OutlinedTextField(testimonialCompany, { testimonialCompany = it.take(120) }, label = { Text("الشركة") })
            OutlinedTextField(testimonialBody, { testimonialBody = it.take(500) }, label = { Text("نص التوصية (500 حرف)") })
            ChoiceChips((1..5).map { it.toString() to "⭐".repeat(it) }, testimonialRating) { testimonialRating = it }
            OutlinedTextField(testimonialLinkedIn, { testimonialLinkedIn = it.take(500) }, label = { Text("رابط LinkedIn") })
        }, !busy && testimonialName.isNotBlank() && testimonialBody.isNotBlank(), "حفظ") {
            runSave {
                val body = JsonObject().apply {
                    editingId?.let { addProperty("id", it) }
                    addProperty("author_name", testimonialName.trim()); addProperty("author_title", testimonialTitle)
                    addProperty("author_company", testimonialCompany); addProperty("body", testimonialBody.trim())
                    addProperty("rating", testimonialRating.toIntOrNull()); addProperty("linkedin_url", testimonialLinkedIn)
                }
                dataSource.saveRow("nexa_profile_testimonials", body)
            }
        }
        "request" -> FormDialog("طلب توصية", { dialog = "" }, {
            OutlinedTextField(requestEmail, { requestEmail = it.take(200) }, label = { Text("البريد الإلكتروني (اختياري)") })
            Text("سيتم إنشاء رمز آمن صالح لمدة 30 يوماً. أرسله للشخص الذي تريد منه التوصية.", style = MaterialTheme.typography.bodySmall)
        }, !busy, "إنشاء الرمز") {
            busy = true
            scope.launch {
                val token = dataSource.createTestimonialRequest(requestEmail)
                message = if (token != null) "تم إنشاء طلب التوصية. شارك هذا الرمز مع الشخص: $token" else "تعذر إنشاء الطلب. تحقق من اتصال Supabase."
                dialog = ""; busy = false
            }
        }
        "submit" -> FormDialog("إرسال توصية", { dialog = "" }, {
            OutlinedTextField(requestToken, { requestToken = it.take(80) }, label = { Text("رمز طلب التوصية") })
            OutlinedTextField(testimonialName, { testimonialName = it.take(100) }, label = { Text("اسمك") })
            OutlinedTextField(testimonialTitle, { testimonialTitle = it.take(120) }, label = { Text("المسمى الوظيفي") })
            OutlinedTextField(testimonialCompany, { testimonialCompany = it.take(120) }, label = { Text("الشركة") })
            OutlinedTextField(testimonialBody, { testimonialBody = it.take(500) }, label = { Text("التوصية") })
            ChoiceChips((1..5).map { it.toString() to "⭐".repeat(it) }, testimonialRating) { testimonialRating = it }
            OutlinedTextField(testimonialLinkedIn, { testimonialLinkedIn = it.take(500) }, label = { Text("LinkedIn (اختياري)") })
        }, !busy && requestToken.isNotBlank() && testimonialName.isNotBlank() && testimonialBody.isNotBlank(), "إرسال") {
            busy = true
            scope.launch {
                val ok = dataSource.submitTestimonial(requestToken, testimonialName, testimonialBody, testimonialRating.toIntOrNull() ?: 5, testimonialTitle, testimonialCompany, testimonialLinkedIn)
                message = if (ok) "تم إرسال التوصية وتأكيدها من حسابك." else "لم يتم إرسال التوصية. تأكد من الرمز وصلاحيته."
                dialog = ""; busy = false
            }
        }
        "favorite" -> FormDialog(if (editingId == null) "إضافة مفضلة" else "تعديل المفضلة", { dialog = "" }, {
            ChoiceChips(favoriteCategories, favoriteCategory) { favoriteCategory = it }
            OutlinedTextField(favoriteTitle, { favoriteTitle = it.take(140) }, label = { Text("العنوان") })
            OutlinedTextField(favoriteCreator, { favoriteCreator = it.take(140) }, label = { Text("المؤلف / الفنان / الجهة") })
            OutlinedTextField(favoriteYear, { favoriteYear = it.take(20) }, label = { Text("السنة") })
            OutlinedTextField(favoriteImage, { favoriteImage = it.take(500) }, label = { Text("رابط الصورة") })
            OutlinedTextField(favoriteUrl, { favoriteUrl = it.take(500) }, label = { Text("رابط خارجي") })
            OutlinedTextField(favoriteQuote, { favoriteQuote = it.take(500) }, label = { Text("اقتباس") })
            OutlinedTextField(favoriteDescription, { favoriteDescription = it.take(500) }, label = { Text("لماذا تحبها؟") })
        }, !busy && favoriteTitle.isNotBlank(), "حفظ") {
            runSave {
                val body = JsonObject().apply {
                    editingId?.let { addProperty("id", it) }
                    addProperty("category", favoriteCategory); addProperty("title", favoriteTitle.trim())
                    addProperty("creator", favoriteCreator); addProperty("year", favoriteYear)
                    addProperty("image_url", favoriteImage); addProperty("external_url", favoriteUrl)
                    addProperty("quote", favoriteQuote); addProperty("description", favoriteDescription)
                }
                dataSource.saveRow("nexa_profile_favorites", body)
            }
        }
    }

}

@Composable
private fun ExperienceCard(
    title: String,
    isOwner: Boolean,
    onAdd: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (isOwner && onAdd != null) IconButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = "إضافة") }
            }
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceChips(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (code, label) ->
            FilterChip(selected = selected == code, onClick = { onSelect(code) }, label = { Text(label) })
        }
    }
}

@Composable
private fun FormDialog(
    title: String,
    onDismiss: () -> Unit,
    fields: @Composable ColumnScope.() -> Unit,
    enabled: Boolean,
    confirmText: String,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fields()
            }
        },
        confirmButton = { TextButton(enabled = enabled, onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun statusColor(code: String): String = when (code) {
    "AVAILABLE" -> "GREEN"; "LOOKING" -> "BLUE"; "BUSY" -> "PURPLE"
    "VACATION" -> "YELLOW"; "UNAVAILABLE" -> "ORANGE"; else -> "PINK"
}

private fun achievementTitle(code: String): String = when (code) {
    "FIRST_POST" -> "🥇 أول منشور"
    "ACTIVE_CREATOR" -> "🔥 صانع محتوى نشط"
    "HELPFUL_VOICE" -> "💬 صوت مفيد"
    "WORLD_EXPLORER" -> "🌍 مستكشف المجموعات"
    "WORLD_BUILDER" -> "🏗️ منشئ المجموعات"
    "SOCIAL_CONNECTOR" -> "🤝 اجتماعي"
    "CLIP_MAKER" -> "🎬 صانع مقاطع"
    "STORYTELLER" -> "📖 حكّاء القصص"
    "RISING_STAR" -> "⭐ نجم صاعد"
    else -> code
}
