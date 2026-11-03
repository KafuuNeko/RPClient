package me.kafuuneko.rpclient.feature.charactercontent

import android.os.Bundle
import kotlinx.coroutines.CancellationException
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.character.CharacterContentKind
import me.kafuuneko.rpclient.feature.charactercontent.model.toUiModel
import me.kafuuneko.rpclient.feature.charactercontent.presentation.CharacterContentListState
import me.kafuuneko.rpclient.feature.charactercontent.presentation.CharacterContentUiIntent
import me.kafuuneko.rpclient.feature.charactercontent.presentation.CharacterContentUiState
import me.kafuuneko.rpclient.feature.chat.ChatActivity
import me.kafuuneko.rpclient.feature.chatcreate.ChatCreateActivity
import me.kafuuneko.rpclient.feature.groupchat.GroupChatActivity
import me.kafuuneko.rpclient.feature.storyeditor.StoryEditorActivity
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 单个角色的内容导航页。
 * - 恢复页面时刷新关联记录，保留当前筛选。
 * - 使用内容类型与 ID 打开原记录，并将角色带入新建单聊流程。
 */
class CharacterContentViewModel :
    CoreViewModelWithEvent<CharacterContentUiIntent, CharacterContentUiState>(
        CharacterContentUiState.Loading
    ), KoinComponent {
    private val mCharacterRepository by inject<CharacterRepository>()
    private var mCharacterId = 0L

    /**
     * 根据宿主传入的角色刷新列表。
     *
     * @param intent 包含当前角色 ID 的恢复意图。
     */
    @UiIntentObserver(CharacterContentUiIntent.Resume::class)
    private suspend fun onResume(intent: CharacterContentUiIntent.Resume) {
        if (isStateOf<CharacterContentUiState.Finished>()) return
        mCharacterId = intent.characterId
        refresh()
    }

    /** 读取失败后允许重新加载。 */
    @UiIntentObserver(CharacterContentUiIntent.Retry::class)
    private suspend fun onRetry() {
        if (!isStateOf<CharacterContentUiState.LoadFailed>()) return
        CharacterContentUiState.Loading.setup()
        refresh()
    }

    /** 恢复时按当前成员关系查询，避免把已经解除关联的内容留在页内。 */
    private suspend fun refresh() {
        val previous = getOrNull<CharacterContentUiState.Normal>()
        val content = try {
            mCharacterRepository.getCharacterContent(mCharacterId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            CharacterContentUiState.LoadFailed.setup()
            return
        }
        // 角色可能在其他页面已被删除，此时不再提供创建或打开入口。
        if (content == null) {
            AppViewEvent.PopupToastMessageByResId(R.string.character_not_found).tryEmit()
            onBack()
            return
        }
        CharacterContentUiState.Normal(
            characterName = content.name,
            list = CharacterContentListState(
                items = content.items.map { it.toUiModel() },
                filter = previous?.list?.filter
            ),
        ).setup()
    }

    /**
     * 在快照内切换内容类型。
     *
     * @param intent 包含目标分类，null 表示全部内容。
     */
    @UiIntentObserver(CharacterContentUiIntent.SelectFilter::class)
    private fun onSelectFilter(intent: CharacterContentUiIntent.SelectFilter) {
        val state = getOrNull<CharacterContentUiState.Normal>() ?: return
        state.copy(list = state.list.copy(filter = intent.filter)).setup()
    }

    /**
     * 根据目标页的既有参数契约打开单聊、群聊或故事。
     *
     * @param intent 包含内容类型和对应表内的记录 ID。
     */
    @UiIntentObserver(CharacterContentUiIntent.OpenContent::class)
    private fun onOpenContent(intent: CharacterContentUiIntent.OpenContent) {
        val state = getOrNull<CharacterContentUiState.Normal>() ?: return
        if (state.list.visibleItems.none { it.type == intent.type && it.contentId == intent.contentId }) return
        // 聊天页沿用字符串会话 ID，故事编辑器沿用数值 ID。
        val extras = Bundle()
        val activity = when (intent.type) {
            CharacterContentKind.Chat -> {
                extras.putString(ChatActivity.EXTRA_SESSION_ID, intent.contentId.toString())
                ChatActivity::class.java
            }

            CharacterContentKind.Group -> {
                extras.putString(GroupChatActivity.EXTRA_SESSION_ID, intent.contentId.toString())
                GroupChatActivity::class.java
            }

            CharacterContentKind.Story -> {
                extras.putLong(StoryEditorActivity.EXTRA_STORY_ID, intent.contentId)
                StoryEditorActivity::class.java
            }
        }
        AppViewEvent.StartActivity(activity = activity, extras = extras).tryEmit()
    }

    /** 打开标准创建页，并预选当前角色。 */
    @UiIntentObserver(CharacterContentUiIntent.CreateChat::class)
    private fun onCreateChat() {
        if (!isStateOf<CharacterContentUiState.Normal>()) return
        // 复用标准创建表单，只传入角色预选参数。
        AppViewEvent.StartActivity(
            activity = ChatCreateActivity::class.java,
            extras = Bundle().apply {
                putLong(
                    ChatCreateActivity.EXTRA_CHARACTER_ID,
                    mCharacterId
                )
            },
        ).tryEmit()
    }

    /** 保存最后可见状态后通知宿主退出。 */
    @UiIntentObserver(CharacterContentUiIntent.Back::class)
    private fun onBack() {
        if (!isStateOf<CharacterContentUiState.Finished>()) CharacterContentUiState.Finished(
            uiStateFlow.value
        ).setup()
    }
}
