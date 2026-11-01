package com.demomart.register.di

import com.demomart.register.BuildConfig
import com.demomart.register.auth.AuthTokenProvider
import com.demomart.register.auth.FakeTokenProvider
import com.demomart.register.auth.FirebaseTokenProvider
import com.google.firebase.auth.FirebaseAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides @Singleton
    fun auth(): AuthTokenProvider = if (BuildConfig.AUTH_MODE == "fake") {
        FakeTokenProvider()
    } else {
        FirebaseTokenProvider(FirebaseAuth.getInstance())
    }
}
