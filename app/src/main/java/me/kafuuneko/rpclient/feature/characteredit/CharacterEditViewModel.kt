package me.kafuuneko.rpclient.feature.characteredit

import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.appearance.presentation.PageBackgroundUiState
import me.kafuuneko.rpclient.feature.characteredit.model.BackgroundDraft
import me.kafuuneko.rpclient.feature.characteredit.model.CharacterEditForm
import me.kafuuneko.rpclient.feature.characteredit.model.CharacterLorebookItem
import me.kafuuneko.rpclient.feature.characteredit.model.CharacterProviderItem
import me.kafuuneko.rpclient.feature.characteredit.model.ensureListInputs
import me.kafuuneko.rpclient.feature.characteredit.model.hasUnsavedChangesFrom
import me.kafuuneko.rpclient.feature.characteredit.model.promptText
import me.kafuuneko.rpclient.feature.characteredit.model.withPromptText
import me.kafuuneko.rpclient.feature.characteredit.model.withValidLorebookAssociation
import me.kafuuneko.rpclient.feature.characteredit.model.withValidProviderAssociation
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditDialogState
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditLoadState
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditMode
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditUiIntent
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditUiState
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditViewEvent
import me.kafuuneko.rpclient.feature.worldbooklist.WorldBookListActivity
import me.kafuuneko.rpclient.libs.appearance.BackgroundImageRuntime
import me.kafuuneko.rpclient.libs.appearance.BackgroundPage
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.model.PreparedFile
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.libs.room.repository.PageBackgroundRepository
import me.kafuuneko.rpclient.utils.orSingleBlank
import me.kafuuneko.rpclient.utils.removeAtOrSelf
import me.kafuuneko.rpclient.utils.updateAt
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

/**
 * 角色创建与编辑页的状态持有者。
 *
 * 核心职责：
 * - 维护角色全量表单字段（基本信息、性格、场景、首条消息、示例对话、系统提示词、历史后指令、深度提示词、替代开场白等）；
 * - 管理角色头像与背景文件生命周期（选择、暂存、替换、保存与未提交临时文件清理）；
 * - 协调角色关联世界书与独立 LLM 提供商配置的校验与动态同步；
 * - 提供多行长文本 Prompt 全屏编辑器弹窗及剪贴板复制能力；
 * - 支持未保存修改防误退脏检查弹窗及级联删除关联世界书确认。
 */
class CharacterEditViewModel : CoreViewModelWithEvent<CharacterEditUiIntent, CharacterEditUiState>(
    CharacterEditUiState.None
), KoinComponent {
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mFileRepository by inject<FileRepository>()
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mLLMRepository by inject<LLMRepository>()

    private val mBackgrounds by inject<PageBackgroundRepository>()
    private val mBackgroundRuntime by inject<BackgroundImageRuntime>()
    private val mBackgroundOwner = UUID.randomUUID().toString()
    private var mPreparedBackground: PreparedFile? = null
    private val mBackgroundDraft = MutableStateFlow<BackgroundDraft?>(value = null)

    init {
        viewModelScope.launch {
            combine(
                mBackgrounds.observe(BackgroundPage.General),
                mBackgroundDraft.filterNotNull()
            ) { general, draft ->
                general.copy(fileUuid = draft.uuid ?: general.fileUuid) to draft.prepared
            }.collectLatest { (settings, prepared) ->
                // 角色只覆盖图片；通用外观变化会取消过期渲染，并保留未保存的图片草稿。
                val background = try {
                    PageBackgroundUiState(
                        settings = settings,
                        image = mBackgroundRuntime.render(settings, prepared)?.asImageBitmap(),
                        surfaceImages = mBackgroundRuntime.renderSurfaces(settings, prepared)
                            .mapValues { entry -> entry.value.asImageBitmap() },
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    PageBackgroundUiState(settings)
                } catch (_: OutOfMemoryError) {
                    PageBackgroundUiState(settings)
                }
                getOrNull<CharacterEditUiState.Normal>()?.copy(background = background)?.setup()
            }
        }
    }

    /** 初始化编辑页，拉取候选世界书与提供商列表，并加载目标角色或新建空表单。 */
    @UiIntentObserver(CharacterEditUiIntent.Init::class)
    private suspend fun onInit(intent: CharacterEditUiIntent.Init) {
        if (!isStateOf<CharacterEditUiState.None>()) return
        // 在 IO 线程并发查询候选世界书列表与提供商列表
        val (lorebooks, providers) = withContext(Dispatchers.IO) {
            mLorebookRepository.getAllLorebooks() to mLLMRepository.getAllProviders()
        }
        // 若传入角色 ID 则查询角色实体详情
        val character = intent.characterId?.let { id ->
            withContext(Dispatchers.IO) { mCharacterRepository.getCharacterById(id) }
        }
        // 查询该角色独立绑定的 LLM 提供商 ID
        val llmProviderId = character?.let { character ->
            withContext(Dispatchers.IO) { mCharacterRepository.getLLMProviderId(character.id) }
        } ?: 0L
        // 转换实体为 UI 表单数据
        val form =
            character?.let { CharacterEditForm.from(it, llmProviderId) } ?: CharacterEditForm()
        // 构建初始 UI 状态
        CharacterEditUiState.Normal(
            mode = if (character == null) CharacterEditMode.Create else CharacterEditMode.Edit,
            form = form.ensureListInputs(),
            avatarImage = form.resolveAvatarImage(),
            availableLorebooks = lorebooks.map { CharacterLorebookItem(it.id, it.name) },
            availableProviders = providers
                .filter { it.isEnabled || it.id == form.llmProviderId }
                .map { provider ->
                    CharacterProviderItem(
                        id = provider.id,
                        name = provider.name,
                        model = provider.model,
                        isEnabled = provider.isEnabled,
                    )
                },
            loadState = CharacterEditLoadState.None,
        ).setup()
        mBackgroundDraft.value = BackgroundDraft(form.backgroundFileUuid)
    }

    /** 更新角色绑定的世界书 ID。 */
    @UiIntentObserver(CharacterEditUiIntent.UpdateCharacterLorebook::class)
    private fun onUpdateCharacterLorebook(intent: CharacterEditUiIntent.UpdateCharacterLorebook) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        uiState.copy(form = uiState.form.copy(characterLorebookId = intent.lorebookId)).setup()
    }

    /** 选择角色绑定的独立 LLM 提供商。 */
    @UiIntentObserver(CharacterEditUiIntent.SelectLLMProvider::class)
    private fun onSelectLLMProvider(intent: CharacterEditUiIntent.SelectLLMProvider) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        uiState.copy(
            form = uiState.form.copy(llmProviderId = intent.providerId),
            availableProviders = uiState.availableProviders.filter { characterProviderItem ->
                characterProviderItem.isEnabled || characterProviderItem.id == intent.providerId
            },
        ).setup()
    }

    /** 页面恢复可见时重新校验世界书与模型提供商有效性，清理失效外键关联。 */
    @UiIntentObserver(CharacterEditUiIntent.Resume::class)
    private suspend fun onResume() {
        if (!isStateOf<CharacterEditUiState.Normal>()) return
        // 重新拉取最新的世界书与提供商列表
        val (lorebooks, providers) = withContext(Dispatchers.IO) {
            mLorebookRepository.getAllLorebooks() to mLLMRepository.getAllProviders()
        }
        val availableLorebookIds = lorebooks.mapTo(mutableSetOf()) { it.id }
        val availableProviderIds = providers.mapTo(mutableSetOf()) { it.id }
        // 读取最新表单，避免覆盖等待期间完成的背景预览或图片选择。
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        uiState.copy(
            form = uiState.form
                .withValidLorebookAssociation(availableLorebookIds)
                .withValidProviderAssociation(availableProviderIds),
            initialForm = uiState.initialForm
                .withValidLorebookAssociation(availableLorebookIds)
                .withValidProviderAssociation(availableProviderIds),
            availableLorebooks = lorebooks.map { CharacterLorebookItem(it.id, it.name) },
            availableProviders = providers
                .filter { it.isEnabled || it.id == uiState.form.llmProviderId }
                .map { provider ->
                    CharacterProviderItem(
                        id = provider.id,
                        name = provider.name,
                        model = provider.model,
                        isEnabled = provider.isEnabled,
                    )
                },
        ).setup()
    }

    /** 打开世界书管理列表页面。 */
    @UiIntentObserver(CharacterEditUiIntent.OpenWorldBookManager::class)
    private fun onOpenWorldBookManager() {
        if (!isStateOf<CharacterEditUiState.Normal>()) return
        AppViewEvent.StartActivity(WorldBookListActivity::class.java).tryEmit()
    }

    /** 处理返回操作，若有未保存修改弹出防误退弹窗，否则清理临时文件并退出。 */
    @UiIntentObserver(CharacterEditUiIntent.Back::class)
    private suspend fun onBack() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.loadState != CharacterEditLoadState.None) return
        if (uiState.form.hasUnsavedChangesFrom(uiState.initialForm)) {
            uiState.copy(dialogState = CharacterEditDialogState.UnsavedChangesConfirm).setup()
            return
        }
        finishEditing()
    }

    /** 选择背景只触发系统事件，文件校验与暂存由背景管线完成。 */
    @UiIntentObserver(CharacterEditUiIntent.PickBackground::class)
    private fun onPickBackground() {
        val state = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (state.loadState == CharacterEditLoadState.None) CharacterEditViewEvent.OpenBackgroundPicker.tryEmit()
    }

    /** 替换尚未提交的背景草稿，旧关联直到角色保存成功后才回收。 */
    @UiIntentObserver(CharacterEditUiIntent.BackgroundSelected::class)
    private suspend fun onBackgroundSelected(intent: CharacterEditUiIntent.BackgroundSelected) {
        val state = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (state.loadState != CharacterEditLoadState.None) return
        state.copy(loadState = CharacterEditLoadState.Loading).setup()
        try {
            // 导入失败不改动表单及已保存背景，取消由图像管线清理未完成草稿。
            val prepared = mBackgroundRuntime.prepare(mBackgroundOwner, intent.uri)
            val previous = mPreparedBackground
            mPreparedBackground = prepared
            updateForm { copy(backgroundFileUuid = prepared.file.uuid) }
            mBackgroundDraft.value = BackgroundDraft(prepared.file.uuid, prepared)
            mBackgroundRuntime.discard(previous)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AppViewEvent.PopupToastMessageByResId(R.string.appearance_image_error).tryEmit()
        } catch (_: OutOfMemoryError) {
            AppViewEvent.PopupToastMessageByResId(R.string.appearance_image_error).tryEmit()
        } finally {
            getOrNull<CharacterEditUiState.Normal>()?.copy(loadState = CharacterEditLoadState.None)
                ?.setup()
        }
    }

    /** 移除仅改变草稿，取消编辑时已保存背景仍然存在。 */
    @UiIntentObserver(CharacterEditUiIntent.RemoveBackground::class)
    private fun onRemoveBackground() {
        val state = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (state.loadState != CharacterEditLoadState.None) return
        updateForm { copy(backgroundFileUuid = null) }
        mBackgroundDraft.value = BackgroundDraft(uuid = null)
        discardBackgroundDraft()
    }

    /** 独立清理作用域处理临时源图，保存后的文件不再由编辑器持有。 */
    private fun discardBackgroundDraft() {
        mBackgroundRuntime.discard(mPreparedBackground)
        mPreparedBackground = null
    }

    /** 页面被销毁时释放尚未提交的背景，旋转期间 ViewModel 仍持有草稿。 */
    override fun onCleared() {
        discardBackgroundDraft()
        super.onCleared()
    }

    /** 触发系统图片选择器以选取角色头像。 */
    @UiIntentObserver(CharacterEditUiIntent.PickAvatarClick::class)
    private fun onPickAvatarClick() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.loadState != CharacterEditLoadState.None) return
        CharacterEditViewEvent.OpenAvatarPicker.tryEmit()
    }

    /**
     * 接收裁剪页生成的文件，并替换表单持有的临时头像。
     *
     * 连续选择时只删除尚未提交的上一份临时文件；数据库仍引用的原头像必须等角色保存
     * 成功或用户确认删除后才能清理。
     */
    @UiIntentObserver(CharacterEditUiIntent.AvatarCropped::class)
    private suspend fun onAvatarCropped(intent: CharacterEditUiIntent.AvatarCropped) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        uiState.copy(loadState = CharacterEditLoadState.Saving).setup()
        // 裁剪页已保存新文件；这里只清理当前表单不再引用的上一份临时头像。
        runCatching {
            withContext(Dispatchers.IO) {
                if (
                    uiState.form.avatar.isNotBlank()
                    && uiState.form.avatar != uiState.form.originalAvatar
                    && uiState.form.avatar != intent.fileUuid
                ) {
                    mFileRepository.deleteFile(uiState.form.avatar)
                }
            }
        }.getOrElse {
            val latestState = getOrNull<CharacterEditUiState.Normal>() ?: return
            latestState.copy(loadState = CharacterEditLoadState.None).setup()
            AppViewEvent.PopupToastMessageByResId(R.string.character_avatar_save_failed).tryEmit()
            return
        }
        // 更新表单头像 UUID 并重新解码位图渲染
        val latestState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val form = latestState.form.copy(avatar = intent.fileUuid)
        latestState.copy(
            form = form,
            avatarImage = form.resolveAvatarImage(),
            loadState = CharacterEditLoadState.None,
        ).setup()
    }

    /** 修改角色名称。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeName::class)
    private fun onChangeName(intent: CharacterEditUiIntent.ChangeName) =
        updateForm { copy(name = intent.value) }

    /** 批量设置标签列表。 */
    @UiIntentObserver(CharacterEditUiIntent.SetTags::class)
    private fun onSetTags(intent: CharacterEditUiIntent.SetTags) =
        updateForm { copy(tags = intent.tags.orSingleBlank()) }

    /** 修改角色描述（Description）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeDescription::class)
    private fun onChangeDescription(intent: CharacterEditUiIntent.ChangeDescription) =
        updateForm { copy(description = intent.value) }

    /** 修改作者附言（Creator Notes）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeCreatorNotes::class)
    private fun onChangeCreatorNotes(intent: CharacterEditUiIntent.ChangeCreatorNotes) =
        updateForm { copy(creatorNotes = intent.value) }

    /** 修改创作者名称（Creator）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeCreator::class)
    private fun onChangeCreator(intent: CharacterEditUiIntent.ChangeCreator) =
        updateForm { copy(creator = intent.value) }

    /** 修改角色卡版本号（Character Version）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeCharacterVersion::class)
    private fun onChangeCharacterVersion(intent: CharacterEditUiIntent.ChangeCharacterVersion) =
        updateForm { copy(characterVersion = intent.value) }

    /** 修改性格描述（Personality）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangePersonality::class)
    private fun onChangePersonality(intent: CharacterEditUiIntent.ChangePersonality) =
        updateForm { copy(personality = intent.value) }

    /** 修改场景设定（Scenario）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeScenario::class)
    private fun onChangeScenario(intent: CharacterEditUiIntent.ChangeScenario) =
        updateForm { copy(scenario = intent.value) }

    /** 追加一个空的首条消息（First Message）输入项。 */
    @UiIntentObserver(CharacterEditUiIntent.AddFirstMessage::class)
    private fun onAddFirstMessage() =
        updateForm { copy(firstMessages = firstMessages + "") }

    /** 修改指定索引处的首条消息。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeFirstMessage::class)
    private fun onChangeFirstMessage(intent: CharacterEditUiIntent.ChangeFirstMessage) =
        updateForm { copy(firstMessages = firstMessages.updateAt(intent.index, intent.value)) }

    /** 删除指定索引处的首条消息。 */
    @UiIntentObserver(CharacterEditUiIntent.DeleteFirstMessage::class)
    private fun onDeleteFirstMessage(intent: CharacterEditUiIntent.DeleteFirstMessage) =
        updateForm {
            copy(
                firstMessages = firstMessages.removeAtOrSelf(intent.index).orSingleBlank()
            )
        }

    /** 修改示例对话（Examples of Dialogue）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeExamplesOfDialogue::class)
    private fun onChangeExamplesOfDialogue(intent: CharacterEditUiIntent.ChangeExamplesOfDialogue) =
        updateForm { copy(examplesOfDialogue = intent.value) }

    /** 修改历史后指令（Post-History Instructions）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangePostHistoryInstructions::class)
    private fun onChangePostHistoryInstructions(intent: CharacterEditUiIntent.ChangePostHistoryInstructions) =
        updateForm { copy(postHistoryInstructions = intent.value) }

    /** 修改主系统提示词（System Prompt）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeSystemPrompt::class)
    private fun onChangeSystemPrompt(intent: CharacterEditUiIntent.ChangeSystemPrompt) =
        updateForm { copy(systemPrompt = intent.value) }

    /** 修改角色深度提示词内容（Depth Prompt）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeDepthPromptPrompt::class)
    private fun onChangeDepthPromptPrompt(intent: CharacterEditUiIntent.ChangeDepthPromptPrompt) =
        updateForm { copy(depthPromptPrompt = intent.value) }

    /** 修改角色深度提示词注入深度（Depth Prompt Depth）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeDepthPromptDepth::class)
    private fun onChangeDepthPromptDepth(intent: CharacterEditUiIntent.ChangeDepthPromptDepth) =
        updateForm { copy(depthPromptDepth = intent.value) }

    /** 修改角色深度提示词消息角色（Depth Prompt Role）。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeDepthPromptRole::class)
    private fun onChangeDepthPromptRole(intent: CharacterEditUiIntent.ChangeDepthPromptRole) =
        updateForm { copy(depthPromptRole = intent.value) }

    /** 追加一个空的替代开场白（Alternate Greeting）输入项。 */
    @UiIntentObserver(CharacterEditUiIntent.AddAlternateGreeting::class)
    private fun onAddAlternateGreeting() =
        updateForm { copy(alternateGreetings = alternateGreetings + "") }

    /** 修改指定索引处的替代开场白。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeAlternateGreeting::class)
    private fun onChangeAlternateGreeting(intent: CharacterEditUiIntent.ChangeAlternateGreeting) =
        updateForm {
            copy(
                alternateGreetings = alternateGreetings.updateAt(
                    intent.index,
                    intent.value
                )
            )
        }

    /** 删除指定索引处的替代开场白。 */
    @UiIntentObserver(CharacterEditUiIntent.DeleteAlternateGreeting::class)
    private fun onDeleteAlternateGreeting(intent: CharacterEditUiIntent.DeleteAlternateGreeting) =
        updateForm {
            copy(
                alternateGreetings = alternateGreetings.removeAtOrSelf(intent.index).orSingleBlank()
            )
        }

    /** 修改扩展 JSON 字符串。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangeExtensionsJson::class)
    private fun onChangeExtensionsJson(intent: CharacterEditUiIntent.ChangeExtensionsJson) =
        updateForm { copy(extensionsJson = intent.value) }

    /** 打开长文本字段的全屏 Prompt 编辑器弹窗。 */
    @UiIntentObserver(CharacterEditUiIntent.ShowPromptEditor::class)
    private fun onShowPromptEditor(intent: CharacterEditUiIntent.ShowPromptEditor) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val draftText = uiState.form.promptText(intent.field) ?: return
        uiState.copy(dialogState = CharacterEditDialogState.PromptEditor(intent.field, draftText))
            .setup()
    }

    /** 更新全屏 Prompt 编辑器中的临时草稿文本。 */
    @UiIntentObserver(CharacterEditUiIntent.ChangePromptEditorDraft::class)
    private fun onChangePromptEditorDraft(intent: CharacterEditUiIntent.ChangePromptEditorDraft) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? CharacterEditDialogState.PromptEditor ?: return
        uiState.copy(dialogState = dialogState.copy(draftText = intent.value)).setup()
    }

    /** 复制全屏 Prompt 编辑器中的草稿文本到剪贴板。 */
    @UiIntentObserver(CharacterEditUiIntent.CopyPromptEditorText::class)
    private fun onCopyPromptEditorText() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? CharacterEditDialogState.PromptEditor ?: return
        CharacterEditViewEvent.CopyText(dialogState.draftText).tryEmit()
    }

    /** 确认并回写全屏 Prompt 编辑器中的内容至主表单。 */
    @UiIntentObserver(CharacterEditUiIntent.ConfirmPromptEditor::class)
    private fun onConfirmPromptEditor() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? CharacterEditDialogState.PromptEditor ?: return
        uiState.copy(
            form = uiState.form.withPromptText(dialogState.field, dialogState.draftText),
            dialogState = CharacterEditDialogState.None,
        ).setup()
    }

    /** 先提交角色对新头像的引用，再清理不再使用的原头像文件。 */
    @UiIntentObserver(CharacterEditUiIntent.SaveCharacter::class)
    private suspend fun onSaveCharacter() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.loadState != CharacterEditLoadState.None) return
        val character = uiState.form.toCharacterOrNullWithToast() ?: return
        uiState.copy(loadState = CharacterEditLoadState.Saving).setup()
        // 角色、背景及模型关联一起提交，失败时保留可重试草稿。
        try {
            withContext(Dispatchers.IO) {
                mCharacterRepository.saveCharacterWithAppearance(
                    character = character,
                    llmProviderId = uiState.form.llmProviderId,
                    backgroundUuid = uiState.form.backgroundFileUuid,
                    prepared = mPreparedBackground,
                )
                mPreparedBackground = null
                // 头像属于已有独立流程；清理失败不应让已提交的新角色再次创建。
                if (uiState.form.originalAvatar.isNotBlank() && uiState.form.originalAvatar != character.avatar) {
                    runCatching { mFileRepository.deleteFile(uiState.form.originalAvatar) }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            getOrNull<CharacterEditUiState.Normal>()?.copy(loadState = CharacterEditLoadState.None)
                ?.setup()
            AppViewEvent.PopupToastMessageByResId(R.string.appearance_background_error).tryEmit()
            return
        }
        AppViewEvent.PopupToastMessageByResId(
            message = if (uiState.mode == CharacterEditMode.Create) R.string.character_created else R.string.character_saved,
        ).tryEmit()
        CharacterEditUiState.finished(uiStateFlow.value).setup()
    }

    /** 点击删除角色，检查是否有关联世界书并弹出相应确认对话框。 */
    @UiIntentObserver(CharacterEditUiIntent.DeleteCharacterClick::class)
    private suspend fun onDeleteCharacterClick() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.form.isNew) {
            cleanupPendingImages()
            CharacterEditUiState.finished(uiStateFlow.value).setup()
            return
        }
        val savedCharacter = withContext(Dispatchers.IO) {
            mCharacterRepository.getCharacterById(uiState.form.id)
        } ?: run {
            CharacterEditUiState.finished(uiStateFlow.value).setup()
            return
        }
        val associatedLorebook = savedCharacter.characterLorebookId
            .takeIf { it != 0L }
            ?.let { lorebookId ->
                withContext(Dispatchers.IO) {
                    mLorebookRepository.getLorebookById(lorebookId)
                }
            }
        uiState.copy(
            dialogState = if (associatedLorebook == null) {
                CharacterEditDialogState.DeleteConfirm(characterName = savedCharacter.name)
            } else {
                CharacterEditDialogState.DeleteWithLorebookConfirm(
                    characterName = savedCharacter.name,
                    lorebookId = associatedLorebook.id,
                    lorebookName = associatedLorebook.name,
                )
            },
        ).setup()
    }

    /** 确认删除无关联世界书的角色。 */
    @UiIntentObserver(CharacterEditUiIntent.ConfirmDeleteCharacter::class)
    private suspend fun onConfirmDeleteCharacter() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.dialogState !is CharacterEditDialogState.DeleteConfirm) return
        deleteCharacter()
    }

    /** 用户确认仅删除角色本身，保留关联世界书。 */
    @UiIntentObserver(CharacterEditUiIntent.ConfirmDeleteCharacterOnly::class)
    private suspend fun onConfirmDeleteCharacterOnly() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.dialogState !is CharacterEditDialogState.DeleteWithLorebookConfirm) return
        deleteCharacter()
    }

    /** 用户确认一并删除角色及其绑定的专属世界书。 */
    @UiIntentObserver(CharacterEditUiIntent.ConfirmDeleteCharacterWithLorebook::class)
    private suspend fun onConfirmDeleteCharacterWithLorebook() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? CharacterEditDialogState.DeleteWithLorebookConfirm
            ?: return
        deleteCharacter(lorebookId = dialogState.lorebookId)
    }

    /**
     * 删除角色及其头像；只有用户在关联确认中明确选择时才一并删除世界书。
     *
     * 表单尚未提交的新头像不受数据库实体追踪，需要在同一流程中额外清理。
     *
     * @param lorebookId 需一并删除的关联世界书 ID（若有）
     */
    private suspend fun deleteCharacter(lorebookId: Long? = null) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.form.isNew) return
        uiState.copy(
            loadState = CharacterEditLoadState.Deleting,
            dialogState = CharacterEditDialogState.None,
        ).setup()
        val pendingAvatar = uiState.form.avatar
            .takeIf { it.isNotBlank() && it != uiState.form.originalAvatar }
        withContext(Dispatchers.IO) {
            val character = mCharacterRepository.getCharacterById(uiState.form.id)
            // 可选删除关联的世界书
            lorebookId?.let { id ->
                mLorebookRepository.deleteLorebook(id)
            }
            // 删除角色实体
            mCharacterRepository.deleteCharacter(uiState.form.id)
            // 删除已持久化的旧头像文件
            character?.avatar?.takeIf { text -> text.isNotBlank() }?.let { text ->
                mFileRepository.deleteFile(text)
            }
            // 删除未持久化的新头像临时文件
            pendingAvatar?.let { text ->
                mFileRepository.deleteFile(text)
            }
        }
        discardBackgroundDraft()
        AppViewEvent.PopupToastMessageByResId(R.string.character_deleted).tryEmit()
        CharacterEditUiState.finished(uiStateFlow.value).setup()
    }

    /** 确认放弃未保存的修改，直接退出编辑流程。 */
    @UiIntentObserver(CharacterEditUiIntent.ConfirmDiscardChanges::class)
    private suspend fun onConfirmDiscardChanges() {
        finishEditing()
    }

    /** 关闭当前显示的弹窗。 */
    @UiIntentObserver(CharacterEditUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        uiState.copy(dialogState = CharacterEditDialogState.None).setup()
    }

    /** 辅助方法：以不可变方式更新当前角色表单数据。 */
    private fun updateForm(block: CharacterEditForm.() -> CharacterEditForm) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        uiState.copy(form = uiState.form.block()).setup()
    }

    /** 将表单转换为角色实体，名称为空时弹出 Toast 提示并返回 null。 */
    private fun CharacterEditForm.toCharacterOrNullWithToast(): Character? {
        if (name.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.character_name_empty).tryEmit()
            return null
        }
        return toCharacter()
    }

    /** 异步加载当前表单持有的头像图片并解码为 ImageBitmap。 */
    private suspend fun CharacterEditForm.resolveAvatarImage() =
        avatar.takeIf { it.isNotBlank() }?.let { text ->
            withContext(Dispatchers.IO) { mFileRepository.loadAvatarBitmap(text)?.asImageBitmap() }
        }

    /** 清理尚未提交保存的头像及背景草稿，保留已保存资源。 */
    private suspend fun cleanupPendingImages() {
        discardBackgroundDraft()
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.form.avatar.isBlank() || uiState.form.avatar == uiState.form.originalAvatar) return
        withContext(Dispatchers.IO) {
            mFileRepository.deleteFile(uiState.form.avatar)
        }
    }

    /** 结束编辑流程：清理未提交的临时头像文件并迁移至 Finished 状态。 */
    private suspend fun finishEditing() {
        cleanupPendingImages()
        CharacterEditUiState.finished(uiStateFlow.value).setup()
    }

}
