package me.kafuuneko.rpclient.libs.room.model

import me.kafuuneko.rpclient.libs.room.entity.FileEntity

/** 原图暂存凭据；私有路径不进入页面状态，句柄必须由文件仓库签发和校验。 */
@ConsistentCopyVisibility
data class PreparedFile internal constructor(
    val ownerId: String,
    val handle: String,
    val file: FileEntity,
    val byteCount: Long
)

/** 图文编辑中的有序附件输入；保留项必须属于正在编辑的消息。 */
sealed interface MessageImageInput {
    data class Prepared(val value: PreparedFile) : MessageImageInput
    data class Existing(val uuid: String) : MessageImageInput
}

/** 所有图片入口共用的初始资源策略；图像解码和请求阶段继续复用这些限制。 */
object MessageImagePolicy {
    const val MAX_IMAGES_PER_MESSAGE = 4
    const val MAX_IMAGES_PER_REQUEST = 12
    const val MAX_ORIGINAL_BYTES = 32L * 1024 * 1024
    const val MAX_ORIGINAL_PIXELS = 48_000_000L
    const val SEND_LONG_EDGE = 1536
    const val MAX_SEND_BYTES = 2L * 1024 * 1024
    const val MAX_REQUEST_BYTES = 16L * 1024 * 1024
    const val UNKNOWN_IMAGE_TOKENS = 4096
}
