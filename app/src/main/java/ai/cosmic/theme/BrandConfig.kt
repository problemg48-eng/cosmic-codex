package ai.cosmic.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Centralized branding configuration for the platform.
 *
 * Allows product name, tagline, accent palette, and metadata to be configured
 * in one place without modifying applicationId (`ai.cosmic`), signing identity,
 * user-data directories, or update channels.
 */
object BrandConfig {
    private const val PREFS_NAME = "brand_config"
    private const val KEY_PRODUCT_NAME = "product_name"
    private const val KEY_TAGLINE = "tagline"

    const val DEFAULT_PRODUCT_NAME = "Cosmic Codex"
    const val DEFAULT_TAGLINE = "Your AI-Powered Cosmic Development Platform"
    const val LICENSE_NAME = "GPL-3.0"

    var productName by mutableStateOf(DEFAULT_PRODUCT_NAME)
        private set

    var tagline by mutableStateOf(DEFAULT_TAGLINE)
        private set

    /** Primary brand palette tokens (resolved dynamically from active theme when appropriate). */
    val primaryAccent: Color get() = AppColors.primary
    val secondaryAccent: Color get() = AppColors.accentAlt
    val successAccent: Color get() = AppColors.success
    val warningAccent: Color get() = AppColors.warning
    val dangerAccent: Color get() = AppColors.danger

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        productName = prefs.getString(KEY_PRODUCT_NAME, DEFAULT_PRODUCT_NAME)
            ?.takeIf { it.isNotBlank() } ?: DEFAULT_PRODUCT_NAME
        tagline = prefs.getString(KEY_TAGLINE, DEFAULT_TAGLINE)
            ?.takeIf { it.isNotBlank() } ?: DEFAULT_TAGLINE
    }

    fun updateBranding(context: Context, newName: String, newTagline: String) {
        val cleanName = newName.trim().ifEmpty { DEFAULT_PRODUCT_NAME }
        val cleanTagline = newTagline.trim().ifEmpty { DEFAULT_TAGLINE }
        productName = cleanName
        tagline = cleanTagline
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PRODUCT_NAME, cleanName)
            .putString(KEY_TAGLINE, cleanTagline)
            .apply()
    }

    fun reset(context: Context) {
        updateBranding(context, DEFAULT_PRODUCT_NAME, DEFAULT_TAGLINE)
    }
}
