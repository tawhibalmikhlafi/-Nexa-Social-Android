package com.nexa.social.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexa.social.core.ui.rememberAppContainer
import com.nexa.social.ui.components.NexaTopBar

@Composable
fun ProfileDetailsScreen(
    onBack: () -> Unit,
    profileViewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModel.factory(null),
        key = "profile-details-self"
    )
) {
    val user by profileViewModel.user.collectAsStateWithLifecycle()
    val dataSource = rememberAppContainer().profileExperienceDataSource

    Column(modifier = Modifier.fillMaxSize()) {
        NexaTopBar(title = "تفاصيل الملف الشخصي", onBack = onBack)
        val currentUser = user
        when {
            currentUser == null -> Text(
                text = "جارٍ تحميل تفاصيل الملف الشخصي...",
                modifier = Modifier.padding(20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            dataSource == null -> Text(
                text = "تعذر الوصول إلى بيانات الملف الشخصي. تحقق من اتصال Supabase ثم أعد المحاولة.",
                modifier = Modifier.padding(20.dp),
                color = MaterialTheme.colorScheme.error
            )
            else -> ProfileExperiencePanel(
                profileId = currentUser.id,
                isOwner = true,
                dataSource = dataSource
            )
        }
    }
}
