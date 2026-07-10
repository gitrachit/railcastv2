package app.railcast.core.net

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit surface for the Railcast BFF (docs/api-contracts.md). Every call
 * returns the wire envelope; the repository maps it to ApiResult. Bearer auth
 * is attached by AuthInterceptor (except /auth/device).
 */
interface RailcastApi {
    @POST("auth/device")
    suspend fun authDevice(@Body body: DeviceAuthRequest): Response<EnvelopeDto<DeviceAuthResponse>>

    @GET("screen/train/{trainNo}")
    suspend fun trainScreen(
        @Path("trainNo") trainNo: String,
        @Query("run") run: String = "auto",
    ): Response<EnvelopeDto<TrainScreen>>

    // The full PNR travels only here, in the TLS request path (FR-4.3). No HTTP
    // logging interceptor is installed (NetworkModule), so it is never logged.
    @GET("screen/pnr/{pnr}")
    suspend fun pnrScreen(@Path("pnr") pnr: String): Response<EnvelopeDto<PnrScreen>>

    @POST("watch")
    suspend fun createWatch(@Body body: WatchRequest): Response<EnvelopeDto<WatchCreated>>
}
