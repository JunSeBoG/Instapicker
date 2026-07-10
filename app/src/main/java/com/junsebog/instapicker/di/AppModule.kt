package com.junsebog.instapicker.di

import android.content.Context
import androidx.room.Room
import com.junsebog.instapicker.core.database.AppDatabase
import com.junsebog.instapicker.feature.picking.data.AssetProductSource
import com.junsebog.instapicker.feature.picking.data.PickingRepository
import com.junsebog.instapicker.feature.picking.data.ProductSource
import com.junsebog.instapicker.feature.picking.data.RoomPickingRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires the data layer into Hilt's application-scoped graph: a single database, the
 * asset-backed product source, and the repository that binds them.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @Suppress("NamedArguments") // Room.databaseBuilder is a Java builder; args can't be named.
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME).build()

    @Provides
    @Singleton
    fun provideProductSource(@ApplicationContext context: Context): ProductSource =
        AssetProductSource(context = context)

    @Provides
    @Singleton
    fun provideRepository(db: AppDatabase, productSource: ProductSource): PickingRepository =
        RoomPickingRepository(db = db, productSource = productSource)

    private const val DATABASE_NAME = "instapicker.db"
}
