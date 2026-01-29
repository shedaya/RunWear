package com.runwear.app.debug

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.runwear.app.BuildConfig
import com.runwear.app.ui.viewmodel.MainUiState
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug manager for capturing logs, app state, and sending feedback.
 */
object DebugManager {

    private val logBuffer = mutableListOf<String>()
    private const val MAX_LOG_LINES = 500

    /**
     * Log a debug message (also goes to logcat).
     */
    fun log(tag: String, message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val logLine = "[$timestamp] $tag: $message"
        synchronized(logBuffer) {
            logBuffer.add(logLine)
            if (logBuffer.size > MAX_LOG_LINES) {
                logBuffer.removeAt(0)
            }
        }
        android.util.Log.d("RunWear/$tag", message)
    }

    /**
     * Get recent logs from logcat.
     */
    private fun getLogcatLogs(): String {
        return try {
            val process = Runtime.getRuntime().exec("logcat -d -t 200 *:W RunWear:D")
            val reader = InputStreamReader(process.inputStream)
            val logs = reader.readText()
            reader.close()
            logs
        } catch (e: Exception) {
            "Failed to capture logcat: ${e.message}"
        }
    }

    /**
     * Generate a full debug report.
     */
    fun generateDebugReport(
        context: Context,
        uiState: MainUiState?,
        userFeedback: String = ""
    ): String {
        val sb = StringBuilder()

        sb.appendLine("=" .repeat(60))
        sb.appendLine("RUNWEAR DEBUG REPORT")
        sb.appendLine("=" .repeat(60))
        sb.appendLine()

        // Timestamp
        sb.appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())}")
        sb.appendLine()

        // User Feedback
        if (userFeedback.isNotBlank()) {
            sb.appendLine("-".repeat(40))
            sb.appendLine("USER FEEDBACK:")
            sb.appendLine("-".repeat(40))
            sb.appendLine(userFeedback)
            sb.appendLine()
        }

        // App Info
        sb.appendLine("-".repeat(40))
        sb.appendLine("APP INFO:")
        sb.appendLine("-".repeat(40))
        sb.appendLine("Package: ${BuildConfig.APPLICATION_ID}")
        sb.appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        sb.appendLine("Build Type: ${BuildConfig.BUILD_TYPE}")
        sb.appendLine("Debug: ${BuildConfig.DEBUG}")
        sb.appendLine()

        // Device Info
        sb.appendLine("-".repeat(40))
        sb.appendLine("DEVICE INFO:")
        sb.appendLine("-".repeat(40))
        sb.appendLine("Manufacturer: ${Build.MANUFACTURER}")
        sb.appendLine("Model: ${Build.MODEL}")
        sb.appendLine("Device: ${Build.DEVICE}")
        sb.appendLine("Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine("Display: ${context.resources.displayMetrics.widthPixels}x${context.resources.displayMetrics.heightPixels}")
        sb.appendLine("Density: ${context.resources.displayMetrics.density}")
        sb.appendLine()

        // App State
        if (uiState != null) {
            sb.appendLine("-".repeat(40))
            sb.appendLine("APP STATE:")
            sb.appendLine("-".repeat(40))
            sb.appendLine("Loading: ${uiState.isLoading}")
            sb.appendLine("Error: ${uiState.error ?: "none"}")
            sb.appendLine("Location: ${uiState.locationName}")
            sb.appendLine("Location Source: ${uiState.locationSource}")
            sb.appendLine("Has Location Permission: ${uiState.hasLocationPermission}")
            sb.appendLine("Selected DateTime: ${uiState.selectedDateTime}")
            sb.appendLine("Temperature Unit: ${uiState.temperatureUnit}")
            sb.appendLine("Comfort Preference: ${uiState.comfortPreference}")
            sb.appendLine("Gender Preference: ${uiState.genderPreference}")
            sb.appendLine("Onboarding Complete: ${uiState.hasCompletedOnboarding}")
            sb.appendLine()

            // Weather
            uiState.weather?.let { weather ->
                sb.appendLine("-".repeat(40))
                sb.appendLine("WEATHER DATA:")
                sb.appendLine("-".repeat(40))
                sb.appendLine("Temperature: ${weather.temperature}°")
                sb.appendLine("Feels Like: ${weather.feelsLike}°")
                sb.appendLine("Humidity: ${weather.humidity}%")
                sb.appendLine("Wind Speed: ${weather.windSpeed}")
                sb.appendLine("Wind Gusts: ${weather.windGusts}")
                sb.appendLine("Weather Code: ${weather.weatherCode} (${weather.weatherCode.description})")
                sb.appendLine("Precipitation Prob: ${weather.precipitationProbability}%")
                sb.appendLine("UV Index: ${weather.uvIndex}")
                sb.appendLine("Cloud Cover: ${weather.cloudCover}%")
                sb.appendLine("Is Celsius: ${weather.isCelsius}")
                sb.appendLine()
            }

            // Outfit
            uiState.outfit?.let { outfit ->
                sb.appendLine("-".repeat(40))
                sb.appendLine("OUTFIT RECOMMENDATION:")
                sb.appendLine("-".repeat(40))
                outfit.allItems.forEach { item ->
                    sb.appendLine("- ${item.icon} ${item.displayName} (${item.category})")
                }
                if (outfit.tips.isNotEmpty()) {
                    sb.appendLine("Tips:")
                    outfit.tips.forEach { tip ->
                        sb.appendLine("  • $tip")
                    }
                }
                sb.appendLine()
            }

            // Hero Image
            sb.appendLine("-".repeat(40))
            sb.appendLine("HERO IMAGE:")
            sb.appendLine("-".repeat(40))
            sb.appendLine("URL: ${uiState.heroImageUrl ?: "null"}")
            sb.appendLine()
        }

        // In-app Logs
        sb.appendLine("-".repeat(40))
        sb.appendLine("IN-APP LOGS (recent ${logBuffer.size}):")
        sb.appendLine("-".repeat(40))
        synchronized(logBuffer) {
            logBuffer.takeLast(100).forEach { sb.appendLine(it) }
        }
        sb.appendLine()

        // Logcat
        sb.appendLine("-".repeat(40))
        sb.appendLine("LOGCAT (recent):")
        sb.appendLine("-".repeat(40))
        sb.appendLine(getLogcatLogs())

        return sb.toString()
    }

    /**
     * Share debug report via intent.
     */
    fun shareDebugReport(
        context: Context,
        uiState: MainUiState?,
        userFeedback: String = ""
    ) {
        val report = generateDebugReport(context, uiState, userFeedback)

        // Save to file for attachment
        val file = File(context.cacheDir, "runwear_debug_${System.currentTimeMillis()}.txt")
        file.writeText(report)

        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "RunWear Debug Report - ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())}")
            putExtra(Intent.EXTRA_TEXT, "Debug report attached. User feedback:\n\n$userFeedback")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Send Debug Report"))
    }
}
