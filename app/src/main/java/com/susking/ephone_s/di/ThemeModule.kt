package com.susking.ephone_s.di

import android.content.Context
import com.google.gson.Gson
import com.susking.ephone_s.features.theme.data.repository.ThemeRepositoryImpl
import com.susking.ephone_s.features.theme.domain.repository.ThemeRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ThemeModule {

    @Provides
    @Singleton
    fun provideThemeRepository(
        @ApplicationContext context: Context,
        gson: Gson
    ): ThemeRepository {
        return ThemeRepositoryImpl(context, gson)
    }
}