package me.kafuuneko.rpclient.feature.storycreate

import android.os.Bundle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.storycreate.model.StoryCreateForm
import me.kafuuneko.rpclient.feature.storycreate.model.StoryCreateOptions
import me.kafuuneko.rpclient.feature.storycreate.model.filterForQuery
import me.kafuuneko.rpclient.feature.storycreate.model.toStorageValue
import me.kafuuneko.rpclient.feature.storycreate.model.toStoryCreateCharacterItem
import me.kafuuneko.rpclient.feature.storycreate.model.toStoryCreateLorebookGroups
import me.kafuuneko.rpclient.feature.storycreate.presentation.StoryCreateLoadState
import me.kafuuneko.rpclient.feature.storycreate.presentation.StoryCreateUiIntent
import me.kafuuneko.rpclient.feature.storycreate.presentation.StoryCreateUiState
import me.kafuuneko.rpclient.feature.storyeditor.StoryEditorActivity
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.libs.room.repository.StoryCharacterSelection
import me.kafuuneko.rpclient.libs.room.repository.StoryLorebookEntrySelection
import me.kafuuneko.rpclient.libs.room.repository.StoryRepository
import me.kafuuneko.rpclient.utils.matchesCharacterQuery
import me.kafuuneko.rpclient.utils.toDefaultChatTitle
import me.kafuuneko.rpclient.utils.toggle
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 新建 Story 页状态持有者。
 *
 * 核心职责：
 * - 异步加载所有候选角色与带有条目的世界书分组列表；
 * - 维护故事标题输入、参演角色选择以及关联世界书条目的联动勾选；
 * - 支持角色名称、简介、标签以及世界书内容的模糊检索过滤；
 * - 保证故事实体、参演角色配置与世界书条目的原子级事务创建，并在成功后导航至故事编辑器。
 */
class StoryCreateViewModel : CoreViewModelWithEvent<StoryCreateUiIntent, StoryCreateUiState>(
    StoryCreateUiState.None
), KoinComponent {
    private var mCharacterSearchJob: Job? = null
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mStoryRepository by inject<StoryRepository>()

    /** 初始化页面，拉取数据库中的角色列表与世界书条目候选数据。 */
    @UiIntentObserver(StoryCreateUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<StoryCreateUiState.None>()) return
        StoryCreateUiState.Normal().setup()
        try {
            // 在 IO 线程并发拉取角色与世界书选项
            val data = withContext(Dispatchers.IO) { loadOptions() }
            val current = getOrNull<StoryCreateUiState.Normal>() ?: return
            // 装载候选数据并迁移至 Ready 状态
            current.copy(
                loadState = StoryCreateLoadState.Ready,
                characters = data.characters,
                visibleCharacters = data.characters,
                lorebookGroups = data.lorebookGroups,
                visibleLorebookGroups = data.lorebookGroups,
            ).setup()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            val current = getOrNull<StoryCreateUiState.Normal>() ?: return
            current.copy(loadState = StoryCreateLoadState.Ready).setup()
            AppViewEvent.PopupToastMessageByResId(R.string.story_settings_load_failed).tryEmit()
        }
    }

    /** 处理返回操作，创建进行中时禁止退出。 */
    @UiIntentObserver(StoryCreateUiIntent.Back::class)
    private fun onBack() {
        val uiState = getOrNull<StoryCreateUiState.Normal>() ?: return
        if (uiState.loadState == StoryCreateLoadState.Creating) return
        StoryCreateUiState.finished(uiState).setup()
    }

    /** 修改故事标题。 */
    @UiIntentObserver(StoryCreateUiIntent.ChangeTitle::class)
    private fun onChangeTitle(intent: StoryCreateUiIntent.ChangeTitle) {
        updateReadyForm { copy(title = intent.value) }
    }

    /** 设置新故事是否在生成 Prompt 中注入当前全局用户人设。 */
    @UiIntentObserver(StoryCreateUiIntent.SetIncludeUserPersona::class)
    private fun onSetIncludeUserPersona(intent: StoryCreateUiIntent.SetIncludeUserPersona) {
        updateReadyForm { copy(includeUserPersona = intent.enabled) }
    }

    /** 后台筛选角色；取消旧请求并保留同时发生的表单与选择修改。 */
    @UiIntentObserver(StoryCreateUiIntent.ChangeCharacterQuery::class)
    private fun onChangeCharacterQuery(intent: StoryCreateUiIntent.ChangeCharacterQuery) {
        val uiState = readyState() ?: return
        mCharacterSearchJob?.cancel()
        uiState.copy(characterQuery = intent.value).setup()
        // 后台计算仅返回 ID，回写时从最新列表恢复选择状态。
        mCharacterSearchJob = viewModelScope.launch {
            val visibleIds = withContext(Dispatchers.Default) {
                val context = currentCoroutineContext()
                uiState.characters.filter { storyCreateCharacterItem ->
                    context.ensureActive()
                    matchesCharacterQuery(
                        name = storyCreateCharacterItem.name,
                        description = storyCreateCharacterItem.description,
                        tags = storyCreateCharacterItem.tags,
                        query = intent.value,
                    )
                }.mapTo(HashSet()) { storyCreateCharacterItem -> storyCreateCharacterItem.id }
            }
            val current = readyState() ?: return@launch
            if (current.characterQuery == intent.value) {
                current.copy(visibleCharacters = current.characters.filter { storyCreateCharacterItem -> storyCreateCharacterItem.id in visibleIds }).setup()
            }
        }
    }

    /**
     * 切换角色的勾选状态，若新选中角色则自动联动选中其绑定的专属世界书条目。
     *
     * @param intent 包含目标角色 ID 的意图
     */
    @UiIntentObserver(StoryCreateUiIntent.ToggleCharacter::class)
    private fun onToggleCharacter(intent: StoryCreateUiIntent.ToggleCharacter) {
        val uiState = readyState() ?: return
        val character = uiState.characters.firstOrNull { it.id == intent.characterId } ?: return
        val selecting = character.id !in uiState.form.selectedCharacterIds
        // 查找角色专属绑定的世界书条目集合
        val linkedEntryIds = character.linkedLorebookId
            ?.let { lorebookId ->
                uiState.lorebookGroups
                    .firstOrNull { storyCreateLorebookGroupItem -> storyCreateLorebookGroupItem.lorebookId == lorebookId }
                    ?.entries
                    ?.mapTo(mutableSetOf()) { storyCreateLorebookEntryItem -> storyCreateLorebookEntryItem.id }
            }
            .orEmpty()
        // 切换角色选择状态并在初次选中时追加关联条目
        updateReadyForm {
            toggleCharacterSelection(character.id).copy(
                selectedLorebookEntryIds = if (selecting) {
                    selectedLorebookEntryIds + linkedEntryIds
                } else {
                    selectedLorebookEntryIds
                },
            )
        }
    }

    /** 设置已选角色的激活模式，并在设置新主角时自动清除旧主角。 */
    @UiIntentObserver(StoryCreateUiIntent.SetCharacterActivationMode::class)
    private fun onSetCharacterActivationMode(
        intent: StoryCreateUiIntent.SetCharacterActivationMode,
    ) {
        val uiState = readyState() ?: return
        if (intent.characterId !in uiState.form.selectedCharacterIds) return
        updateReadyForm {
            setCharacterActivationMode(intent.characterId, intent.activationMode)
        }
    }

    /** 修改世界书检索关键词，实时过滤展示的分组与条目。 */
    @UiIntentObserver(StoryCreateUiIntent.ChangeLorebookQuery::class)
    private fun onChangeLorebookQuery(intent: StoryCreateUiIntent.ChangeLorebookQuery) {
        val uiState = readyState() ?: return
        uiState.copy(
            lorebookQuery = intent.value,
            visibleLorebookGroups = uiState.lorebookGroups.filterForQuery(intent.value),
        ).setup()
    }

    /** 批量切换某一本世界书下所有条目的选中状态。 */
    @UiIntentObserver(StoryCreateUiIntent.ToggleLorebook::class)
    private fun onToggleLorebook(intent: StoryCreateUiIntent.ToggleLorebook) {
        val uiState = readyState() ?: return
        val group = uiState.lorebookGroups
            .firstOrNull { it.lorebookId == intent.lorebookId }
            ?: return
        val entryIds = group.entries.mapTo(mutableSetOf()) { it.id }
        if (entryIds.isEmpty()) return
        updateReadyForm {
            val selectAll = entryIds.any { id -> id !in selectedLorebookEntryIds }
            copy(
                selectedLorebookEntryIds = if (selectAll) {
                    selectedLorebookEntryIds + entryIds
                } else {
                    selectedLorebookEntryIds - entryIds
                },
            )
        }
    }

    /** 切换单个世界书条目的选中状态。 */
    @UiIntentObserver(StoryCreateUiIntent.ToggleLorebookEntry::class)
    private fun onToggleLorebookEntry(intent: StoryCreateUiIntent.ToggleLorebookEntry) {
        val uiState = readyState() ?: return
        if (uiState.lorebookGroups.none { group -> group.entries.any { storyCreateLorebookEntryItem -> storyCreateLorebookEntryItem.id == intent.entryId } }) {
            return
        }
        updateReadyForm {
            copy(selectedLorebookEntryIds = selectedLorebookEntryIds.toggle(intent.entryId))
        }
    }

    /** Story 与初始引用必须在同一事务中创建，避免配置校验失败后留下空 Story。 */
    @UiIntentObserver(StoryCreateUiIntent.CreateStory::class)
    private suspend fun onCreateStory() {
        val uiState = readyState() ?: return
        val createTime = System.currentTimeMillis()
        val title = uiState.form.title.trim().ifBlank { createTime.toDefaultChatTitle() }
        // 进入创建中状态
        uiState.copy(loadState = StoryCreateLoadState.Creating).setup()
        try {
            // 在 IO 线程原子事务创建故事、参演角色与关联世界书条目
            val storyId = withContext(Dispatchers.IO) {
                mStoryRepository.createStoryWithConfiguration(
                    title = title,
                    includeUserPersona = uiState.form.includeUserPersona,
                    lorebookSelections = uiState.form.selectedLorebookEntryIds
                        .sorted()
                        .map(::StoryLorebookEntrySelection),
                    characterSelections = uiState.characters
                        .filter { storyCreateCharacterItem -> storyCreateCharacterItem.id in uiState.form.selectedCharacterIds }
                        .map { character ->
                            StoryCharacterSelection(
                                characterId = character.id,
                                activationMode = uiState.form
                                    .activationModeOf(character.id)
                                    .toStorageValue(),
                            )
                        },
                )
            }
            // 启动故事编辑器页面并等待返回
            AppViewEvent.StartActivity(
                activity = StoryEditorActivity::class.java,
                extras = Bundle().apply {
                    putLong(StoryEditorActivity.EXTRA_STORY_ID, storyId)
                },
            ).emitAndAwait()
            // 退出当前新建页
            StoryCreateUiState.finished(uiStateFlow.value).setup()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // 创建失败恢复就绪状态并提示
            val current = getOrNull<StoryCreateUiState.Normal>() ?: return
            current.copy(loadState = StoryCreateLoadState.Ready).setup()
            AppViewEvent.PopupToastMessageByResId(R.string.story_save_failed).tryEmit()
        }
    }

    /** 从数据库拉取所有世界书及其有效条目，以及全量候选角色。 */
    private suspend fun loadOptions(): StoryCreateOptions {
        val userName = AppModel.resolvedUserName
        val lorebooks = mLorebookRepository.getAllLorebooksWithEntries()
        val lorebookNames = lorebooks.associate { it.lorebook.id to it.lorebook.name }
        // 构建包含条目列表的世界书分组项
        val groups = lorebooks.toStoryCreateLorebookGroups()
        // 构建候选角色列表项
        val characters = mCharacterRepository.getAllCharacters().map { character ->
            character.toStoryCreateCharacterItem(userName, lorebookNames)
        }
        return StoryCreateOptions(characters, groups)
    }

    /** 获取处于 Ready 就绪状态的 UI 状态。 */
    private fun readyState(): StoryCreateUiState.Normal? {
        return getOrNull<StoryCreateUiState.Normal>()
            ?.takeIf { it.loadState == StoryCreateLoadState.Ready }
    }

    /** 辅助方法：在 Ready 状态下以不可变方式更新表单数据。 */
    private fun updateReadyForm(update: StoryCreateForm.() -> StoryCreateForm) {
        val uiState = readyState() ?: return
        uiState.copy(form = uiState.form.update()).setup()
    }

}
