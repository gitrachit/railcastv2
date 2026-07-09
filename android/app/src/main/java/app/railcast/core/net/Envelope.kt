package app.railcast.core.net

import kotlinx.serialization.Serializable
import retrofit2.Response

// Wire envelope — mirrors docs/api-contracts.md §0 field-for-field.
@Serializable
data class MetaDto(
    val fetchedAt: String,
    val stale: Boolean,
    val ttlSeconds: Int,
)

@Serializable
data class ApiErrorDto(
    val code: String,
    val message: String,
    val retryable: Boolean,
)

@Serializable
data class EnvelopeDto<T>(
    val ok: Boolean,
    val data: T? = null,
    val meta: MetaDto? = null,
    val error: ApiErrorDto? = null,
)

/** Parsed result of one API call. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val data: T, val meta: MetaDto) : ApiResult<T>
    data class Err(val error: ApiErrorDto, val httpStatus: Int?) : ApiResult<Nothing>
}

/** Error codes from contracts §0 (client-synthesised ones marked). */
object ErrorCodes {
    const val UPSTREAM_DOWN = "UPSTREAM_DOWN"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val INVALID_INPUT = "INVALID_INPUT"
}

/**
 * Runs a Retrofit call and normalises it to ApiResult: success envelope →
 * Ok; a non-2xx that still carries an Err envelope → Err with its code;
 * anything else (network failure, unparseable body) → a synthetic retryable
 * UPSTREAM_DOWN so the repository can fall back to cache (FR-9.1).
 */
suspend fun <T> apiResult(
    parseError: (String) -> ApiErrorDto?,
    block: suspend () -> Response<EnvelopeDto<T>>,
): ApiResult<T> {
    return try {
        val response = block()
        val body = response.body()
        if (response.isSuccessful && body?.ok == true && body.data != null && body.meta != null) {
            ApiResult.Ok(body.data, body.meta)
        } else {
            val err = body?.error
                ?: response.errorBody()?.string()?.let(parseError)
                ?: ApiErrorDto(ErrorCodes.UPSTREAM_DOWN, "unexpected response", retryable = true)
            ApiResult.Err(err, response.code())
        }
    } catch (e: Exception) {
        ApiResult.Err(
            ApiErrorDto(ErrorCodes.UPSTREAM_DOWN, e.message ?: "network error", retryable = true),
            httpStatus = null,
        )
    }
}
