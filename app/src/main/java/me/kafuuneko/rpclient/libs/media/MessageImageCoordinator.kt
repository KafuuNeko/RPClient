package me.kafuuneko.rpclient.libs.media

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.llm.ImageRequestException
import me.kafuuneko.rpclient.libs.llm.ImageRequestFailure
import me.kafuuneko.rpclient.libs.room.model.MessageImageInput
import me.kafuuneko.rpclient.libs.room.model.MessageImagePolicy
import me.kafuuneko.rpclient.libs.room.model.PreparedFile
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import java.util.UUID

/**
 * 每个聊天 ViewModel 独占的图片交互协调器。
 * - 草稿和选择器状态只驻留内存，跟随所属 ViewModel，不做跨进程恢复。
 * - IO 委托图片服务，状态发布回调只由 ViewModel 更新状态树。
 */
class MessageImageCoordinator(
    private val mRuntime: MessageImageRuntime,
    private val mFiles: FileRepository,
    private val mChanged: (MessageImageState) -> Unit
) {
    /** 系统选择器的单次内存归属；新 ViewModel 不接纳旧页面的在途结果。 */
    private data class PendingPick(val editing: Boolean, val editingVersion: Long)

    private val mOwner = UUID.randomUUID().toString()
    private val mPrepared = mutableMapOf<String, PreparedFile>()
    private val mDisplayReferences = mutableMapOf<String, Int>()
    private val mLoading = mutableSetOf<String>()
    private var mPendingPick: PendingPick? = null
    private var mSaveUuid: String? = null
    private var mEditingActive = false

    // 每次退出或切换编辑都使旧选择器结果失效，不能只判断是否仍处于编辑态。
    private var mEditingVersion = 0L
    private var mProcessingJob: Job? = null
    private var mProcessingEditing = false

    // 取消后允许新任务立即开始，旧任务的 finally 必须失去发布状态的权限。
    private var mProcessingVersion = 0L
    var state = MessageImageState()
        private set

    /** 页面销毁时释放仍未提交的暂存；已提交的 UUID 已从所有权集合移除。 */
    suspend fun releaseDrafts() {
        mPendingPick = null
        mPrepared.values.toList().forEach { mFiles.releasePrepared(it) }
        mPrepared.clear()
        mDisplayReferences.clear()
        mLoading.clear()
    }

    /**
     * 根据用户选择的输入位置记录本次系统选择器目标。
     *
     * @param editing 是否将本次选择的图片加入历史消息编辑区。
     */
    fun choose(editing: Boolean) {
        if (state.processing) return
        mPendingPick = PendingPick(editing, mEditingVersion)
    }

    /** 立即撤销任务的状态发布权；底层资源在 IO 线程取消，不阻塞串行 Intent。 */
    fun cancelProcessing() {
        if (state.submitting) return
        mProcessingVersion++
        mProcessingJob?.cancel()
        mProcessingJob = null
        mProcessingEditing = false
        publish(state.copy(processing = false))
    }

    /**
     * 返回草稿凭据；只允许全部图片准备完成后提交。
     *
     * @return 按草稿显示顺序排列的暂存附件。
     * @throws IllegalStateException 图片尚在处理中时抛出。
     * @throws IllegalArgumentException 草稿的暂存凭据缺失时抛出。
     */
    fun draftInputs(): List<MessageImageInput.Prepared> {
        check(!state.processing) { "Images are still being processed" }
        return state.draft.map { MessageImageInput.Prepared(requireNotNull(mPrepared[it])) }
    }

    /**
     * 返回编辑后的顺序，保留项交给消息仓库验证所有权。
     *
     * @return 按编辑顺序排列的已有附件和新增暂存附件。
     */
    fun editingInputs(): List<MessageImageInput> = state.editing.map { text ->
        mPrepared[text]?.let(MessageImageInput::Prepared) ?: MessageImageInput.Existing(text)
    }

    /**
     * 冻结附件并提交最终图片；失败保持当前编辑或新消息草稿供重试。
     *
     * @param editing 是否提交历史消息编辑。
     * @param commit 将处理后的附件与正文一起提交的操作。
     * @return 已提交消息的业务结果。
     */
    suspend fun <T> submit(
        editing: Boolean = false,
        commit: suspend (List<MessageImageInput>) -> T
    ): T {
        check(!state.processing) { "Images are still being processed" }
        val inputs = if (editing) editingInputs() else draftInputs()
        val ids = inputs.filterIsInstance<MessageImageInput.Prepared>().map { it.value.file.uuid }
        mPendingPick = null
        publish(state.copy(processing = true, submitting = true, errorResId = null))
        try {
            return mRuntime.submit(inputs) { finalInputs ->
                val result = commit(finalInputs)
                // 回调处于不可取消的提交收尾中；清除原草稿缩略图，历史改用最终文件 UUID。
                ids.forEach { text -> mPrepared.remove(text) }
                if (editing) {
                    mEditingActive = false
                    mEditingVersion++
                }
                publish(
                    value = state.copy(
                        draft = if (editing) state.draft else emptyList(),
                        editing = if (editing) emptyList() else state.editing,
                        thumbnails = state.thumbnails - ids.toSet(),
                        preview = state.preview?.takeUnless { preview -> preview.ids.any { text -> text in ids } },
                    ),
                )
                result
            }
        } finally {
            publish(state.copy(processing = false, submitting = false))
        }
    }

    /**
     * 进入历史图文编辑；原附件不能因为取消编辑而被删除。
     *
     * @param ids 当前消息已有的附件 ID，顺序与消息一致。
     */
    suspend fun startEditing(ids: List<String>) {
        if (state.submitting) return
        cancelEditing()
        mEditingActive = true
        publish(state.copy(editing = ids))
    }

    /** 取消编辑只释放本次编辑新增的暂存。 */
    suspend fun cancelEditing() {
        if (state.submitting) return
        mEditingActive = false
        mEditingVersion++
        if (mProcessingEditing) cancelProcessing()
        state.editing.filter { it !in state.draft }.forEach { uuid ->
            mPrepared.remove(uuid)?.let { preparedFile -> mFiles.releasePrepared(preparedFile) }
        }
        publish(state.copy(editing = emptyList()))
    }

    /**
     * 保存选择器启动前冻结图片 ID，并检查实际格式。
     *
     * @return 创建文档所需的元数据；没有可保存图片或准备失败时为 null。
     */
    suspend fun beginSave(): ImageExportMetadata? {
        mSaveUuid = null
        val preview = state.preview ?: return null
        val uuid = preview.ids.getOrNull(preview.index) ?: return null
        return try {
            // 元数据就绪后才登记保存目标，预览切换不会改变此次选择的图片。
            val metadata = mRuntime.exportMetadata(uuid, mPrepared[uuid])
            mSaveUuid = uuid
            metadata
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            publish(state.copy(errorResId = R.string.image_prepare_failed))
            null
        }
    }

    /**
     * 处理图片行为；失败只展示受控提示，取消继续向上传播。
     *
     * @param action 页面发出的图片操作。
     */
    suspend fun handle(action: MessageImageAction) {
        val shouldBlockDraftMutation = state.submitting
            && (action is MessageImageAction.Picked
            || action is MessageImageAction.Remove
            || action is MessageImageAction.Move)
        if (shouldBlockDraftMutation) return
        try {
            // 暂存、历史加载与图片保存走同一个受控资源入口，页面不直接读取文件。
            when (action) {
                is MessageImageAction.Picked -> pick(action)
                is MessageImageAction.Load -> load(action.uuid)
                is MessageImageAction.RegisterDisplay -> {
                    mDisplayReferences[action.uuid] = (mDisplayReferences[action.uuid] ?: 0) + 1
                    load(action.uuid)
                }

                is MessageImageAction.ReleaseDisplay -> {
                    val remaining = (mDisplayReferences[action.uuid] ?: 0) - 1
                    if (remaining > 0) mDisplayReferences[action.uuid] = remaining
                    else mDisplayReferences.remove(action.uuid)
                    publish(state)
                }

                is MessageImageAction.Remove -> remove(action)
                is MessageImageAction.Move -> move(action)
                is MessageImageAction.Preview -> preview(action)
                MessageImageAction.ClosePreview -> publish(state.copy(preview = null))
                is MessageImageAction.SaveResult -> mSaveUuid?.let { text ->
                    mRuntime.save(text, action.uri, mPrepared[text])
                    mSaveUuid = null
                }

                else -> Unit
            }
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            // 失败只发布提示；选图和预览各自负责结束状态，不能解除其他任务的保护。
            publish(state.copy(errorResId = prepareErrorRes(error)))
        }
    }

    /** 原图模式失败给出可操作的设置引导，其余资源错误沿用通用受控提示。 */
    @StringRes
    private fun prepareErrorRes(error: Exception): Int =
        when ((error as? ImageRequestException)?.failure) {
            ImageRequestFailure.OriginalUnsupported -> R.string.image_error_original_format
            ImageRequestFailure.OriginalTooLarge -> R.string.image_error_original_large
            else -> R.string.image_prepare_failed
        }

    /**
     * 逐图复制并立即取得私有暂存；部分选择失败时保留之前成功的草稿。
     *
     * @param action 系统选择器返回的 URI 列表。
     */
    private suspend fun pick(action: MessageImageAction.Picked) {
        // 包括取消在内，每次回调都消费选择记录；进程重建或重复结果不能默认归入新草稿。
        val pendingPick = mPendingPick
        mPendingPick = null
        if (action.uris.isEmpty() || state.processing) return
        if (pendingPick == null) {
            publish(state.copy(errorResId = R.string.image_prepare_failed))
            return
        }
        val editing = pendingPick.editing
        val editingVersion = pendingPick.editingVersion
        // 编辑已经结束时，迟到的选择器结果不能误加到新消息草稿。
        if (editing && (!mEditingActive || editingVersion != mEditingVersion)) {
            publish(state.copy(errorResId = R.string.image_edit_ended))
            return
        }
        val initial = if (editing) state.editing else state.draft
        require(initial.size + action.uris.size <= MessageImagePolicy.MAX_IMAGES_PER_MESSAGE) { "A message can contain at most four images" }
        val processingVersion = ++mProcessingVersion
        mProcessingJob = currentCoroutineContext()[Job]
        mProcessingEditing = editing
        publish(state.copy(processing = true, errorResId = null))
        try {
            for (uri in action.uris) {
                val prepared = mRuntime.prepare(mOwner, uri)
                // 旧任务即使迟到返回，也只能释放资源，不能把 A 的图片交给 B 或新草稿。
                val isPreparedImageOutdated = processingVersion != mProcessingVersion
                    || (editing
                    && (!mEditingActive
                    || editingVersion != mEditingVersion))
                if (isPreparedImageOutdated) {
                    mFiles.releasePrepared(prepared)
                    return
                }
                val uuid = prepared.file.uuid
                mPrepared[uuid] = prepared
                publish(
                    if (editing) state.copy(editing = state.editing + uuid) else state.copy(
                        draft = state.draft + uuid
                    )
                )
                load(uuid)
            }
        } finally {
            if (processingVersion == mProcessingVersion) {
                mProcessingJob = null
                mProcessingEditing = false
                publish(state.copy(processing = false))
            }
        }
    }

    /**
     * 同一 UUID 的解码去重；null 是已完成的缺图结果，不能自动循环重试。
     *
     * @param uuid 需要显示缩略图的图片 ID。
     */
    private suspend fun load(uuid: String) {
        if (state.thumbnails.containsKey(uuid) || !mLoading.add(uuid)) return
        try {
            val bitmap = mRuntime.load(uuid, mPrepared[uuid])?.asImageBitmap()
            publish(state.copy(thumbnails = state.thumbnails + (uuid to bitmap)))
        } finally {
            mLoading.remove(uuid)
        }
    }

    /**
     * 调整草稿中的顺序，不改变任何文件所有权。
     *
     * @param action 要前移的图片及其所属编辑区域。
     */
    private fun move(action: MessageImageAction.Move) {
        val ids = (if (action.editing) state.editing else state.draft).toMutableList()
        val index = ids.indexOf(action.uuid)
        if (index <= 0) return
        ids.removeAt(index)
        ids.add(index - 1, action.uuid)
        publish(if (action.editing) state.copy(editing = ids) else state.copy(draft = ids))
    }

    /**
     * 大图仅在当前查看器仍对应同一次请求时发布，避免迟到结果覆盖新图。
     *
     * @param action 消息内图片列表和目标位置。
     */
    private suspend fun preview(action: MessageImageAction.Preview) {
        val uuid = action.ids.getOrNull(action.index) ?: return
        val preview = ImagePreviewState(action.ids, action.index)
        publish(state.copy(preview = preview))
        try {
            // 解码期间允许其他图片操作，返回时只更新本次仍可见的预览。
            val bitmap = mRuntime.load(uuid = uuid, prepared = mPrepared[uuid], longEdge = 3072)
            if (state.preview === preview) {
                publish(
                    state.copy(
                        preview = preview.copy(
                            bitmap = bitmap?.asImageBitmap(),
                            loading = false
                        )
                    )
                )
            }
        } finally {
            // 异常与取消同样结束本次加载，不能覆盖后来打开的另一张图片。
            if (state.preview === preview) {
                publish(state.copy(preview = preview.copy(loading = false)))
            }
        }
    }

    /**
     * 移除草稿释放暂存；移除历史附件仅更改编辑顺序，保存时才删除。
     *
     * @param action 要移除的图片及其所属编辑区域。
     */
    private suspend fun remove(action: MessageImageAction.Remove) {
        mPrepared.remove(action.uuid)?.let { mFiles.releasePrepared(it) }
        publish(
            value = if (action.editing) state.copy(editing = state.editing - action.uuid)
            else state.copy(draft = state.draft - action.uuid),
        )
    }

    /**
     * 草稿、编辑和组合中的图片受到保护；64 是历史缓存回收目标。
     * 保护项超过目标时允许短时超出，解除保护后立即回收，避免可见图退回加载状态。
     */
    private fun publish(value: MessageImageState) {
        val protected = value.draft.toSet() + value.editing + mDisplayReferences.keys
        val removable = value.thumbnails.keys.filter { it !in protected }
        val excess = (value.thumbnails.size - HISTORY_CACHE_TARGET).coerceAtLeast(0)
        state = value.copy(
            thumbnails = value.thumbnails - removable.take(excess).toSet(),
            canAddDraft = value.draft.size < MessageImagePolicy.MAX_IMAGES_PER_MESSAGE,
            canAddEditing = value.editing.size < MessageImagePolicy.MAX_IMAGES_PER_MESSAGE,
        )
        mChanged(state)
    }

    private companion object {
        const val HISTORY_CACHE_TARGET = 64
    }
}
