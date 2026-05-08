package com.adam.xai_client

import android.content.Context
import androidx.room.Room
import com.adam.xai_client.data.local.database.AppDatabase
import com.adam.xai_client.data.local.settings.SettingsDataStore
import com.adam.xai_client.data.remote.api.XaiApiClient
import com.adam.xai_client.data.remote.client.KtorXaiApiClient
import com.adam.xai_client.data.repository.ChatRepository
import com.adam.xai_client.data.repository.BackupRepository
import com.adam.xai_client.data.repository.ImageRepository
import com.adam.xai_client.data.repository.ModelRepository
import com.adam.xai_client.data.repository.RoleRepository
import com.adam.xai_client.data.repository.SettingsRepository
import com.adam.xai_client.domain.token.TokenCounter
import com.adam.xai_client.domain.usecase.SendMessageUseCase

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "xai_chat.db"
    ).addMigrations(
        AppDatabase.MIGRATION_1_2,
        AppDatabase.MIGRATION_2_3,
        AppDatabase.MIGRATION_3_4,
        AppDatabase.MIGRATION_4_5,
        AppDatabase.MIGRATION_5_6,
        AppDatabase.MIGRATION_6_7,
        AppDatabase.MIGRATION_7_8,
        AppDatabase.MIGRATION_8_9,
        AppDatabase.MIGRATION_9_10
    ).build()

    val settingsRepository: SettingsRepository = SettingsRepository(
        SettingsDataStore(appContext)
    )

    val apiClient: XaiApiClient = KtorXaiApiClient()

    val tokenCounter: TokenCounter = TokenCounter()

    val backupRepository: BackupRepository = BackupRepository(appContext, database)

    val chatRepository: ChatRepository = ChatRepository(database)

    val roleRepository: RoleRepository = RoleRepository(
        modelRoleDao = database.modelRoleDao(),
        settingsRepository = settingsRepository
    )

    val modelRepository: ModelRepository = ModelRepository(
        aiModelDao = database.aiModelDao(),
        settingsRepository = settingsRepository,
        apiClient = apiClient
    )

    val imageRepository: ImageRepository = ImageRepository(
        context = appContext,
        database = database,
        settingsRepository = settingsRepository,
        apiClient = apiClient
    )

    val sendMessageUseCase: SendMessageUseCase = SendMessageUseCase(
        chatRepository = chatRepository,
        roleRepository = roleRepository,
        settingsRepository = settingsRepository,
        apiClient = apiClient
    )
}
