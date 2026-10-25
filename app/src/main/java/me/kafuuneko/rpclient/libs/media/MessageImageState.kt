package me.kafuuneko.rpclient.libs.media

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap

/** 共享图片交互状态；原始凭据只在协调器内持有。 */
data class MessageImageState(
    val draft: List<String> = emptyList(),
    val editing: List<String> = emptyList(),
    val thumbnails: Map<String, ImageBitmap?> = emptyMap(),
    val loadState: MessageImageLoadState = MessageImageLoadState.None,
    val canAddDraft: Boolean = true,
    val canAddEditing: Boolean = true,
    /** 图片选择或处理失败时展示的本地化提示资源；null 表示当前没有错误。 */
    @param:StringRes val errorResId: Int? = null,
    val preview: ImagePreviewState? = null
)

/** 图片准备和提交的互斥任务状态，提交期间禁止修改或取消已冻结的附件。 */
sealed class MessageImageLoadState {
    data object None : MessageImageLoadState()

    data object Preparing : MessageImageLoadState()

    data object Submitting : MessageImageLoadState()

    /** 是否有图片任务正在执行，由唯一任务状态派生。 */
    val isBusy: Boolean get() = this !is None

    /** 是否已经冻结附件并进入提交阶段。 */
    val isSubmitting: Boolean get() = this is Submitting
}

/** 大图查看器状态，切换或关闭时释放前一张大图引用。 */
data class ImagePreviewState(
    val ids: List<String>,
    val index: Int,
    val bitmap: ImageBitmap? = null,
    val loading: Boolean = true
)

/** 两类聊天共用的图片用户行为，由页面 Intent 包装后交给 ViewModel。 */
sealed interface MessageImageAction {
    data class Choose(val editing: Boolean = false) : MessageImageAction

    data class Picked(val uris: List<Uri>) : MessageImageAction

    data class Remove(val uuid: String, val editing: Boolean = false) : MessageImageAction

    data class Move(val uuid: String, val editing: Boolean = false) : MessageImageAction

    data class Load(val uuid: String) : MessageImageAction

    data class RegisterDisplay(val uuid: String) : MessageImageAction

    data class ReleaseDisplay(val uuid: String) : MessageImageAction

    data class Preview(val ids: List<String>, val index: Int) : MessageImageAction

    data object CancelProcessing : MessageImageAction

    data object ClosePreview : MessageImageAction

    data object Save : MessageImageAction

    data class SaveResult(val uri: Uri) : MessageImageAction
}
