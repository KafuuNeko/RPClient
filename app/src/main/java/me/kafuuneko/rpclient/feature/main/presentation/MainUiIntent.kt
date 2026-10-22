package me.kafuuneko.rpclient.feature.main.presentation

import android.net.Uri
import me.kafuuneko.rpclient.feature.main.model.ImageSendField
import me.kafuuneko.rpclient.feature.main.model.MainGenerationParameter
import me.kafuuneko.rpclient.feature.main.model.MainHomeContentTab
import me.kafuuneko.rpclient.feature.main.model.MainHomeItemSelection
import me.kafuuneko.rpclient.feature.main.model.Route
import me.kafuuneko.rpclient.libs.media.ImageSendMode
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionPosition
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionRole
import me.kafuuneko.rpclient.libs.theme.AppThemeMode

/** 首页及全局设置页可接收的全部用户意图。 */
sealed class MainUiIntent {
    data class Init(val route: Route = Route.Main) : MainUiIntent()

    data object Resume : MainUiIntent()

    data object Back : MainUiIntent()

    data class SelectImageSendMode(val mode: ImageSendMode) : MainUiIntent()

    data class ChangeImageSendLimit(val field: ImageSendField, val value: String) : MainUiIntent()

    data object SaveImageSendLimits : MainUiIntent()

    data class SelectPage(val page: MainPage) : MainUiIntent()

    data class SelectThemeMode(val themeMode: AppThemeMode) : MainUiIntent()

    data class SelectHomeContentTab(val tab: MainHomeContentTab) : MainUiIntent()

    data class OpenChat(val sessionId: String) : MainUiIntent()

    data object OpenContentSearch : MainUiIntent()

    data class ToggleSearchAcceleration(val enabled: Boolean) : MainUiIntent()

    data object ConfirmEnableSearchIndex : MainUiIntent()

    data object ConfirmDisableSearchIndex : MainUiIntent()

    data object CancelSearchIndexBuild : MainUiIntent()

    data object RetrySearchIndexCleanup : MainUiIntent()

    data object OpenCreateChat : MainUiIntent()

    data class OpenGroupChat(val sessionId: String) : MainUiIntent()

    data object OpenCreateGroupChat : MainUiIntent()

    data class OpenStory(val storyId: Long) : MainUiIntent()

    data object OpenCreateStory : MainUiIntent()

    data class ShowRenameItemDialog(val item: MainHomeItemSelection) : MainUiIntent()

    data class ChangeItemTitleDraft(val value: String) : MainUiIntent()

    data object ConfirmItemRename : MainUiIntent()

    data object OpenCharacterManager : MainUiIntent()

    data object OpenWorldBookManager : MainUiIntent()

    data object OpenProviderManager : MainUiIntent()

    data object OpenSelectedProviderEdit : MainUiIntent()

    data class ShowGenerationParameterDialog(
        val parameter: MainGenerationParameter
    ) : MainUiIntent()

    data class ChangeGenerationParameterDraft(val value: String) : MainUiIntent()

    data object ConfirmGenerationParameter : MainUiIntent()

    data object PickUserAvatarClick : MainUiIntent()

    data class UserAvatarCropped(val fileUuid: String) : MainUiIntent()

    data object ClearUserAvatar : MainUiIntent()

    data object ImportChatClick : MainUiIntent()

    data class ImportChatResult(val uri: Uri) : MainUiIntent()

    data class ChangeImportCharacterQuery(val value: String) : MainUiIntent()

    data class SelectImportCharacter(val characterId: Long) : MainUiIntent()

    data object ChooseChatImageDirectory : MainUiIntent()

    data class ChatImageDirectoryResult(val uri: Uri?) : MainUiIntent()

    data object ConfirmImportChat : MainUiIntent()

    data class ChangeUserName(val value: String) : MainUiIntent()

    data class ChangeUserDescription(val value: String) : MainUiIntent()

    data object ShowUserDescriptionEditor : MainUiIntent()

    data class ChangeUserDescriptionEditorDraft(val value: String) : MainUiIntent()

    data object ConfirmUserDescriptionEditor : MainUiIntent()

    data class SelectProvider(val providerId: Long) : MainUiIntent()

    data class ToggleStreamEnabled(val enabled: Boolean) : MainUiIntent()

    data class SelectPostProcessingMode(val mode: PromptPostProcessingMode) : MainUiIntent()

    data class SelectExampleDialogueBehavior(
        val behavior: ExampleDialogueBehavior
    ) : MainUiIntent()

    data class ToggleIncludeThinkInContext(val enabled: Boolean) : MainUiIntent()

    data class ChangeMaxPromptHistoryMessages(val value: String) : MainUiIntent()

    data class ChangeWorldInfoBudgetPercent(val value: Int) : MainUiIntent()

    data class ChangeWorldInfoBudgetCap(val value: String) : MainUiIntent()

    data class ToggleWorldInfoOverflowAlert(val enabled: Boolean) : MainUiIntent()

    data class ToggleContextTrimmingAlert(val enabled: Boolean) : MainUiIntent()

    data class ToggleDebugModeEnabled(val enabled: Boolean) : MainUiIntent()

    data class ToggleAutoSummaryEnabled(val enabled: Boolean) : MainUiIntent()

    data class SelectSummaryProvider(val providerId: Long) : MainUiIntent()

    data class ChangeSummaryTriggerMessageCount(val value: String) : MainUiIntent()

    data class ChangeSummaryWordsLimit(val value: String) : MainUiIntent()

    data class ChangeSummaryMaxMessagesPerRequest(val value: String) : MainUiIntent()

    data class ChangeSummaryResponseTokens(val value: String) : MainUiIntent()

    data class SelectSummarySettingsTab(
        val tab: MainSummarySettingsTab
    ) : MainUiIntent()

    data class SelectSummaryInjectionPosition(
        val position: SummaryInjectionPosition
    ) : MainUiIntent()

    data class ChangeSummaryInjectionDepth(val value: String) : MainUiIntent()

    data class SelectSummaryInjectionRole(
        val role: SummaryInjectionRole
    ) : MainUiIntent()

    data object OpenPromptPreset : MainUiIntent()

    data object OpenRegexScripts : MainUiIntent()

    data object OpenRequestLogs : MainUiIntent()

    data object OpenTokenUsage : MainUiIntent()

    data object OpenAppearance : MainUiIntent()

    data object OpenBackupRestore : MainUiIntent()

    data class ToggleStoryMarkdownRendering(val enabled: Boolean) : MainUiIntent()

    data object OpenAbout : MainUiIntent()

    data class EnterMultiSelect(val item: MainHomeItemSelection) : MainUiIntent()

    data class ToggleItemSelection(val item: MainHomeItemSelection) : MainUiIntent()

    data class ToggleSessionGroup(val characterId: String) : MainUiIntent()

    data object ExitMultiSelect : MainUiIntent()

    data object ShowDeleteSelectedDialog : MainUiIntent()

    data object ConfirmDeleteSelected : MainUiIntent()

    data object DismissDialog : MainUiIntent()
}
