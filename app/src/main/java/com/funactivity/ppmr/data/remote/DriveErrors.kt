package com.funactivity.ppmr.data.remote

import com.funactivity.ppmr.domain.model.DriveApiException
import com.funactivity.ppmr.domain.model.DriveErrorKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
private data class ErrorEnvelope(val error: ErrorBody? = null)

@Serializable
private data class ErrorBody(
    val message: String? = null,
    val errors: List<ErrorItem> = emptyList(),
)

@Serializable
private data class ErrorItem(val reason: String? = null)

private val errorJson = Json { ignoreUnknownKeys = true }

/** Classifies a failed Drive response. 403 can mean several things, so the reason is checked. */
internal fun parseDriveError(httpCode: Int, body: String?): DriveApiException {
    val parsed = try {
        body?.takeIf { it.isNotBlank() }?.let { errorJson.decodeFromString<ErrorEnvelope>(it).error }
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
    val reasons = parsed?.errors.orEmpty().mapNotNull { it.reason }
    val kind = when {
        httpCode == 401 -> DriveErrorKind.AUTH
        reasons.any { it in QUOTA_REASONS } -> DriveErrorKind.QUOTA_EXCEEDED
        reasons.any { it in AUTH_REASONS } -> DriveErrorKind.AUTH
        reasons.any { it in RATE_LIMIT_REASONS } -> DriveErrorKind.TRANSIENT
        httpCode == 404 -> DriveErrorKind.NOT_FOUND
        httpCode == 408 || httpCode == 429 || httpCode >= 500 -> DriveErrorKind.TRANSIENT
        else -> DriveErrorKind.PERMANENT
    }
    return DriveApiException(kind, httpCode, parsed?.message ?: "Drive returned HTTP $httpCode")
}

private val QUOTA_REASONS = setOf("storageQuotaExceeded", "quotaExceeded")
private val AUTH_REASONS = setOf("insufficientPermissions", "insufficientScopes", "authError")
private val RATE_LIMIT_REASONS = setOf(
    "rateLimitExceeded",
    "userRateLimitExceeded",
    "dailyLimitExceeded",
    "backendError",
    "internalError",
)
