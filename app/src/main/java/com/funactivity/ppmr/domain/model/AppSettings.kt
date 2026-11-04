package com.funactivity.ppmr.domain.model

/** User-controlled sync preferences. Defaults here are the app's documented defaults. */
data class AppSettings(
    val wifiOnly: Boolean = true,
    val autoSync: Boolean = true,
    val includeSubfolders: Boolean = false,
    val successNotifications: Boolean = false,
    val minFileAgeSeconds: Int = DEFAULT_MIN_AGE_SECONDS,
) {
    companion object {
        const val DEFAULT_MIN_AGE_SECONDS = 30
        val MIN_AGE_OPTIONS = listOf(10, 20, 30, 60)

        /** Falls back to the default rather than trusting an out-of-range stored value. */
        fun normalizeMinAge(seconds: Int?): Int =
            seconds?.takeIf { it in MIN_AGE_OPTIONS } ?: DEFAULT_MIN_AGE_SECONDS
    }
}
