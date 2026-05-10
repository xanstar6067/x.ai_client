package com.adam.xai_client.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.adam.xai_client.AppContainer
import com.adam.xai_client.ui.backup.BackupScreen
import com.adam.xai_client.ui.backup.BackupViewModel
import com.adam.xai_client.ui.chat.ChatScreen
import com.adam.xai_client.ui.chat.ChatViewModel
import com.adam.xai_client.ui.chatlist.ChatListScreen
import com.adam.xai_client.ui.chatlist.ChatListViewModel
import com.adam.xai_client.ui.haptics.LocalUiHapticsEnabled
import com.adam.xai_client.ui.images.ImageGenerationScreen
import com.adam.xai_client.ui.images.ImageGenerationViewModel
import com.adam.xai_client.ui.models.ModelsScreen
import com.adam.xai_client.ui.models.ModelsViewModel
import com.adam.xai_client.ui.roles.RolesScreen
import com.adam.xai_client.ui.roles.RolesViewModel
import com.adam.xai_client.ui.settings.SettingsScreen
import com.adam.xai_client.ui.settings.SettingsViewModel
import com.adam.xai_client.ui.videos.VideoGenerationScreen
import com.adam.xai_client.ui.videos.VideoGenerationViewModel

@Composable
fun XaiChatNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val uiHapticsEnabled = container.settingsRepository.uiHapticsEnabled
        .collectAsStateWithLifecycle(initialValue = true)
        .value

    CompositionLocalProvider(LocalUiHapticsEnabled provides uiHapticsEnabled) {
        NavHost(
            navController = navController,
            startDestination = Screen.ChatList.route
        ) {
        composable(Screen.ChatList.route) {
            val viewModel: ChatListViewModel = viewModel(
                factory = ChatListViewModel.factory(container)
            )
            val state = viewModel.uiState.collectAsStateWithLifecycle().value
            ChatListScreen(
                state = state,
                onOpenChat = { chatId -> navController.navigate(Screen.Chat.route(chatId)) },
                onNewChat = { navController.navigate(Screen.NewChat.route) },
                onDeleteChat = viewModel::deleteChat,
                onDuplicateChat = viewModel::duplicateChat,
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                onOpenModels = { navController.navigate(Screen.Models.route) },
                onOpenRoles = { navController.navigate(Screen.Roles.route) },
                onOpenImages = { navController.navigate(Screen.Images.route) },
                onOpenVideos = { navController.navigate(Screen.Videos.route) },
                onOpenBackups = { navController.navigate(Screen.Backups.route) },
                onErrorShown = viewModel::clearError
            )
        }

        composable(Screen.NewChat.route) {
            val viewModel: ChatViewModel = viewModel(
                key = "chat-new",
                factory = ChatViewModel.factory(container = container, chatId = null)
            )
            val state = viewModel.uiState.collectAsStateWithLifecycle().value
            ChatScreen(
                state = state,
                onInputChange = viewModel::onInputChange,
                onModelSelected = viewModel::onModelSelected,
                onRoleSelected = viewModel::onRoleSelected,
                onSend = viewModel::sendMessage,
                onStopSending = viewModel::stopSending,
                onRegenerate = viewModel::regenerateResponse,
                onResendMessage = viewModel::resendFromUserMessage,
                onSwitchMessageVersion = viewModel::switchMessageVersion,
                onUpdateMessage = viewModel::updateMessageText,
                onDeleteMessage = viewModel::deleteMessage,
                onModelInfoOpenChange = viewModel::setModelInfoOpen,
                onModelSettingsOpenChange = viewModel::setModelSettingsOpen,
                onMaxTokensChange = viewModel::updateMaxTokens,
                onTemperatureChange = viewModel::updateTemperature,
                onTopPChange = viewModel::updateTopP,
                onFrequencyPenaltyChange = viewModel::updateFrequencyPenalty,
                onPresencePenaltyChange = viewModel::updatePresencePenalty,
                onReasoningEffortChange = viewModel::updateReasoningEffort,
                onContextMessageLimitChange = viewModel::updateContextMessageLimit,
                onWebSearchEnabledChange = viewModel::updateWebSearchEnabled,
                onResetModelSettings = viewModel::resetModelSettings,
                onBack = { navController.popBackStack() },
                onErrorShown = viewModel::clearError
            )
        }

        composable(
            route = Screen.Chat.pattern,
            arguments = listOf(navArgument(Screen.Chat.ARG_CHAT_ID) { type = NavType.LongType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getLong(Screen.Chat.ARG_CHAT_ID)
            val viewModel: ChatViewModel = viewModel(
                key = "chat-$chatId",
                factory = ChatViewModel.factory(container = container, chatId = chatId)
            )
            val state = viewModel.uiState.collectAsStateWithLifecycle().value
            ChatScreen(
                state = state,
                onInputChange = viewModel::onInputChange,
                onModelSelected = viewModel::onModelSelected,
                onRoleSelected = viewModel::onRoleSelected,
                onSend = viewModel::sendMessage,
                onStopSending = viewModel::stopSending,
                onRegenerate = viewModel::regenerateResponse,
                onResendMessage = viewModel::resendFromUserMessage,
                onSwitchMessageVersion = viewModel::switchMessageVersion,
                onUpdateMessage = viewModel::updateMessageText,
                onDeleteMessage = viewModel::deleteMessage,
                onModelInfoOpenChange = viewModel::setModelInfoOpen,
                onModelSettingsOpenChange = viewModel::setModelSettingsOpen,
                onMaxTokensChange = viewModel::updateMaxTokens,
                onTemperatureChange = viewModel::updateTemperature,
                onTopPChange = viewModel::updateTopP,
                onFrequencyPenaltyChange = viewModel::updateFrequencyPenalty,
                onPresencePenaltyChange = viewModel::updatePresencePenalty,
                onReasoningEffortChange = viewModel::updateReasoningEffort,
                onContextMessageLimitChange = viewModel::updateContextMessageLimit,
                onWebSearchEnabledChange = viewModel::updateWebSearchEnabled,
                onResetModelSettings = viewModel::resetModelSettings,
                onBack = { navController.popBackStack() },
                onErrorShown = viewModel::clearError
            )
        }

        composable(Screen.Settings.route) {
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(container)
            )
            val state = viewModel.uiState.collectAsStateWithLifecycle().value
            SettingsScreen(
                state = state,
                onApiKeyChange = viewModel::onApiKeyChange,
                onBaseUrlChange = viewModel::onBaseUrlChange,
                onStreamingHapticsChange = viewModel::onStreamingHapticsChange,
                onUiHapticsChange = viewModel::onUiHapticsChange,
                onSave = viewModel::save,
                onCheckConnection = viewModel::checkConnection,
                onBack = { navController.popBackStack() },
                onMessageShown = viewModel::clearTransientMessages
            )
        }

        composable(Screen.Models.route) {
            val viewModel: ModelsViewModel = viewModel(
                factory = ModelsViewModel.factory(container)
            )
            val state = viewModel.uiState.collectAsStateWithLifecycle().value
            ModelsScreen(
                state = state,
                onRefresh = viewModel::refreshModels,
                onToggleModel = viewModel::setModelEnabled,
                onBack = { navController.popBackStack() },
                onMessageShown = viewModel::clearTransientMessages
            )
        }

        composable(Screen.Roles.route) {
            val viewModel: RolesViewModel = viewModel(
                factory = RolesViewModel.factory(container)
            )
            val state = viewModel.uiState.collectAsStateWithLifecycle().value
            RolesScreen(
                state = state,
                onCreateRole = viewModel::createRole,
                onUpdateRole = viewModel::updateRole,
                onDeleteRole = viewModel::deleteRole,
                onSetDefaultRole = viewModel::setDefaultRole,
                onBack = { navController.popBackStack() },
                onMessageShown = viewModel::clearTransientMessages
            )
        }

        composable(Screen.Images.route) {
            val viewModel: ImageGenerationViewModel = viewModel(
                factory = ImageGenerationViewModel.factory(container)
            )
            val state = viewModel.uiState.collectAsStateWithLifecycle().value
            ImageGenerationScreen(
                state = state,
                onPromptChange = viewModel::onPromptChange,
                onSourceImageUrlChange = viewModel::onSourceImageUrlChange,
                onAspectRatioChange = viewModel::onAspectRatioChange,
                onResolutionChange = viewModel::onResolutionChange,
                onModelSelected = viewModel::onModelSelected,
                onChatSelected = viewModel::onChatSelected,
                onNewChat = viewModel::newChat,
                onDeleteChat = viewModel::deleteChat,
                onDuplicateChat = viewModel::duplicateChat,
                onImageSettingsOpenChange = viewModel::setImageSettingsOpen,
                onModelInfoOpenChange = viewModel::setModelInfoOpen,
                onShowChatList = viewModel::showChatList,
                onEditFromMessage = viewModel::editFromMessage,
                onUpdateUserMessage = viewModel::updateUserMessageText,
                onDeleteMessage = viewModel::deleteMessage,
                onClearEditSource = viewModel::clearEditSource,
                onGenerate = viewModel::generate,
                onStopGeneration = viewModel::stopGeneration,
                onGenerateFromMessage = viewModel::generateFromUserMessage,
                onRegenerate = viewModel::regenerateResponse,
                onSwitchMessageVersion = viewModel::switchMessageVersion,
                onSave = viewModel::save,
                onStoragePermissionDenied = viewModel::onStoragePermissionDenied,
                onBack = { navController.popBackStack() },
                onMessageShown = viewModel::clearTransientMessages
            )
        }

        composable(Screen.Videos.route) {
            val viewModel: VideoGenerationViewModel = viewModel(
                factory = VideoGenerationViewModel.factory(container)
            )
            val state = viewModel.uiState.collectAsStateWithLifecycle().value
            VideoGenerationScreen(
                state = state,
                onPromptChange = viewModel::onPromptChange,
                onSourceImageUrlChange = viewModel::onSourceImageUrlChange,
                onDurationChange = viewModel::onDurationChange,
                onAspectRatioChange = viewModel::onAspectRatioChange,
                onResolutionChange = viewModel::onResolutionChange,
                onModelSelected = viewModel::onModelSelected,
                onChatSelected = viewModel::onChatSelected,
                onNewChat = viewModel::newChat,
                onDeleteChat = viewModel::deleteChat,
                onDuplicateChat = viewModel::duplicateChat,
                onVideoSettingsOpenChange = viewModel::setVideoSettingsOpen,
                onModelInfoOpenChange = viewModel::setModelInfoOpen,
                onShowChatList = viewModel::showChatList,
                onUpdateUserMessage = viewModel::updateUserMessageText,
                onDeleteMessage = viewModel::deleteMessage,
                onGenerate = viewModel::generate,
                onStopGeneration = viewModel::stopGeneration,
                onGenerateFromMessage = viewModel::generateFromUserMessage,
                onRegenerate = viewModel::regenerateResponse,
                onSwitchMessageVersion = viewModel::switchMessageVersion,
                onSave = viewModel::save,
                onStoragePermissionDenied = viewModel::onStoragePermissionDenied,
                onBack = { navController.popBackStack() },
                onMessageShown = viewModel::clearTransientMessages
            )
        }

        composable(Screen.Backups.route) {
            val viewModel: BackupViewModel = viewModel(
                factory = BackupViewModel.factory(container)
            )
            val state = viewModel.uiState.collectAsStateWithLifecycle().value
            BackupScreen(
                state = state,
                onExport = viewModel::exportBackup,
                onImport = viewModel::importBackup,
                onBack = { navController.popBackStack() },
                onMessageShown = viewModel::clearTransientMessages
            )
        }
        }
    }
}

private sealed class Screen(val route: String) {
    data object ChatList : Screen("chat_list")
    data object NewChat : Screen("chat/new")
    data object Settings : Screen("settings")
    data object Models : Screen("models")
    data object Roles : Screen("roles")
    data object Images : Screen("images")
    data object Videos : Screen("videos")
    data object Backups : Screen("backups")

    data object Chat : Screen("chat/{chatId}") {
        const val ARG_CHAT_ID = "chatId"
        val pattern: String = route
        fun route(chatId: Long): String = "chat/$chatId"
    }
}
