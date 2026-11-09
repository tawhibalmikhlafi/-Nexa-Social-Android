package com.nexa.social.feature.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.google.gson.JsonObject
import com.nexa.social.core.ui.rememberAppContainer
import com.nexa.social.data.remote.OwnerReportRow
import com.nexa.social.data.remote.OwnerUserRow
import com.nexa.social.data.remote.OwnerSupportRow
import com.nexa.social.data.remote.OwnerContentRow
import com.nexa.social.data.remote.OwnerAdRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerDashboardScreen(onBack: () -> Unit) {
    val admin = rememberAppContainer().ownerAdminDataSource
    val scope = rememberCoroutineScope()
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    var ownerUserId by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var stats by remember { mutableStateOf<JsonObject?>(null) }
    var users by remember { mutableStateOf<List<OwnerUserRow>>(emptyList()) }
    var reports by remember { mutableStateOf<List<OwnerReportRow>>(emptyList()) }
    var reportFilter by remember { mutableStateOf("ALL") }
    var support by remember { mutableStateOf<List<OwnerSupportRow>>(emptyList()) }
    var contentRows by remember { mutableStateOf<List<OwnerContentRow>>(emptyList()) }
    var ads by remember { mutableStateOf<List<OwnerAdRow>>(emptyList()) }
    var auditRows by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var contentDeleteTarget by remember { mutableStateOf<OwnerContentRow?>(null) }
    var contentDeleteReason by remember { mutableStateOf("") }
    var selectedContentKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var contentBulkDeleteConfirm by remember { mutableStateOf(false) }
    var contentBulkDeleteReason by remember { mutableStateOf("") }
    var supportReply by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var announcementTitle by remember { mutableStateOf("") }
    var announcementBody by remember { mutableStateOf("") }
    var officialPost by remember { mutableStateOf("") }
    var settings by remember { mutableStateOf<JsonObject?>(null) }
    var confirmDelete by remember { mutableStateOf<OwnerUserRow?>(null) }
    var moderationTarget by remember { mutableStateOf<OwnerUserRow?>(null) }
    var moderationAction by remember { mutableStateOf("RESTRICT") }
    var moderationReason by remember { mutableStateOf("") }
    var moderationDurationHours by remember { mutableStateOf("24") }
    var moderationHistory by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var selectedUser by remember { mutableStateOf<OwnerUserRow?>(null) }
    var selectedUserDetails by remember { mutableStateOf<JsonObject?>(null) }
    var userDetailsLoading by remember { mutableStateOf(false) }
    var verificationBusy by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            loading = true
            message = null
            val source = admin
            if (source == null) {
                allowed = false
                message = "اتصال Supabase غير مضبوط"
                loading = false
                return@launch
            }
            allowed = source.isOwner()
            if (allowed == true) {
                ownerUserId = source.currentUserId()
                stats = source.dashboardStats()
                users = source.listUsers(search)
                reports = source.listReports()
                settings = source.getSettings()
                support = source.listSupport()
                contentRows = source.listContent()
                ads = source.listAds()
                auditRows = source.listAudit()
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        containerColor = Color(0xFF090B16),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF111426),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color(0xFFD8CCFF),
                    actionIconContentColor = Color(0xFFD8CCFF)
                ),
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("NEXA CONTROL", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB7A3FF))
                        Text("لوحة المالك", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = { IconButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, contentDescription = "تحديث") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).background(Color(0xFF090B16)).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (loading && allowed == null) {
                CircularProgressIndicator()
            } else if (allowed != true) {
                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text("غير مصرح بالدخول", style = MaterialTheme.typography.titleLarge)
                Text(message ?: "هذه المساحة مخصصة لمالك Nexa فقط. تحقق من تسجيل الدخول بحساب المالك.")
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF171B30))
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("مركز إدارة Nexa", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                Text("نظرة عامة على المنصة", color = Color(0xFF9BA3BF), style = MaterialTheme.typography.labelSmall)
                            }
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = Color(0xFF9B72FF), modifier = Modifier.size(28.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatCard("المستخدمون", stats?.get("users_total")?.asString ?: "—", Modifier.weight(1f))
                            StatCard("تسجيلات اليوم", stats?.get("users_today")?.asString ?: "—", Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatCard("المنشورات", stats?.get("posts_total")?.asString ?: "—", Modifier.weight(1f))
                            StatCard("البلاغات المفتوحة", stats?.get("reports_open")?.asString ?: "—", Modifier.weight(1f))
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("المستخدمون", "البلاغات", "الإعلانات الرسمية", "المنشورات الرسمية", "إعدادات المنصة", "الدعم الفني", "إدارة المحتوى", "الحملات الإعلانية", "الصلاحيات والأمان", "سجل العمليات").forEachIndexed { i, label ->
                        FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(label, maxLines = 1) }, shape = RoundedCornerShape(14.dp))
                    }
                }
                when (tab) {
                    0 -> {
                        OutlinedTextField(
                            value = search, onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth(), label = { Text("بحث بالبريد أو الاسم أو اسم المستخدم") },
                            singleLine = true
                        )
                        Button(onClick = { refresh() }, modifier = Modifier.fillMaxWidth()) { Text("بحث وتحديث المستخدمين") }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(users, key = { it.id }) { user ->
                                UserAdminCard(
                                    user = user,
                                    isOwnerAccount = ownerUserId.isNullOrBlank() || user.id == ownerUserId,
                                    onCardClick = {
                                        selectedUser = user
                                        selectedUserDetails = null
                                        moderationHistory = emptyList()
                                        userDetailsLoading = true
                                        scope.launch {
                                            selectedUserDetails = admin?.getUserDetails(user.id)
                                            moderationHistory = admin?.moderationHistory(user.id).orEmpty()
                                            userDetailsLoading = false
                                        }
                                    },
                                    onRestrictClick = {
                                        if (!ownerUserId.isNullOrBlank() && user.id != ownerUserId) {
                                            moderationTarget = user
                                            moderationAction = if (user.status == "SUSPENDED") "RESTORE" else "RESTRICT"
                                            moderationReason = ""
                                        }
                                    },
                                    onBlockClick = {
                                        if (!ownerUserId.isNullOrBlank() && user.id != ownerUserId) {
                                            moderationTarget = user
                                            moderationAction = if (user.status == "BANNED") "RESTORE" else "BAN"
                                            moderationReason = ""
                                        }
                                    },
                                    onDeleteClick = {
                                        if (!ownerUserId.isNullOrBlank() && user.id != ownerUserId) confirmDelete = user
                                    }
                                )
                            }
                        }
                    }
                    1 -> Column(Modifier.fillMaxSize()) {
                        Text("مركز مراجعة البلاغات", style = MaterialTheme.typography.titleLarge)
                        Text("يظهر صاحب البلاغ والحساب أو المحتوى المبلّغ عليه، مع حفظ كل قرار إداري في سجل التدقيق.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("ALL" to "الكل", "POST" to "منشورات", "GROUP_POST" to "منشورات المجموعات", "COMMENT" to "تعليقات", "CLIP" to "ريلز", "USER" to "حسابات").forEach { (value, label) ->
                                FilterChip(
                                    selected = reportFilter == value,
                                    onClick = {
                                        reportFilter = value
                                        scope.launch { reports = admin?.listReports(value).orEmpty() }
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(reports, key = { it.id }) { report ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(when(report.targetType) {
                                                "POST" -> "بلاغ منشور"
                                                "COMMENT" -> "بلاغ تعليق"
                                                "CLIP" -> "بلاغ ريلز"
                                                "USER" -> "بلاغ حساب"
                                                "COMMUNITY", "GROUP_POST" -> "بلاغ مجموعة"
                                                else -> report.targetType
                                            }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.weight(1f))
                                            Text(when(report.status.uppercase()) {
                                                "REVIEWED" -> "قيد المراجعة"
                                                "DISMISSED" -> "مرفوض"
                                                "RESOLVED" -> "تمت المعالجة"
                                                else -> "جديد"
                                            }, style = MaterialTheme.typography.labelMedium)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            AsyncImage(model=report.reporterAvatarUrl.takeIf { it.isNotBlank() },contentDescription="صورة المبلّغ",modifier=Modifier.size(40.dp).clip(CircleShape),contentScale=ContentScale.Crop)
                                            Column {
                                                Text("المبلّغ: ${report.reporterName.ifBlank { "مستخدم Nexa" }}")
                                                if (report.reporterUsername.isNotBlank()) Text("@${report.reporterUsername}",style=MaterialTheme.typography.bodySmall)
                                                if (report.reporterEmail.isNotBlank()) Text(report.reporterEmail,style=MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            AsyncImage(model=report.targetAuthorAvatarUrl.takeIf { it.isNotBlank() },contentDescription="صورة الحساب المبلّغ عليه",modifier=Modifier.size(40.dp).clip(CircleShape),contentScale=ContentScale.Crop)
                                            Column {
                                                Text("الحساب المبلّغ عليه: ${report.targetAuthorName.ifBlank { if (report.targetType == "USER") "الحساب محل البلاغ" else "غير متاح أو حُذف المحتوى" }}")
                                                if (report.targetAuthorUsername.isNotBlank()) Text("@${report.targetAuthorUsername}",style=MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        if (report.targetPreview.isNotBlank()) {
                                            Text("المحتوى المبلّغ عنه", style = MaterialTheme.typography.labelLarge)
                                            Text(report.targetPreview, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        Text("إجمالي البلاغات على هذا الهدف: ${report.targetReportCount}")
                                        Text("السبب: ${report.reason}")
                                        if (report.note.isNotBlank()) Text("تفاصيل البلاغ: ${report.note}")
                                        Text("التاريخ: ${report.createdAt}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("معرّف البلاغ: ${report.id}", style = MaterialTheme.typography.labelSmall)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (report.reporterId.isNotBlank()) {
                                                OutlinedButton(onClick = {
                                                    selectedUser = OwnerUserRow(report.reporterId, report.reporterEmail, report.reporterUsername, report.reporterName, "", "ACTIVE", false)
                                                    selectedUserDetails = null
                                                    moderationHistory = emptyList()
                                                    userDetailsLoading = true
                                                    scope.launch {
                                                        selectedUserDetails = admin?.getUserDetails(report.reporterId)
                                                        moderationHistory = admin?.moderationHistory(report.reporterId).orEmpty()
                                                        userDetailsLoading = false
                                                    }
                                                }) { Text("ملف المبلّغ") }
                                            }
                                            if (report.targetAuthorId.isNotBlank()) {
                                                OutlinedButton(onClick = {
                                                    selectedUser = OwnerUserRow(report.targetAuthorId, "", report.targetAuthorUsername, report.targetAuthorName, "", "ACTIVE", false)
                                                    selectedUserDetails = null
                                                    moderationHistory = emptyList()
                                                    userDetailsLoading = true
                                                    scope.launch {
                                                        selectedUserDetails = admin?.getUserDetails(report.targetAuthorId)
                                                        moderationHistory = admin?.moderationHistory(report.targetAuthorId).orEmpty()
                                                        userDetailsLoading = false
                                                    }
                                                }) { Text("ملف صاحب المحتوى") }
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (report.targetType in setOf("POST","GROUP_POST","COMMENT","CLIP")) {
                                                OutlinedButton(onClick = {
                                                    contentDeleteTarget = OwnerContentRow(report.targetId, report.targetType, report.targetPreview, report.targetAuthorName, report.createdAt)
                                                    contentDeleteReason = "حذف نتيجة بلاغ رقم ${report.id}"
                                                }) { Text("حذف المحتوى") }
                                            }
                                            if (report.targetAuthorId.isNotBlank()) {
                                                OutlinedButton(onClick = {
                                                    moderationTarget = OwnerUserRow(report.targetAuthorId, "", report.targetAuthorUsername, report.targetAuthorName, "", "ACTIVE", false)
                                                    moderationAction = "RESTRICT"
                                                    moderationReason = "إجراء بسبب البلاغ ${report.id}"
                                                }) { Text("تقييد صاحب المحتوى") }
                                                OutlinedButton(onClick = {
                                                    moderationTarget = OwnerUserRow(report.targetAuthorId, "", report.targetAuthorUsername, report.targetAuthorName, "", "ACTIVE", false)
                                                    moderationAction = "BAN"
                                                    moderationReason = "حظر بسبب البلاغ ${report.id}"
                                                }) { Text("حظر صاحب المحتوى") }
                                            }
                                        }
                                        if (report.status.uppercase() !in setOf("REVIEWED", "RESOLVED", "DISMISSED")) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(onClick = {
                                                    scope.launch {
                                                        val ok = admin?.updateReport(report.id, "REVIEWED", "تمت المراجعة من لوحة المالك") == true
                                                        message = if (ok) "تم حفظ قرار مراجعة البلاغ" else "تعذر تحديث البلاغ: ${admin?.lastError.orEmpty()}"
                                                        reports = admin?.listReports(reportFilter).orEmpty()
                                                    }
                                                }) { Text("قيد المراجعة") }
                                                OutlinedButton(onClick = {
                                                    scope.launch {
                                                        val ok = admin?.updateReport(report.id, "RESOLVED", "تمت معالجة البلاغ من لوحة المالك") == true
                                                        message = if (ok) "تمت معالجة البلاغ وإغلاقه" else "تعذر إغلاق البلاغ: ${admin?.lastError.orEmpty()}"
                                                        reports = admin?.listReports(reportFilter).orEmpty()
                                                    }
                                                }) { Text("تمت المعالجة") }
                                                OutlinedButton(onClick = {
                                                    scope.launch {
                                                        val ok = admin?.updateReport(report.id, "DISMISSED", "تم رفض البلاغ من لوحة المالك") == true
                                                        message = if (ok) "تم رفض البلاغ وحفظ القرار" else "تعذر رفض البلاغ: ${admin?.lastError.orEmpty()}"
                                                        reports = admin?.listReports(reportFilter).orEmpty()
                                                    }
                                                }) { Text("رفض البلاغ") }
                                            }
                                        }
                                    }
                                }
                            }
                            if (reports.isEmpty()) item {
                                Text("لا توجد بلاغات في هذا التصنيف.", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        OutlinedButton(onClick = { scope.launch { reports = admin?.listReports(reportFilter).orEmpty() } }, modifier = Modifier.fillMaxWidth()) {
                            Text("تحديث البلاغات")
                        }
                    }
                    2 -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(announcementTitle, { announcementTitle = it }, Modifier.fillMaxWidth(), label = { Text("عنوان الإعلان") })
                        OutlinedTextField(announcementBody, { announcementBody = it }, Modifier.fillMaxWidth(), minLines = 4, label = { Text("نص الإعلان") })
                        Button(onClick = {
                            scope.launch {
                                val ok = admin?.createAnnouncement(announcementTitle, announcementBody) == true
                                message = if (ok) "تم نشر الإعلان وإضافته إلى إشعارات المستخدمين" else "فشل نشر الإعلان: ${admin?.lastError ?: "تحقق من اتصال Supabase وصلاحيات المالك"}"
                                if (ok) { announcementTitle = ""; announcementBody = "" }
                            }
                        }, enabled = announcementTitle.isNotBlank() && announcementBody.isNotBlank()) {
                            Icon(Icons.Default.Campaign, contentDescription = null); Text("نشر إعلان لجميع المستخدمين")
                        }
                    }
                    3 -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("النشر الرسمي من حساب المالك الحالي. إنشاء حساب Nexa مستقل يتطلب هوية دخول مستقلة.")
                        OutlinedTextField(officialPost, { officialPost = it }, Modifier.fillMaxWidth(), minLines = 5, label = { Text("نص المنشور الرسمي") })
                        Button(onClick = {
                            scope.launch {
                                val ok = admin?.createOfficialPost(officialPost) == true
                                message = if (ok) "تم نشر المنشور الرسمي بنجاح" else "فشل النشر: ${admin?.lastError ?: "تحقق من صلاحيات حساب المالك"}"
                                if (ok) officialPost = ""
                            }
                        }, enabled = officialPost.isNotBlank()) { Text("نشر باسم حساب المالك") }
                        OutlinedButton(onClick = {
                            scope.launch {
                                message = if (admin?.enableFounderBadge() == true) "تم تفعيل شارة مؤسس Nexa" else "تعذر تفعيل الشارة"
                            }
                        }) { Icon(Icons.Default.AdminPanelSettings, contentDescription = null); Text("تفعيل شارة مؤسس Nexa") }
                    }
                    4 -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("إعدادات التشغيل العامة", style = MaterialTheme.typography.titleLarge)
                        Text("تُحفظ المفاتيح فور تغييرها في Supabase، أما إعدادات الإعلان فتُحفظ بزر مستقل بعد مراجعتها.")
                        OwnerSettingRow("وضع الصيانة", settings?.get("maintenance_mode")?.asBoolean ?: false) { enabled ->
                            scope.launch {
                                val ok = admin?.setSetting("maintenance_mode", enabled) == true
                                message = if (ok) "تم حفظ وضع الصيانة في قاعدة البيانات" else "تعذر حفظ وضع الصيانة: ${admin?.lastError.orEmpty()}"
                                settings = admin?.getSettings()
                            }
                        }
                        OwnerSettingRow("السماح بالتسجيل الجديد", settings?.get("new_registrations_enabled")?.asBoolean ?: true) { enabled ->
                            scope.launch {
                                val ok = admin?.setSetting("new_registrations_enabled", enabled) == true
                                message = if (ok) "تم حفظ إعداد التسجيل في قاعدة البيانات" else "تعذر حفظ إعداد التسجيل: ${admin?.lastError.orEmpty()}"
                                settings = admin?.getSettings()
                            }
                        }
                        OwnerSettingRow("تفعيل شارة التوثيق الرسمية", settings?.get("official_badge_enabled")?.asBoolean ?: true) { enabled ->
                            scope.launch {
                                val ok = admin?.setSetting("official_badge_enabled", enabled) == true
                                message = if (ok) "تم حفظ إعداد الشارة في قاعدة البيانات" else "تعذر حفظ إعداد الشارة: ${admin?.lastError.orEmpty()}"
                                settings = admin?.getSettings()
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("التحكم في ميزات المنصة", style = MaterialTheme.typography.titleLarge)
                        Text("إيقاف الميزة يمنع استخدامها من خلال سياسات قاعدة البيانات، وليس مجرد إخفاء زرها.")
                        listOf(
                            Triple("posts_enabled", "السماح بإنشاء المنشورات", true),
                            Triple("comments_enabled", "السماح بالتعليقات والردود", true),
                            Triple("stories_enabled", "السماح بنشر القصص", true),
                            Triple("reels_enabled", "السماح بنشر المقاطع القصيرة", true),
                            Triple("verified_only_posting", "قصر النشر على الحسابات الموثقة", false)
                        ).forEach { (key, label, defaultValue) ->
                            OwnerSettingRow(label, settings?.get(key)?.asBoolean ?: defaultValue) { enabled ->
                                scope.launch {
                                    val ok = admin?.setSetting(key, enabled) == true
                                    message = if (ok) "تم تحديث: $label" else "تعذر تحديث $label: ${admin?.lastError.orEmpty()}"
                                    settings = admin?.getSettings()
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("إعدادات الإعلانات", style = MaterialTheme.typography.titleLarge)
                        Text("تحكم في موضع الإعلان ومدة بقائه وعدد مرات عرضه في الجلسة.")
                        OwnerAdsSettingsPanel(settings = settings) { enabled, placement, duration, frequency ->
                            scope.launch {
                                message = "جارٍ حفظ إعدادات الإعلانات..."
                                val saved = admin?.setAdsSettings(enabled, placement, duration, frequency) == true
                                if (!saved) {
                                    message = "فشل حفظ إعدادات الإعلانات: ${admin?.lastError ?: "لم تؤكد قاعدة البيانات العملية"}"
                                } else {
                                    val persisted = admin?.getSettings()
                                    val verified = persisted?.let { saved ->
                                        saved.get("ads_enabled")?.takeUnless { it.isJsonNull }?.asBoolean == enabled &&
                                            saved.get("ads_placement")?.takeUnless { it.isJsonNull }?.asString == placement &&
                                            saved.get("ads_duration_seconds")?.takeUnless { it.isJsonNull }?.asInt == duration &&
                                            saved.get("ads_frequency_per_session")?.takeUnless { it.isJsonNull }?.asInt == frequency
                                    } == true
                                    if (verified) {
                                        settings = persisted
                                        message = "تم الحفظ بنجاح، وتم التحقق من الإعدادات المحفوظة في Supabase"
                                    } else {
                                        settings = persisted ?: settings
                                        message = "وصل تأكيد الحفظ، لكن تعذر التحقق من القيم بعد إعادة قراءتها من Supabase: ${admin?.lastError.orEmpty()}"
                                    }
                                }
                            }
                        }
                    }
                    5 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(support, key = { it.id }) { ticket ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(ticket.subject, style = MaterialTheme.typography.titleMedium)
                                    Text(ticket.message)
                                    Text("الحالة: ${ticket.status} • ${ticket.createdAt}")
                                    if (ticket.reply.isNotBlank()) Text("الرد السابق: ${ticket.reply}")
                                    OutlinedTextField(supportReply, { supportReply = it }, Modifier.fillMaxWidth(), label = { Text("اكتب رد الدعم") }, minLines = 2)
                                    Button(onClick = {
                                        scope.launch {
                                            val ok = admin?.replySupport(ticket.id, supportReply) == true
                                            message = if (ok) "تم إرسال الرد" else "تعذر إرسال الرد"
                                            if (ok) supportReply = ""
                                            refresh()
                                        }
                                    }, enabled = supportReply.isNotBlank()) { Text("إرسال الرد وإغلاق الطلب") }
                                }
                            }
                        }
                    }
                    6 -> Column(Modifier.fillMaxSize()) {
                        Text("إدارة وحذف المحتوى من جميع المستخدمين", style = MaterialTheme.typography.titleMedium)
                        Text("الحذف نهائي من قاعدة البيانات مع حفظ سجل تدقيق. يمكن تحديد عناصر منفردة أو تحديد جميع العناصر المعروضة (حتى 200).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val allContentKeys = contentRows.map { "${it.type}:${it.id}" }.toSet()
                        val allContentSelected = allContentKeys.isNotEmpty() && selectedContentKeys.containsAll(allContentKeys)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = allContentSelected,
                                    onCheckedChange = { checked ->
                                        selectedContentKeys = if (checked) allContentKeys else emptySet()
                                    }
                                )
                                Text(if (allContentSelected) "إلغاء تحديد الكل" else "تحديد الكل")
                            }
                            Text("${selectedContentKeys.intersect(allContentKeys).size} / ${contentRows.size} محدد", style = MaterialTheme.typography.labelMedium)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { contentBulkDeleteReason = ""; contentBulkDeleteConfirm = true },
                                enabled = selectedContentKeys.any { it in allContentKeys },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                                Text("حذف المحدد")
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        contentRows = admin?.listContent().orEmpty()
                                        selectedContentKeys = emptySet()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("تحديث المحتوى") }
                        }
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(contentRows, key = { it.type + it.id }) { item ->
                                val contentKey = "${item.type}:${item.id}"
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = contentKey in selectedContentKeys,
                                                onCheckedChange = { checked ->
                                                    selectedContentKeys = if (checked) selectedContentKeys + contentKey else selectedContentKeys - contentKey
                                                }
                                            )
                                            Text(item.type, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Text(item.body.ifBlank { "بدون نص" }, style = MaterialTheme.typography.bodyMedium)
                                        Text("بواسطة " + item.authorName + " • " + item.createdAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        OutlinedButton(onClick = { contentDeleteTarget = item; contentDeleteReason = "" }) {
                                            Icon(Icons.Default.Delete, contentDescription = null)
                                            Text("حذف هذا المحتوى")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (tab == 7) {
                    OwnerAdsManagementPanel(ads, { scope.launch { ads = admin?.listAds().orEmpty() } }) { id,title,body,mediaUrl,mediaType,clickUrl,placement,audience,startsAt,endsAt,duration,frequency,enabled ->
                        scope.launch {
                            val ok = admin?.saveAd(id,title,body,mediaUrl,mediaType,clickUrl,placement,audience,startsAt,endsAt,duration,frequency,enabled) == true
                            message = if (ok) "تم حفظ الحملة الإعلانية" else "فشل الحفظ: " + admin?.lastError.orEmpty()
                            if (ok) ads = admin?.listAds().orEmpty()
                        }
                    }
                }
                if (tab == 8) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("الصلاحيات والأمان", style = MaterialTheme.typography.titleLarge)
                        Text("الدخول محصور بمالك Nexa عبر nexa_is_owner(). عمليات الحظر والتقييد والحذف تمر عبر دوال محمية في Supabase.")
                        Text("حذف الحسابات يتم على الخادم عبر Edge Function ولا يُكشف مفتاح الخدمة داخل تطبيق Android.")
                        Text("يمكن التحكم في التسجيل والمنشورات والتعليقات والقصص والريلز من إعدادات المنصة.")
                    }
                }
                if (tab == 9) {
                    Column(Modifier.fillMaxSize()) {
                        Text("سجل العمليات", style = MaterialTheme.typography.titleLarge)
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(auditRows) { entry ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text((entry.get("action")?.takeUnless { it.isJsonNull }?.asString ?: "إجراء") + " • " + (entry.get("entity_type")?.takeUnless { it.isJsonNull }?.asString ?: ""))
                                        Text("المعرّف: " + (entry.get("entity_id")?.takeUnless { it.isJsonNull }?.asString ?: "—"))
                                        Text("المنفذ: " + (entry.get("actor_id")?.takeUnless { it.isJsonNull }?.asString ?: "—"))
                                        Text("التاريخ: " + (entry.get("created_at")?.takeUnless { it.isJsonNull }?.asString ?: "—"))
                                    }
                                }
                            }
                        }
                        OutlinedButton(onClick = { scope.launch { auditRows = admin?.listAudit().orEmpty() } }, modifier = Modifier.fillMaxWidth()) { Text("تحديث السجل") }
                    }
                }
                message?.let { status ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF191D32))
                    ) {
                        Text(status, color = if (status.startsWith("تم")) Color(0xFF8FE0B0) else Color(0xFFFF9C9C), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    selectedUser?.let { user ->
        val profile = selectedUserDetails?.getAsJsonObject("profile")
        val displayName = profile?.get("display_name")?.takeUnless { it.isJsonNull }?.asString
            ?: user.displayName.ifBlank { user.username.ifBlank { user.email.substringBefore("@") } }
        val username = profile?.get("username")?.takeUnless { it.isJsonNull }?.asString ?: user.username
        val email = selectedUserDetails?.get("email")?.takeUnless { it.isJsonNull }?.asString ?: user.email
        val createdAt = selectedUserDetails?.get("created_at")?.takeUnless { it.isJsonNull }?.asString ?: user.createdAt
        val lastSignIn = selectedUserDetails?.get("last_sign_in_at")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
        val accountStatus = profile?.get("account_status")?.takeUnless { it.isJsonNull }?.asString ?: user.status
        val canManageSelectedUser = !ownerUserId.isNullOrBlank() && user.id != ownerUserId
        val postsCount = selectedUserDetails?.get("posts_count")?.takeUnless { it.isJsonNull }?.asString ?: user.postsCount.toString()
        val followersCount = selectedUserDetails?.get("followers_count")?.takeUnless { it.isJsonNull }?.asString ?: user.followersCount.toString()
        val followingCount = selectedUserDetails?.get("following_count")?.takeUnless { it.isJsonNull }?.asString ?: user.followingCount.toString()
        Dialog(
            onDismissRequest = { selectedUser = null; selectedUserDetails = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("ملف المستخدم") },
                            navigationIcon = {
                                IconButton(onClick = { selectedUser = null; selectedUserDetails = null }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                                }
                            },
                            actions = {
                                IconButton(onClick = {
                                    userDetailsLoading = true
                                    scope.launch {
                                        selectedUserDetails = admin?.getUserDetails(user.id)
                                        moderationHistory = admin?.moderationHistory(user.id).orEmpty()
                                        userDetailsLoading = false
                                    }
                                }) { Icon(Icons.Default.Refresh, contentDescription = "تحديث التفاصيل") }
                            }
                        )
                    }
                ) { detailPadding ->
                    Column(
                        Modifier.fillMaxSize().padding(detailPadding).verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1838))
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AsyncImage(
                                    model = profile?.get("avatar_url")?.takeUnless { it.isJsonNull }?.asString
                                        ?.takeIf { it.isNotBlank() } ?: user.avatarUrl.takeIf { it.isNotBlank() },
                                    contentDescription = "الصورة الشخصية",
                                    modifier = Modifier.size(88.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Text(displayName.ifBlank { "مستخدم Nexa" }, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                                Text("@${username.ifBlank { "بدون اسم مستخدم" }}", color = Color(0xFFC9BDFB))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        when(accountStatus) { "BANNED" -> "محظور"; "SUSPENDED" -> "مقيّد"; "ACTIVE" -> "نشط"; else -> accountStatus },
                                        color = Color.White,
                                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF493A76)).padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                    Text(
                                        if (user.verified) "✓ موثق من Nexa" else "غير موثق",
                                        color = Color.White,
                                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF493A76)).padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AdminUserMetric("المنشورات", postsCount, Modifier.weight(1f))
                            AdminUserMetric("المتابعون", followersCount, Modifier.weight(1f))
                            AdminUserMetric("يتابع", followingCount, Modifier.weight(1f))
                        }

                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("معلومات الحساب", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                AdminUserInfoRow("البريد الإلكتروني", email.ifBlank { "غير متوفر" })
                                HorizontalDivider()
                                AdminUserInfoRow("اسم المستخدم", username.ifBlank { "غير متوفر" })
                                HorizontalDivider()
                                AdminUserInfoRow("معرّف الحساب", user.id, copyable = true)
                                HorizontalDivider()
                                AdminUserInfoRow("تاريخ التسجيل", formatAdminDate(createdAt))
                                HorizontalDivider()
                                AdminUserInfoRow("آخر تسجيل دخول", formatAdminDate(lastSignIn).ifBlank { "غير متوفر" })
                                if (user.restrictionExpiresAt.isNotBlank()) {
                                    HorizontalDivider()
                                    AdminUserInfoRow("انتهاء التقييد", formatAdminDate(user.restrictionExpiresAt))
                                }
                            }
                        }

                        val bio = profile?.get("bio")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                        val city = profile?.get("city")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                        val origin = profile?.get("place_of_origin")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                        val residence = profile?.get("current_residence")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                        val birthDate = profile?.get("birth_date")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                        val website = profile?.get("website")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                        val skills = profile?.get("skills")?.takeIf { it.isJsonArray }?.asJsonArray?.joinToString("، ") { it.asString }.orEmpty()
                        val hobbies = profile?.get("hobbies")?.takeIf { it.isJsonArray }?.asJsonArray?.joinToString("، ") { it.asString }.orEmpty()
                        if (listOf(bio, city, origin, residence, birthDate, website, skills, hobbies).any { it.isNotBlank() }) {
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("المعلومات الشخصية", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                    if (bio.isNotBlank()) AdminUserInfoRow("النبذة", bio)
                                    if (city.isNotBlank()) AdminUserInfoRow("المدينة", city)
                                    if (origin.isNotBlank()) AdminUserInfoRow("مكان المنشأ", origin)
                                    if (residence.isNotBlank()) AdminUserInfoRow("مكان الإقامة", residence)
                                    if (birthDate.isNotBlank()) AdminUserInfoRow("تاريخ الميلاد", formatAdminDate(birthDate))
                                    if (website.isNotBlank()) AdminUserInfoRow("الموقع", website)
                                    if (skills.isNotBlank()) AdminUserInfoRow("المهارات", skills)
                                    if (hobbies.isNotBlank()) AdminUserInfoRow("الهوايات", hobbies)
                                }
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("إدارة الحساب", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                Text("كل إجراء يُرسل إلى خدمة الإدارة المحمية في Supabase، ويُطلب تأكيده قبل التنفيذ.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedButton(
                                    onClick = {
                                        if (!ownerUserId.isNullOrBlank() && !verificationBusy) {
                                            scope.launch {
                                                verificationBusy = true
                                                val nextVerified = !user.verified
                                                val saved = admin?.setUserVerified(user.id, nextVerified) == true
                                                if (saved) {
                                                    users = admin?.listUsers(search).orEmpty()
                                                    selectedUser = users.firstOrNull { it.id == user.id } ?: user.copy(verified = nextVerified)
                                                    selectedUserDetails = admin?.getUserDetails(user.id)
                                                    message = if (nextVerified) "تم توثيق الحساب بنجاح" else "تمت إزالة شارة التوثيق"
                                                } else {
                                                    message = "تعذر تحديث التوثيق: ${admin?.lastError ?: "لم تؤكد قاعدة البيانات العملية"}"
                                                }
                                                verificationBusy = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !ownerUserId.isNullOrBlank() && !verificationBusy
                                ) {
                                    if (verificationBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Default.Verified, contentDescription = null)
                                    Spacer(Modifier.size(8.dp))
                                    Text(if (user.verified) "إزالة شارة التوثيق" else "إضافة شارة التوثيق")
                                }
                                Button(
                                    onClick = {
                                        if (canManageSelectedUser) {
                                            moderationTarget = user
                                            moderationAction = if (accountStatus == "SUSPENDED") "RESTORE" else "RESTRICT"
                                            moderationReason = ""
                                            selectedUser = null
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = canManageSelectedUser
                                ) { Text(if (accountStatus == "SUSPENDED") "إلغاء التقييد" else "تقييد الحساب") }
                                OutlinedButton(
                                    onClick = {
                                        if (canManageSelectedUser) {
                                            moderationTarget = user
                                            moderationAction = if (accountStatus == "BANNED") "RESTORE" else "BAN"
                                            moderationReason = ""
                                            selectedUser = null
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = canManageSelectedUser
                                ) { Text(if (accountStatus == "BANNED") "إلغاء الحظر" else "حظر الحساب") }
                                OutlinedButton(
                                    onClick = {
                                        if (!ownerUserId.isNullOrBlank() && user.id != ownerUserId) {
                                            confirmDelete = user
                                            selectedUser = null
                                        } else {
                                            message = "لا يمكن حذف حساب المالك أو تنفيذ الإجراء دون التحقق من هويته."
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = canManageSelectedUser
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                    Text("حذف الحساب نهائياً")
                                }
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("سجل إجراءات الإدارة", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                if (userDetailsLoading) {
                                    CircularProgressIndicator()
                                } else if (moderationHistory.isEmpty()) {
                                    Text("لا توجد إجراءات إدارية مسجلة لهذا الحساب.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    moderationHistory.forEach { entry ->
                                        val action = entry.get("action")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                                        val reason = entry.get("reason")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                                        val at = entry.get("created_at")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                                        Text(action.ifBlank { "إجراء إداري" }, style = MaterialTheme.typography.titleSmall)
                                        if (reason.isNotBlank()) Text("السبب: $reason", style = MaterialTheme.typography.bodySmall)
                                        Text(formatAdminDate(at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (entry != moderationHistory.last()) HorizontalDivider()
                                    }
                                }
                            }
                        }
                        if (userDetailsLoading) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
                        }
                    }
                }
            }
        }
    }

    contentDeleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { contentDeleteTarget = null },
            title = { Text("تأكيد حذف المحتوى نهائياً") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("سيتم حذف المحتوى وملفاته المرتبطة عند توفر صلاحية التخزين. لا يمكن التراجع عن هذا الإجراء.")
                    OutlinedTextField(
                        value = contentDeleteReason,
                        onValueChange = { contentDeleteReason = it.take(1000) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("سبب الحذف (مطلوب)") },
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = contentDeleteReason.isNotBlank(), onClick = {
                    val target = item
                    val reason = contentDeleteReason
                    contentDeleteTarget = null
                    scope.launch {
                        val ok = admin?.deleteContent(target.type, target.id, reason) == true
                        message = if (ok) admin?.lastError ?: "تم حذف المحتوى وحفظ سجل التدقيق" else "تعذر حذف المحتوى: " + admin?.lastError.orEmpty()
                        contentRows = admin?.listContent().orEmpty()
                        stats = admin?.dashboardStats()
                    }
                }) { Text("حذف نهائي") }
            },
            dismissButton = { TextButton(onClick = { contentDeleteTarget = null }) { Text("إلغاء") } }
        )
    }

    if (contentBulkDeleteConfirm) {
        val selectedItems = contentRows.filter { "${it.type}:${it.id}" in selectedContentKeys }
        AlertDialog(
            onDismissRequest = { contentBulkDeleteConfirm = false },
            title = { Text("تأكيد الحذف الجماعي") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("سيتم حذف ${selectedItems.size} عنصراً نهائياً من جداول Supabase، مع حفظ سجل لكل عنصر. لا يمكن التراجع عن الحذف.")
                    OutlinedTextField(
                        value = contentBulkDeleteReason,
                        onValueChange = { contentBulkDeleteReason = it.take(1000) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("سبب الحذف (مطلوب)") },
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selectedItems.isNotEmpty() && contentBulkDeleteReason.isNotBlank(),
                    onClick = {
                        val targets = selectedItems.toList()
                        val reason = contentBulkDeleteReason
                        contentBulkDeleteConfirm = false
                        scope.launch {
                            message = "جارٍ حذف ${targets.size} عنصراً من قاعدة البيانات..."
                            val ok = admin?.deleteContentBatch(targets, reason) == true
                            message = if (ok) {
                                val warning = admin?.lastError
                                if (warning.isNullOrBlank()) "تم حذف ${targets.size} عنصراً نهائياً من قاعدة البيانات وتنظيف وسائطها" else warning
                            } else "فشل الحذف الجماعي: ${admin?.lastError.orEmpty()}"
                            contentRows = admin?.listContent().orEmpty()
                            selectedContentKeys = emptySet()
                            stats = admin?.dashboardStats()
                        }
                    }
                ) { Text("حذف نهائي للجميع") }
            },
            dismissButton = { TextButton(onClick = { contentBulkDeleteConfirm = false }) { Text("إلغاء") } }
        )
    }

    moderationTarget?.let { user ->
        AlertDialog(
            onDismissRequest = { moderationTarget = null },
            title = { Text(when(moderationAction) { "BAN" -> "حظر المستخدم"; "RESTRICT" -> "تقييد المستخدم"; else -> "إعادة تفعيل المستخدم" }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(when(moderationAction) {
                        "BAN" -> "سيُمنع الحساب من النشر والتفاعل وسيختفي من المساحات العامة حتى فك الحظر."
                        "RESTRICT" -> "سيبقى الحساب موجوداً، لكن ستُمنع عمليات الكتابة والنشر حتى إلغاء التقييد."
                        else -> "ستتم إعادة الحساب إلى الحالة النشطة."
                    })
                    OutlinedTextField(
                        value = moderationReason,
                        onValueChange = { moderationReason = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("سبب الإجراء (يُحفظ في السجل)") },
                        minLines = 2
                    )
                    if (moderationAction == "RESTRICT") {
                        OutlinedTextField(
                            value = moderationDurationHours,
                            onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) moderationDurationHours = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("مدة التقييد بالساعات (اتركها فارغة للتقييد الدائم)") },
                            singleLine = true
                        )
                        Text("الحد المسموح: من ساعة إلى 8760 ساعة (سنة).", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = moderationAction != "RESTRICT" || moderationDurationHours.isBlank() || (moderationDurationHours.toIntOrNull() ?: 0) in 1..8760,
                    onClick = {
                    val action = moderationAction
                    val reason = moderationReason
                    val duration = if (action == "RESTRICT") moderationDurationHours.toIntOrNull() else null
                    moderationTarget = null
                    scope.launch {
                        val ok = admin?.moderateUser(user.id, action, reason, duration) == true
                        message = if (ok) "تم تنفيذ الإجراء وحفظه في سجل الإدارة" else "فشل الإجراء: ${admin?.lastError.orEmpty()}"
                        if (ok) refresh()
                    }
                }) { Text("تأكيد") }
            },
            dismissButton = { TextButton(onClick = { moderationTarget = null }) { Text("إلغاء") } }
        )
    }

    confirmDelete?.let { user ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("حذف الحساب نهائياً؟") },
            text = {
                Text("سيتم حذف حساب ${user.displayName.ifBlank { user.username.ifBlank { user.email } }} نهائياً، مع سجلاته المرتبطة وملفاته في مخازن post-media وstory-media وclips-media وclip-covers وprofile-media وmessage-media وnexa-pulse. لا يمكن التراجع عن الحذف.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        val deleted = admin?.deleteUser(user.id) == true
                        message = if (deleted) admin?.lastError ?: "تم حذف الحساب نهائياً والتحقق من تنظيف ملفاته في Storage وسجل العملية" else "فشل حذف الحساب: ${admin?.lastError ?: "راجع الاتصال وصلاحيات المالك"}"
                        refresh()
                    }
                }) { Text("تأكيد الحذف") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("إلغاء") } }
        )
    }
}



@Composable
private fun AdminUserMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdminUserInfoRow(label: String, value: String, copyable: Boolean = true) {
    val clipboard = LocalClipboardManager.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        if (copyable && value.isNotBlank() && value != "غير متوفر") {
            IconButton(onClick = { clipboard.setText(AnnotatedString(value)) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "نسخ $label", tint = Color(0xFF9B72FF))
            }
        }
    }
}

private fun formatAdminDate(value: String): String {
    if (value.isBlank()) return ""
    return runCatching {
        OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("dd MMM yyyy • HH:mm", Locale("ar")))
    }.getOrElse { value }
}


@Composable
private fun UserAdminCard(
    user: OwnerUserRow,
    isOwnerAccount: Boolean,
    onCardClick: () -> Unit,
    onRestrictClick: () -> Unit,
    onBlockClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val visibleName = user.displayName.trim().ifBlank {
        user.username.trim().ifBlank { user.email.substringBefore("@").ifBlank { "مستخدم Nexa" } }
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onCardClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AsyncImage(
                    model = user.avatarUrl.takeIf { it.isNotBlank() },
                    contentDescription = "صورة الحساب",
                    modifier = Modifier.size(52.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(visibleName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        listOf(user.username.takeIf { it.isNotBlank() }?.let { "@$it" }, user.email.takeIf { it.isNotBlank() })
                            .filterNotNull().joinToString(" • ").ifBlank { "حساب Nexa" },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        when (user.status) {
                            "BANNED" -> "محظور"
                            "SUSPENDED" -> "مقيّد"
                            "ACTIVE" -> "نشط"
                            else -> user.status
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = when (user.status) {
                            "BANNED", "SUSPENDED" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
                IconButton(onClick = onCardClick) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = "تفاصيل الحساب")
                }
            }
            Text(
                "المنشورات: ${user.postsCount} • المتابعون: ${user.followersCount} • يتابع: ${user.followingCount}",
                style = MaterialTheme.typography.bodySmall
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("البريد: ${user.email.ifBlank { "غير متوفر" }}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                if (user.email.isNotBlank()) {
                    IconButton(onClick = { clipboard.setText(AnnotatedString(user.email)) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "نسخ البريد", tint = Color(0xFF9B72FF), modifier = Modifier.size(17.dp))
                    }
                }
            }
            Text("التسجيل: ${formatAdminDate(user.createdAt)}", style = MaterialTheme.typography.bodySmall)
            Text("آخر دخول: ${formatAdminDate(user.lastSignInAt).ifBlank { "غير متوفر" }}", style = MaterialTheme.typography.labelSmall)
            if (user.restrictionExpiresAt.isNotBlank()) {
                Text("ينتهي التقييد: ${formatAdminDate(user.restrictionExpiresAt)}", style = MaterialTheme.typography.labelSmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isOwnerAccount) {
                    Text("إجراءات الإدارة معطلة لهذا الحساب لحمايته", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    IconButton(onClick = onRestrictClick) {
                        Icon(Icons.Default.Lock, contentDescription = if (user.status == "SUSPENDED") "إلغاء التقييد" else "تقييد الحساب")
                    }
                    IconButton(onClick = onBlockClick) {
                        Icon(Icons.Default.Block, contentDescription = if (user.status == "BANNED") "فك الحظر" else "حظر الحساب")
                    }
                    IconButton(onClick = onDeleteClick) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف الحساب نهائياً", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Text("اضغط البطاقة لعرض تفاصيل الحساب", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}


@Composable
private fun OwnerSettingRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}


@Composable
private fun OwnerAdsSettingsPanel(
    settings: JsonObject?,
    onSave: (Boolean, String, Int, Int) -> Unit
) {
    var enabled by remember { mutableStateOf(settings?.get("ads_enabled")?.asBoolean ?: false) }
    var placement by remember { mutableStateOf(settings?.get("ads_placement")?.asString ?: "FEED_INLINE") }
    var duration by remember { mutableStateOf((settings?.get("ads_duration_seconds")?.asInt ?: 15).toString()) }
    var frequency by remember { mutableStateOf((settings?.get("ads_frequency_per_session")?.asInt ?: 2).toString()) }

    LaunchedEffect(settings) {
        enabled = settings?.get("ads_enabled")?.asBoolean ?: false
        placement = settings?.get("ads_placement")?.asString ?: "FEED_INLINE"
        duration = (settings?.get("ads_duration_seconds")?.asInt ?: 15).toString()
        frequency = (settings?.get("ads_frequency_per_session")?.asInt ?: 2).toString()
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OwnerSettingRow("السماح بعرض الإعلانات", enabled) { enabled = it }
            Text("مكان ظهور الإعلان", style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    listOf("FEED_TOP" to "أعلى الرئيسية", "FEED_INLINE" to "داخل المنشورات"),
                    listOf("STORIES_BETWEEN" to "بين القصص", "PROFILE_FEED" to "الملف الشخصي")
                ).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowItems.forEach { (value, label) ->
                            FilterChip(selected = placement == value, onClick = { placement = value }, label = { Text(label, maxLines = 1) }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            OutlinedTextField(
                value = duration,
                onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) duration = it },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                label = { Text("مدة الإعلان بالثواني (5–120)") },
                singleLine = true
            )
            OutlinedTextField(
                value = frequency,
                onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) frequency = it },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                label = { Text("مرات الظهور لكل جلسة (1–10)") },
                singleLine = true
            )
            val durationValue = duration.toIntOrNull()
            val frequencyValue = frequency.toIntOrNull()
            Button(
                onClick = { onSave(enabled, placement, durationValue!!, frequencyValue!!) },
                enabled = durationValue != null && durationValue in 5..120 && frequencyValue != null && frequencyValue in 1..10,
                modifier = Modifier.fillMaxWidth()
            ) { Text("حفظ إعدادات الإعلانات") }
        }
    }
}

@Composable
private fun OwnerAdsManagementPanel(
    ads: List<OwnerAdRow>,
    onRefresh: () -> Unit,
    onSave: (String?,String,String,String,String,String,String,String,String,String,Int,Int,Boolean) -> Unit
) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var mediaUrl by remember { mutableStateOf("") }
    var mediaType by remember { mutableStateOf("NONE") }
    var clickUrl by remember { mutableStateOf("") }
    var placement by remember { mutableStateOf("FEED_INLINE") }
    var audience by remember { mutableStateOf("ALL") }
    var startsAt by remember { mutableStateOf("") }
    var endsAt by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("15") }
    var frequency by remember { mutableStateOf("2") }
    var enabled by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (editingId == null) "إنشاء حملة إعلانية" else "تعديل الحملة الإعلانية", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(title,{title=it},Modifier.fillMaxWidth(),label={Text("عنوان الإعلان")},singleLine=true)
        OutlinedTextField(body,{body=it},Modifier.fillMaxWidth(),label={Text("نص الإعلان")},minLines=3)
        Text("أدخل رابطاً عاماً مباشراً للصورة أو الفيديو. رفع الملفات من الجهاز غير متاح في هذه الشاشة حالياً.",style=MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("NONE" to "بدون","IMAGE" to "صورة","VIDEO" to "فيديو").forEach { (v,l) ->
                FilterChip(selected=mediaType==v,onClick={mediaType=v},label={Text(l)})
            }
        }
        if(mediaType!="NONE") OutlinedTextField(mediaUrl,{mediaUrl=it},Modifier.fillMaxWidth(),label={Text("رابط الوسائط")},singleLine=true)
        OutlinedTextField(clickUrl,{clickUrl=it},Modifier.fillMaxWidth(),label={Text("رابط الفتح عند الضغط")},singleLine=true)
        if(title.isNotBlank() || body.isNotBlank() || mediaUrl.isNotBlank()) {
            OwnerAdPreview(
                title = title,
                body = body,
                mediaUrl = mediaUrl,
                mediaType = mediaType,
                clickUrl = clickUrl,
                placement = placement,
                audience = audience
            )
        }
        Text("مكان الظهور")
        listOf("FEED_TOP" to "أعلى الرئيسية","FEED_INLINE" to "بين المنشورات","STORIES_BETWEEN" to "بين القصص","PROFILE_FEED" to "الملف الشخصي").forEach { (v,l) ->
            FilterChip(selected=placement==v,onClick={placement=v},label={Text(l)})
        }
        Text("الجمهور المستهدف")
        listOf("ALL" to "الجميع","NEW_USERS" to "المستخدمون الجدد","VERIFIED" to "الموثقون","FOLLOWERS" to "المتابعون").forEach { (v,l) ->
            FilterChip(selected=audience==v,onClick={audience=v},label={Text(l)})
        }
        OutlinedTextField(startsAt,{startsAt=it},Modifier.fillMaxWidth(),label={Text("تاريخ البداية ISO-8601")},placeholder={Text("2026-11-08T09:00:00Z")})
        OutlinedTextField(endsAt,{endsAt=it},Modifier.fillMaxWidth(),label={Text("تاريخ النهاية ISO-8601")},placeholder={Text("2026-12-08T09:00:00Z")})
        OutlinedTextField(duration,{if(it.length<=3&&it.all(Char::isDigit))duration=it},Modifier.fillMaxWidth(),label={Text("مدة العرض بالثواني (5–120)")})
        OutlinedTextField(frequency,{if(it.length<=2&&it.all(Char::isDigit))frequency=it},Modifier.fillMaxWidth(),label={Text("مرات العرض في الجلسة (1–10)")})
        OwnerSettingRow("تفعيل الحملة",enabled){enabled=it}
        Button(
            onClick={onSave(editingId,title,body,mediaUrl,mediaType,clickUrl,placement,audience,startsAt,endsAt,duration.toInt(),frequency.toInt(),enabled)},
            enabled=title.isNotBlank()&&body.isNotBlank()&&startsAt.isNotBlank()&&endsAt.isNotBlank()&&(duration.toIntOrNull() ?: 0) in 5..120&&(frequency.toIntOrNull() ?: 0) in 1..10&&(mediaType=="NONE"||mediaUrl.startsWith("http")),
            modifier=Modifier.fillMaxWidth()
        ) { Text(if(editingId==null)"إنشاء الحملة" else "حفظ التعديلات") }
        OutlinedButton(onClick={editingId=null;title="";body="";mediaUrl="";mediaType="NONE";clickUrl="";placement="FEED_INLINE";audience="ALL";startsAt="";endsAt="";duration="15";frequency="2";enabled=false},modifier=Modifier.fillMaxWidth()) { Text("حملة جديدة") }
        Text("الحملات المسجلة",style=MaterialTheme.typography.titleLarge)
        ads.forEach { ad ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(ad.title,style=MaterialTheme.typography.titleMedium)
                    Text(ad.body)
                    Text("الموضع: ${ad.placement} • الجمهور: ${ad.audience}")
                    Text("المشاهدات: ${ad.impressions} • النقرات: ${ad.clicks}")
                    Text(if(ad.enabled)"مفعّل" else "متوقف")
                    Text("${ad.startsAt} — ${ad.endsAt}",style=MaterialTheme.typography.labelSmall)
                    OutlinedButton(onClick={
                        editingId=ad.id;title=ad.title;body=ad.body;mediaUrl=ad.mediaUrl;mediaType=ad.mediaType
                        clickUrl=ad.clickUrl;placement=ad.placement;audience=ad.audience;startsAt=ad.startsAt;endsAt=ad.endsAt
                        duration=ad.durationSeconds.toString();frequency=ad.frequencyPerSession.toString();enabled=ad.enabled
                    }) { Text("تعديل الحملة") }
                }
            }
        }
        OutlinedButton(onClick=onRefresh,modifier=Modifier.fillMaxWidth()) { Text("تحديث الحملات") }
    }
}


@Composable
private fun OwnerAdPreview(
    title: String,
    body: String,
    mediaUrl: String,
    mediaType: String,
    clickUrl: String,
    placement: String,
    audience: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171329))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF7654D9)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Campaign, contentDescription = null, tint = Color.White)
                }
                Column(
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("NEXA BUSINESS", color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("محتوى ترويجي", color = Color(0xFFC9BCEB), style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    "إعلان ممول",
                    color = Color(0xFFE7DCFF),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF49366F))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (mediaType == "IMAGE" && mediaUrl.startsWith("http")) {
                AsyncImage(
                    model = mediaUrl,
                    contentDescription = "صورة الإعلان",
                    modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Crop
                )
            } else if (mediaType == "VIDEO" && mediaUrl.startsWith("http")) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(190.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF30264B)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Campaign, contentDescription = null, tint = Color(0xFFE0D3FF), modifier = Modifier.size(34.dp))
                        Text("رابط فيديو الإعلان محدد", color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Text("معاينة التشغيل غير متاحة هنا", color = Color(0xFFC9BCEB), style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF30264B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Campaign, contentDescription = null, tint = Color(0xFFD9C9FF), modifier = Modifier.size(42.dp))
                }
            }
            Text(title.ifBlank { "عنوان الإعلان" }, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(body.ifBlank { "سيظهر وصف الإعلان هنا" }, color = Color(0xFFE1D9EF), style = MaterialTheme.typography.bodyMedium)
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF7654D9)).padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (clickUrl.isBlank()) "اكتشف المزيد" else "اعرف المزيد", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("موضع: $placement", color = Color(0xFFC9BCEB), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text("الجمهور: $audience", color = Color(0xFFC9BCEB), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
