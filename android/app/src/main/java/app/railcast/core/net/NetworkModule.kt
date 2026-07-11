package app.railcast.core.net

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Builds the Retrofit client. `ignoreUnknownKeys` keeps the app forward-
 * compatible if the server adds fields ahead of an app release.
 */
object NetworkModule {
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun railcastApi(baseUrl: String, session: DeviceSession): RailcastApi {
        // The authenticator re-mints through the API it protects; the reference
        // is set right after Retrofit builds it (the mint call itself carries no
        // Authorization header, so there is no recursion).
        val apiRef = java.util.concurrent.atomic.AtomicReference<RailcastApi?>(null)
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(session.tokenProvider))
            .authenticator(TokenAuthenticator(session) { apiRef.get() })
            .build()
        val api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RailcastApi::class.java)
        apiRef.set(api)
        return api
    }

    /** Parse a server Err envelope out of a non-2xx body (for apiResult). */
    fun parseError(body: String): ApiErrorDto? = runCatching {
        json.decodeFromString(EnvelopeDto.serializer(ApiErrorDto.serializer()), body).error
    }.getOrNull()
}
