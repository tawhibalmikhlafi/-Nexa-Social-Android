package com.nexa.social

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.nexa.social.core.auth.AuthCallbackRouter
import com.nexa.social.core.auth.AuthLinkParser
import com.nexa.social.core.common.FailureReason
import com.nexa.social.core.common.LocaleManager
import com.nexa.social.core.common.Resource
import com.nexa.social.core.diagnostics.CrashReporter
import com.nexa.social.core.navigation.DeepLinkRouter
import com.nexa.social.core.navigation.NexaRoot
import com.nexa.social.core.notifications.NotificationRouter
import com.nexa.social.core.ui.failureMessageRes
import com.nexa.social.domain.model.ThemeConfig
import com.nexa.social.ui.theme.NexaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationRouter.handleIntent(intent)
        handleAuthDeepLink(intent)
        handleContentDeepLink(intent)
        val container = (application as NexaApplication).container

        setContent {
            val themeConfig by container.themeRepository.themeConfig
                .collectAsStateWithLifecycle(initialValue = ThemeConfig())
            val fontScale by container.preferenceStorage.fontScale
                .collectAsStateWithLifecycle(initialValue = 1.0f)
            var crashReport by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(Unit) {
                crashReport = withContext(Dispatchers.IO) {
                    CrashReporter.consumeLastCrash(applicationContext)
                }
            }

            NexaTheme(config = themeConfig, fontScale = fontScale) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NexaRoot()
                }

                // Clear, local-only alert about the previous unexpected close.
                crashReport?.let { report ->
                    AlertDialog(
                        onDismissRequest = { crashReport = null },
                        confirmButton = {
                            TextButton(onClick = { crashReport = null }) {
                                Text(stringResource(R.string.action_ok))
                            }
                        },
                        title = { Text(stringResource(R.string.crash_dialog_title)) },
                        text = {
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 280.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = stringResource(R.string.crash_dialog_body),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = report,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
            }
        }

        // Session sync and remote profile loading touch the network. Start them only
        // after the first frame so a slow or failing connection can never block the app opening.
        window.decorView.post { container.startBackgroundSync() }

        // Requested only after the content view exists so the system permission activity is
        // launched from a fully started window, not while the app is still constructing itself.
        requestNotificationPermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        NotificationRouter.handleIntent(intent)
        handleAuthDeepLink(intent)
        handleContentDeepLink(intent)
    }

    private fun handleContentDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (DeepLinkRouter.handle(data)) {
            // Consume the link so a configuration change does not navigate twice.
            intent.data = null
        }
    }

    private fun handleAuthDeepLink(intent: Intent?) {
        val dataString = intent?.dataString ?: return
        val params = AuthLinkParser.parse(dataString) ?: return
        // Do not process the same one-time tokens again after a configuration change.
        intent.data = null
        if (!params.error.isNullOrBlank()) {
            showAuthLinkError()
            return
        }
        val accessToken = params.accessToken
        val refreshToken = params.refreshToken
        if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank()) {
            showAuthLinkError()
            return
        }
        val repository = (application as NexaApplication).container.authRepository
        lifecycleScope.launch {
            when (val result = repository.completeAuthCallback(accessToken, refreshToken)) {
                is Resource.Success -> {
                    if (params.isRecovery) AuthCallbackRouter.requirePasswordReset()
                    recreate()
                }
                is Resource.Failure ->
                    if (result.reason == FailureReason.INVALID_CODE) showAuthLinkError()
                    else showAuthError(result.reason)
                Resource.Loading -> Unit
            }
        }
    }

    private fun showAuthLinkError() {
        Toast.makeText(this, R.string.error_invalid_link, Toast.LENGTH_LONG).show()
    }

    private fun showAuthError(reason: FailureReason) {
        Toast.makeText(this, getString(failureMessageRes(reason)), Toast.LENGTH_LONG).show()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
    }

    private companion object {
        const val NOTIFICATION_REQUEST = 1001
    }
}
