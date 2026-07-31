package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

// fxtwitter API schemas
data class FxTwitterResponse(
    val code: Int?,
    val message: String?,
    val tweet: FxTweet?
)

data class FxTweet(
    val id: String?,
    val text: String?,
    val author: FxAuthor?,
    val media: FxMedia?,
    val duration: Double?
)

data class FxAuthor(
    val id: String?,
    val name: String?,
    val screen_name: String?,
    val avatar_url: String?
)

data class FxMedia(
    val videos: List<FxVideo>?,
    val all: List<FxMediaItem>?,
    val thumbnail: String?,
    val duration: Double?
)

data class FxVideo(
    val thumbnail_url: String?,
    val url: String?,
    val duration: Double?,
    val formats: List<FxFormat>?
)

data class FxFormat(
    val url: String?,
    val container: String?,
    val content_type: String?,
    val width: Int?,
    val height: Int?,
    val bitrate: Int?
)

data class FxMediaItem(
    val type: String?, // "video", "photo", etc.
    val url: String?,
    val thumbnail_url: String?,
    val formats: List<FxFormat>?,
    val duration: Double?
)

interface FxTwitterService {
    @GET("/status/{tweetId}")
    suspend fun getTweetStatus(
        @Path("tweetId") tweetId: String,
        @Header("User-Agent") userAgent: String = "X-Down/1.5.2 (Android)"
    ): FxTwitterResponse

    companion object {
        private const val BASE_URL = "https://api.fxtwitter.com/"

        fun create(): FxTwitterService {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "X-Down/1.5.2 (Android)")
                        .build()
                    chain.proceed(request)
                }
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(FxTwitterService::class.java)
        }
    }
}
