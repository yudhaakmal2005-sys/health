package id.sehati.app.data.remote

import id.sehati.app.BuildConfig
import id.sehati.app.core.security.SecureStore
import id.sehati.app.data.prefs.SettingsStore
import id.sehati.app.data.sync.SyncPushRequest
import id.sehati.app.data.sync.SyncPushResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Job
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.IOException
import java.util.concurrent.TimeUnit

interface SehatiServerApi {
    @POST("auth/login") suspend fun login(@Body body: LoginRequest): LoginResponse
    @POST("auth/register") suspend fun register(@Body body: RegisterRequest): LoginResponse
    @POST("auth/activate") suspend fun activate(@Body body: ActivateRequest): LoginResponse
    @POST("auth/logout") suspend fun logout(): retrofit2.Response<Unit>
    @POST("ids/reserve") suspend fun reserve(@Body body: ReserveRequest): ReserveResponse
    @POST("sync/push") suspend fun push(@Body body: SyncPushRequest): SyncPushResponse
    @GET("sync/pull") suspend fun pull(@Query("cursor") cursor: Long, @Query("limit") limit: Int = 500): PullResponse
    @GET("config") suspend fun config(): RemoteConfig
    @GET("health") suspend fun health(): ServerHealth
    @POST("admin/cadres") suspend fun createCadre(@Body body: NewCadreRequest): NewCadreResponse
}

/**
 * Klien server SEHATI (VPS). Alamat diambil dari pengaturan (dapat diubah tanpa build ulang) atau BuildConfig.
 * Token disimpan terenkripsi; isi data kesehatan tidak pernah dicatat ke log.
 */
class ServerClient(
    private val settings: SettingsStore,
    private val secure: SecureStore,
    private val json: Json,
    private val defaultBaseUrl: String = BuildConfig.BASE_URL,
) {
    private var cached: Pair<String, SehatiServerApi>? = null
    private var http: OkHttpClient? = null

    suspend fun baseUrl(): String? = normalize(settings.current().serverUrl.ifBlank { defaultBaseUrl }, BuildConfig.DEBUG)

    suspend fun isConfigured(): Boolean = baseUrl() != null

    val hasToken: Boolean get() = token() != null
    fun tokenOwner(): String? = secure.getString(KEY_OWNER)

    fun token(): String? {
        val t = secure.getString(KEY_TOKEN) ?: return null
        val exp = secure.getString(KEY_EXP)?.toLongOrNull() ?: 0
        return if (exp > System.currentTimeMillis()) t else null
    }

    fun saveToken(r: LoginResponse) {
        secure.putString(KEY_TOKEN, r.token); secure.putString(KEY_EXP, r.expiresAt.toString()); secure.putString(KEY_OWNER, r.user.sehatiId)
    }

    /** Mencabut token di server (upaya terbaik, latar belakang). Token lokal dihapus segera oleh pemanggil. */
    suspend fun revoke(token: String) {
        val base = baseUrl() ?: return
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching {
                OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build().newCall(
                    Request.Builder().url(base + "auth/logout").header("Authorization", "Bearer $token")
                        .post(ByteArray(0).toRequestBody(null)).build(),
                ).execute().close()
            }
        }
    }

    fun clearToken() { secure.remove(KEY_TOKEN); secure.remove(KEY_EXP); secure.remove(KEY_OWNER) }

    private fun client(): OkHttpClient = http ?: OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(Interceptor { chain ->
            val b = chain.request().newBuilder().header("Accept-Language", "id")
            token()?.let { b.header("Authorization", "Bearer $it") }
            chain.proceed(b.build())
        })
        .build().also { http = it } // tanpa logging body: data kesehatan tidak boleh masuk log

    private suspend fun api(): SehatiServerApi? {
        val base = baseUrl() ?: return null
        cached?.let { if (it.first == base) return it.second }
        val api = Retrofit.Builder().baseUrl(base).client(client())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(SehatiServerApi::class.java)
        cached = base to api
        return api
    }

    suspend fun <T> call(block: suspend (SehatiServerApi) -> T): ApiResult<T> {
        val api = api() ?: return ApiResult.NotConfigured
        return try {
            ApiResult.Ok(block(api))
        } catch (e: HttpException) {
            val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            val err = body?.let { runCatching { json.decodeFromString(ApiErrorBody.serializer(), it).error }.getOrNull() }
            if (e.code() == 401) clearToken()
            ApiResult.Failure(e.code(), err?.code.orEmpty(), err?.message?.ifBlank { null } ?: defaultMessage(e.code()))
        } catch (e: IOException) {
            ApiResult.Offline("Tidak dapat terhubung ke server. Periksa koneksi internet.")
        } catch (e: kotlinx.serialization.SerializationException) {
            ApiResult.Failure(0, "BAD_RESPONSE", "Jawaban server tidak dikenali. Pastikan alamat server benar.")
        }
    }

    /** Tanya SEHATI: aliran Server-Sent Events dari `/ai/chat`. Dibatalkan otomatis saat pengumpul berhenti. */
    fun chat(request: ChatRequest): Flow<ChatEvent> = flow {
        val base = baseUrl()
        if (base == null) { emit(ChatEvent.Failure("NOT_CONFIGURED", "Server belum diatur.")); return@flow }
        val tok = token()
        if (tok == null) { emit(ChatEvent.Failure("NO_TOKEN", "Masuk ke server terlebih dahulu.")); return@flow }
        val req = Request.Builder().url(base + "ai/chat")
            .header("Accept", "text/event-stream")
            .post(json.encodeToString(ChatRequest.serializer(), request).toRequestBody("application/json".toMediaType()))
            .build()
        val call = client().newCall(req)
        currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = runCatching { json.decodeFromString(ApiErrorBody.serializer(), resp.body.string()).error }.getOrNull()
                    if (resp.code == 401) clearToken()
                    emit(ChatEvent.Failure(err?.code?.ifBlank { null } ?: "HTTP_${resp.code}", err?.message?.ifBlank { null } ?: defaultMessage(resp.code)))
                    return@use
                }
                val source = resp.body.source()
                var event = "message"
                val data = StringBuilder()
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    when {
                        line.isEmpty() -> {
                            if (data.isNotEmpty()) parseEvent(event, data.toString())?.let { emit(it) }
                            event = "message"; data.clear()
                        }
                        line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                        line.startsWith("data:") -> { if (data.isNotEmpty()) data.append('\n'); data.append(line.removePrefix("data:").trimStart()) }
                    }
                }
                if (data.isNotEmpty()) parseEvent(event, data.toString())?.let { emit(it) }
            }
        } catch (e: IOException) {
            if (!call.isCanceled()) emit(ChatEvent.Failure("OFFLINE", "Koneksi terputus."))
        }
    }.flowOn(Dispatchers.IO)

    private fun parseEvent(event: String, data: String): ChatEvent? = runCatching {
        val o = json.parseToJsonElement(data).jsonObject
        when (event) {
            "meta" -> ChatEvent.Meta(o["emergency"]?.jsonPrimitive?.boolean ?: false)
            "delta" -> ChatEvent.Delta(o["text"]?.jsonPrimitive?.content.orEmpty())
            "done" -> ChatEvent.Done(o["stopReason"]?.jsonPrimitive?.content.orEmpty())
            "error" -> ChatEvent.Failure(o["code"]?.jsonPrimitive?.content.orEmpty(), o["message"]?.jsonPrimitive?.content.orEmpty())
            else -> null
        }
    }.getOrNull()

    companion object {
        const val KEY_TOKEN = "api_token"
        private const val KEY_EXP = "api_token_exp"
        private const val KEY_OWNER = "api_token_owner"

        /** Menormalkan alamat server: wajib http(s), berakhiran `/api/v1/`; build rilis hanya HTTPS. */
        fun normalize(raw: String, allowHttp: Boolean): String? {
            var u = raw.trim()
            if (u.isEmpty() || u.contains(".invalid")) return null
            if (!u.startsWith("https://") && !(allowHttp && u.startsWith("http://"))) return null
            if (!u.endsWith("/")) u += "/"
            if (!u.endsWith("api/v1/")) u += "api/v1/"
            return u
        }

        fun defaultMessage(code: Int) = when (code) {
            401 -> "Sesi server berakhir. Silakan masuk kembali."
            403 -> "Akun ini tidak berwenang untuk aksi tersebut."
            404 -> "Data tidak ditemukan."
            409 -> "Data bentrok dengan data di server."
            429 -> "Terlalu banyak permintaan. Coba lagi nanti."
            503 -> "Layanan sedang tidak tersedia."
            in 500..599 -> "Server sedang bermasalah. Coba lagi nanti."
            else -> "Permintaan gagal ($code)."
        }
    }
}
