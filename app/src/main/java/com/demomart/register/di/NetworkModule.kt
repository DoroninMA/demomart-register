package com.demomart.register.di

import com.demomart.register.BuildConfig
import com.demomart.register.auth.AuthTokenProvider
import com.demomart.register.net.PosApi
import com.google.firebase.FirebaseException
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true }

    @Provides @Singleton
    fun httpClient(auth: AuthTokenProvider): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val token = try {
                runBlocking { auth.idToken() }
            } catch (error: FirebaseException) {
                throw IOException("Could not get an ID token", error)
            }
            val request = chain.request().newBuilder()
                .apply { if (token != null) header("Authorization", "Bearer $token") }
                .build()
            chain.proceed(request).also {
                if (it.code == 401) auth.signOut()
            }
        }
        .build()

    @Provides @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit = retrofit(client, json, BuildConfig.SERVER_URL)

    internal fun retrofit(client: OkHttpClient, json: Json, baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides @Singleton
    fun posApi(retrofit: Retrofit): PosApi = retrofit.create(PosApi::class.java)
}
