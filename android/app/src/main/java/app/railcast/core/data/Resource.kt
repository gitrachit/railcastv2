package app.railcast.core.data

import app.railcast.core.net.ApiErrorDto

/**
 * One emission of the SWR stream. `value` is the last-known data (cached, then
 * fresh). `freshness` is the server fetch time for the "updated Xs ago" stamp
 * (FR-2.5); `stale` marks cached-past-TTL or offline data (FR-9.1); `loading`
 * is true while a refresh is in flight; `error` is set when the refresh failed
 * (the value may still be a usable cached copy — degrade, never block).
 */
data class Resource<T>(
    val value: T?,
    val freshness: String?,
    val stale: Boolean,
    val loading: Boolean,
    val error: ApiErrorDto?,
) {
    val hasValue: Boolean get() = value != null
}
