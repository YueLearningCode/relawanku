package com.example.relawanku.data

import com.example.relawanku.BuildConfig
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object ServerAddress {
    fun normalize(value: String, allowHttp: Boolean = BuildConfig.DEBUG): String {
        val raw = value.trim().trimEnd('/')
        val url =
            (if (raw.endsWith("/api")) "$raw/" else "$raw/api/").toHttpUrlOrNull()
                ?: throw IllegalArgumentException(
                    "Masukkan URL server lengkap, misalnya http://192.168.1.10:8000/api/"
                )
        require(
            url.username.isEmpty() &&
                url.password.isEmpty() &&
                url.query == null &&
                url.fragment == null
        ) {
            "Alamat server tidak boleh memuat kredensial, query, atau fragmen."
        }
        require(allowHttp || url.isHttps) { "Server publik harus menggunakan HTTPS." }
        return url.toString()
    }

    fun media(base: String, value: String?): String? {
        if (value.isNullOrBlank()) return null
        val origin = base.toHttpUrlOrNull() ?: return null
        val url = origin.resolve(value) ?: return null
        if (!url.username.isEmpty() || !url.password.isEmpty()) return null
        return if (url.host in listOf("localhost", "127.0.0.1", "0.0.0.0", "10.0.2.2"))
            url.newBuilder()
                .scheme(origin.scheme)
                .host(origin.host)
                .port(origin.port)
                .build()
                .toString()
        else url.toString()
    }
}

class BearerInterceptor(private val base: String, private val token: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val origin = base.toHttpUrlOrNull()!!
        val builder = request.newBuilder().header("Accept", "application/json")
        val public =
            request.url.encodedPath.endsWith("/auth/login") ||
                request.url.encodedPath.endsWith("/auth/register") ||
                request.url.encodedPath.endsWith("/health")
        if (
            !public &&
            request.url.scheme == origin.scheme &&
            request.url.host == origin.host &&
            request.url.port == origin.port &&
            request.url.encodedPath.startsWith(origin.encodedPath)
        )
            token()?.let { builder.header("Authorization", "Bearer $it") }
        return chain.proceed(builder.build())
    }
}

object ApiClient {
    fun create(base: String, token: () -> String?): ApiService {
        val client =
            OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .addInterceptor(BearerInterceptor(base, token))
                .build()
        return Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(
                GsonConverterFactory.create(GsonBuilder().serializeNulls().create())
            )
            .build()
            .create(ApiService::class.java)
    }
}

data class ApiFailure(
    val status: Int = 0,
    override val message: String,
    val fields: Map<String, List<String>> = emptyMap(),
) : Exception(message) {
    companion object {
        fun http(status: Int, body: String?): ApiFailure {
            val json =
                try {
                    Gson().fromJson(body, JsonObject::class.java)
                } catch (_: Exception) {
                    null
                }
            val fields = mutableMapOf<String, List<String>>()
            json
                ?.get("errors")
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.entrySet()
                ?.forEach { (key, value) ->
                    if (value.isJsonArray)
                        fields[key] =
                            value.asJsonArray.mapNotNull {
                                if (it.isJsonPrimitive) it.asString else null
                            }
                }
            val server =
                try {
                    json?.get("message")?.asString
                } catch (_: Exception) {
                    null
                }
            val message =
                when (status) {
                    401 -> "Sesi berakhir. Silakan masuk kembali."
                    403 -> server ?: "Kamu tidak memiliki akses untuk tindakan ini."
                    404 -> "Data tidak ditemukan atau sudah diarsipkan."
                    409 ->
                        server
                            ?: "Data berubah atau tindakan ini belum tersedia. Muat ulang dan coba lagi."

                    422 ->
                        fields.values.flatten().firstOrNull()
                            ?: server
                            ?: "Periksa kembali data yang diisi."

                    429 -> "Terlalu banyak percobaan. Tunggu sebentar lalu coba lagi."
                    in 500..599 -> "Server mengalami kendala. Coba lagi nanti."
                    else -> "Permintaan belum berhasil (HTTP $status)."
                }
            return ApiFailure(status, message, fields)
        }

        fun network(error: Throwable) =
            ApiFailure(
                message =
                    if (error is IOException)
                        "Tidak dapat terhubung ke server. Periksa koneksi dan alamat server, lalu coba lagi."
                    else "Respons server belum dapat dibaca. Periksa konfigurasi API."
            )
    }
}
