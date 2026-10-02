package id.sehati.app.data.sync

import id.sehati.app.core.security.SecureStore
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.IOException
import java.util.concurrent.TimeUnit

interface SehatiApi {
    @POST("sync/push")
    suspend fun push(@Body body: SyncPushRequest): SyncPushResponse
}

/** Transport nyata via HTTPS. BASE_URL berasal dari build config (debug/staging/release), tidak di-hardcode. */
class HttpSyncTransport(
    private val baseUrl: String,
    private val secure: SecureStore,
    json: Json,
) : SyncTransport {
    override val label = "Server SEHATI"

    private val api: SehatiApi? by lazy {
        if (!isConfigured()) return@lazy null
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(Interceptor { chain ->
                val b = chain.request().newBuilder()
                secure.getString("api_token")?.let { b.header("Authorization", "Bearer $it") }
                chain.proceed(b.build())
            })
            .build() // tidak ada logging body: data kesehatan tidak boleh masuk log
        Retrofit.Builder().baseUrl(baseUrl).client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(SehatiApi::class.java)
    }

    private fun isConfigured() = baseUrl.startsWith("http") && !baseUrl.contains(".invalid")

    override suspend fun push(request: SyncPushRequest): TransportResult {
        val api = api ?: return TransportResult.NotConfigured("Alamat server belum dikonfigurasi pada build ini.")
        return try {
            TransportResult.Success(api.push(request).results)
        } catch (e: IOException) {
            TransportResult.Offline("Koneksi terputus atau server tidak terjangkau.")
        } catch (e: retrofit2.HttpException) {
            if (e.code() in 500..599 || e.code() == 429) TransportResult.Offline("Server sedang sibuk (${e.code()}).")
            else TransportResult.Success(request.items.map { SyncAckDto(it.id, AckStatus.REJECTED, null, "Ditolak server (${e.code()})") })
        }
    }

}
