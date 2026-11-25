package ai.cosmic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.graphics.drawable.toBitmap
import ai.cosmic.data.preferences.AuthPreferences
import ai.cosmic.theme.GeminiTheme
import ai.cosmic.theme.isSystemInDarkThemeRobust
import ai.cosmic.ui.components.LocalTerminalDialog

class TerminalActivity : ComponentActivity() {

    private val authPreferences: AuthPreferences by lazy { AuthPreferences(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showTermuxIconInRecents()
        ai.cosmic.data.service.AntiGemService.start(this, fromForeground = true)

        setContent {
            val initialThemeMode = remember { authPreferences.getThemeModeSync() }
            val themeMode by authPreferences.themeMode.collectAsState(initial = initialThemeMode)
            val isSystemDark = isSystemInDarkThemeRobust()
            val useDarkTheme = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemDark
            }

            GeminiTheme(darkTheme = useDarkTheme) {
                LocalTerminalDialog(
                    onDismiss = { finish() }
                )
            }
        }
    }

    /**
     * Recents shows this activity's own icon (the AntiGem one) for the terminal task, even when it was opened from the
     * Termux-style launcher icon. When that style is on, give the task the Termux icon and name instead. It must be a
     * bitmap: the Pixel launcher's Recents ignores a task icon given as a resource id.
     */
    private fun showTermuxIconInRecents() {
        if (ai.cosmic.data.preferences.TerminalLauncherManager.getLauncherStyle(this) !=
            ai.cosmic.data.preferences.TerminalLauncherManager.STYLE_TERMUX
        ) return
        try {
            val drawable = androidx.core.content.ContextCompat.getDrawable(this, R.mipmap.ic_termux_launcher) ?: return
            val size = (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).launcherLargeIconSize
            val icon = drawable.toBitmap(size, size)
            @Suppress("DEPRECATION")
            setTaskDescription(android.app.ActivityManager.TaskDescription(getString(R.string.termux_name), icon))
        } catch (_: Exception) {
        }
    }
}
