package com.sq26.imageview.di

import android.content.Context
import androidx.room.Room
import com.sq26.imageview.data.AppDatabase
import com.sq26.imageview.data.DirectoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class DataModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "data.db")
            // This is not recommended for normal apps, but the goal of this sample isn't to
            // showcase all of Room.
            .fallbackToDestructiveMigration(false)
            .build()

    @Provides
    @Singleton
    fun provideCategoriesDao(database: AppDatabase): DirectoryDao = database.directoryDao()
}