package com.nexa.social.feature.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.nexa.social.core.ui.rememberAppContainer
import com.nexa.social.data.remote.OwnerReportRow
import com.nexa.social.data.remote.OwnerUserRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerDashboardScreen(onBack: () -> Unit) {
    val admin = rememberAppContainer().ownerAdminDataSource
    val scope = rememberCoroutineScope()
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var stats by remember { mutableStateOf<JsonObject?>(null) }
    var users by remember { mutableStateOf<List<OwnerUserRow>>(emptyList()) }
    var reports by remember { mutableStateOf<List<OwnerReportRow>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var announcementTitle by remember { mutableStateOf("") }
    var announcementBody by remember { mutableStateOf("") }
    var officialPost by remember { mutableStateOf("") }
    var settings by remember { mutableStateOf<JsonObject?>(null) }
    var confirmDelete by remember { mutableStateOf<OwnerUserRow?>(null) }

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
                stats = source.dashboardStats()
                users = source.listUsers(search)
                reports = source.listReports()
                settings = source.getSettings()
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إدارة Nexa • لوحة المالك") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = { IconButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, contentDescription = "تحديث") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (loading && allowed == null) {
                CircularProgressIndicator()
            } else if (allowed != true) {
                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text("غير مصرح بالدخول", style = MaterialTheme.typography.titleLarge)
                Text(message ?: "هذه المساحة مخصصة لمالك Nexa فقط. تحقق من تسجيل الدخول بحساب المالك.")
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("المستخدمون", stats?.get("users_total")?.asString ?: "—", Modifier.weight(1f))
                    StatCard("تسجيلات اليوم", stats?.get("users_today")?.asString ?: "—", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("المنشورات", stats?.get("posts_total")?.asString ?: "—", Modifier.weight(1f))
                    StatCard("البلاغات المفتوحة", stats?.get("reports_open")?.asString ?: "—", Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                TabRow(selectedTabIndex = tab) {
                    listOf("المستخدمون", "البلاغات", "الإعلانات", "المنشورات الرسمية", "الإعدادات").forEachIndexed { i, label ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
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
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(user.displayName.ifBlank { user.username }, style = MaterialTheme.typography.titleMedium)
                                        Text("@${user.username} • ${user.email}")
                                        Text("التسجيل: ${user.createdAt}")
                                        Text("الحالة: ${user.status}")
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick = {
                                                scope.launch {
                                                    val next = if (user.status == "SUSPENDED") "ACTIVE" else "SUSPENDED"
                                                    message = if (admin?.setUserStatus(user.id, next) == true) "تم تحديث حالة الحساب" else "تعذر تحديث الحالة"
                                                    refresh()
                                                }
                                            }) { Text(if (user.status == "SUSPENDED") "إلغاء التقييد" else "تقييد") }
                                            OutlinedButton(onClick = { confirmDelete = user }) {
                                                Icon(Icons.Default.Delete, contentDescription = null)
                                                Text("حذف")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(reports, key = { it.id }) { report ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("${report.targetType} • ${report.reason}", style = MaterialTheme.typography.titleMedium)
                                    Text(report.note)
                                    Text("الحالة: ${report.status} • ${report.createdAt}")
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { scope.launch { admin?.updateReport(report.id, "REVIEWED"); refresh() } }) { Text("تمت المراجعة") }
                                        OutlinedButton(onClick = { scope.launch { admin?.updateReport(report.id, "DISMISSED"); refresh() } }) { Text("رفض البلاغ") }
                                    }
                                }
                            }
                        }
                    }
                    2 -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(announcementTitle, { announcementTitle = it }, Modifier.fillMaxWidth(), label = { Text("عنوان الإعلان") })
                        OutlinedTextField(announcementBody, { announcementBody = it }, Modifier.fillMaxWidth(), minLines = 4, label = { Text("نص الإعلان") })
                        Button(onClick = {
                            scope.launch {
                                val ok = admin?.createAnnouncement(announcementTitle, announcementBody) == true
                                message = if (ok) "تم نشر الإعلان" else "فشل نشر الإعلان"
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
                                message = if (ok) "تم نشر المنشور" else "تعذر النشر؛ تحقق من صلاحية إدراج المنشورات"
                                if (ok) officialPost = ""
                            }
                        }, enabled = officialPost.isNotBlank()) { Text("نشر باسم حساب المالك") }
                        OutlinedButton(onClick = {
                            scope.launch {
                                message = if (admin?.enableFounderBadge() == true) "تم تفعيل شارة مؤسس Nexa" else "تعذر تفعيل الشارة"
                            }
                        }) { Icon(Icons.Default.AdminPanelSettings, contentDescription = null); Text("تفعيل شارة مؤسس Nexa") }
                    }
                    else -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("إعدادات التشغيل العامة • التغييرات تحفظ مباشرة في Supabase")
                        OwnerSettingRow("وضع الصيانة", settings?.get("maintenance_mode")?.asBoolean ?: false) { enabled ->
                            scope.launch {
                                val ok = admin?.setSetting("maintenance_mode", enabled) == true
                                message = if (ok) "تم حفظ وضع الصيانة" else "تعذر حفظ الإعداد"
                                settings = admin?.getSettings()
                            }
                        }
                        OwnerSettingRow("السماح بالتسجيل الجديد", settings?.get("new_registrations_enabled")?.asBoolean ?: true) { enabled ->
                            scope.launch {
                                val ok = admin?.setSetting("new_registrations_enabled", enabled) == true
                                message = if (ok) "تم حفظ إعداد التسجيل" else "تعذر حفظ الإعداد"
                                settings = admin?.getSettings()
                            }
                        }
                        OwnerSettingRow("تفعيل شارة التوثيق الرسمية", settings?.get("official_badge_enabled")?.asBoolean ?: true) { enabled ->
                            scope.launch {
                                val ok = admin?.setSetting("official_badge_enabled", enabled) == true
                                message = if (ok) "تم حفظ إعداد الشارة" else "تعذر حفظ الإعداد"
                                settings = admin?.getSettings()
                            }
                        }
                    }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
    }

    confirmDelete?.let { user ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("حذف الحساب نهائياً؟") },
            text = { Text("سيتم حذف حساب ${user.email} من Supabase Auth. لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        message = if (admin?.deleteUser(user.id) == true) "تم حذف الحساب" else "فشل حذف الحساب"
                        refresh()
                    }
                }) { Text("تأكيد الحذف") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("إلغاء") } }
        )
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
