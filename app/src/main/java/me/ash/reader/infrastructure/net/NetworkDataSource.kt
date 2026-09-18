package me.ash.reader.infrastructure.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url
import java.io.File
import java.util.concurrent.TimeUnit

interface NetworkDataSource {

    @GET
    suspend fun getReleaseLatest(@Url url: String): Response<LatestRelease>

    @GET
    @Streaming
    suspend fun downloadFile(@Url url: String): ResponseBody

    companion object {

        private var instance: NetworkDataSource? = null

        fun getInstance(): NetworkDataSource {
            return instance ?: synchronized(this) {
                instance ?: Retrofit.Builder()
                    .baseUrl("https://api.github.com/")
                    .client(
                        OkHttpClient.Builder()
                            .connectTimeout(30, TimeUnit.SECONDS)
                            // APK downloads can pause for longer than OkHttp's default 10 seconds,
                            // especially when GitHub redirects to a slow release-asset endpoint.
                            .readTimeout(5, TimeUnit.MINUTES)
                            .retryOnConnectionFailure(true)
                            .build()
                    )
                    .addConverterFactory(GsonConverterFactory.create())
                    .build().create(NetworkDataSource::class.java).also {
                        instance = it
                    }
            }
        }
    }
}

fun ResponseBody.downloadToFileWithProgress(saveFile: File): Flow<Download> =
    flow {
        emit(Download.Progress(0))

        // flag to delete file if download errors or is cancelled
        var deleteFile = true

        try {
            byteStream().use { inputStream ->
                saveFile.outputStream().use { outputStream ->
                    val totalBytes = contentLength().takeIf { it > 0L }
                    val data = ByteArray(8_192)
                    var progressBytes = 0L

                    while (true) {
                        val bytes = inputStream.read(data)

                        if (bytes == -1) {
                            break
                        }

                        outputStream.write(data, 0, bytes)
                        progressBytes += bytes

                        totalBytes?.let {
                            emit(
                                Download.Progress(
                                    percent = ((progressBytes * 100) / it).toInt().coerceIn(0, 100)
                                )
                            )
                        }
                    }

                    when {
                        totalBytes == null ->
                            deleteFile = false

                        progressBytes < totalBytes ->
                            throw Exception("missing bytes")

                        progressBytes > totalBytes ->
                            throw Exception("too many bytes")

                        else ->
                            deleteFile = false
                    }
                }
            }

            emit(Download.Finished(saveFile))
        } finally {
            // check if download was successful

            if (deleteFile) {
                saveFile.delete()
            }
        }
    }.flowOn(Dispatchers.IO).distinctUntilChanged()

data class LatestRelease(
    val html_url: String? = null,
    val tag_name: String? = null,
    val name: String? = null,
    val draft: Boolean? = null,
    val prerelease: Boolean? = null,
    val created_at: String? = null,
    val published_at: String? = null,
    val assets: List<AssetsItem>? = null,
    val body: String? = null,
)

data class AssetsItem(
    val name: String? = null,
    val content_type: String? = null,
    val size: Int? = null,
    val download_count: Int? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
    val browser_download_url: String? = null,
)

sealed class Download {
    object NotYet : Download()
    data class Progress(val percent: Int) : Download()
    data class Finished(val file: File) : Download()
    object Failed : Download()
}
