package me.kafuuneko.rpclient.libs.backup

import java.io.File
import java.util.UUID
import me.kafuuneko.rpclient.libs.chat.ChatArchiveImage
import me.kafuuneko.rpclient.libs.chat.ChatArchiveImagePayload
import me.kafuuneko.rpclient.libs.media.MessageImageValidator
import me.kafuuneko.rpclient.libs.room.entity.FileEntity

/**
 * 完整恢复过程中只访问归档暂存目录的图片仓库。
 * - 解码时立即释放 Base64，复用聊天协议的解压大小和内容摘要校验。
 * - 原始字节按哈希保存，图片关系各自持有独立文件 UUID。
 */
internal class BackupImageStore(private val mDirectory: File) {
    private val mMimeTypes = mutableMapOf<String, String>()

    /**
     * 将内嵌载荷写入暂存，纯哈希引用只解析归档内已有的附件。
     * @param image 聊天协议解码的图片描述。
     * @return 移除 Base64 后的描述；无法找到字节的引用保持未解析状态。
     */
    fun stage(image: ChatArchiveImage): ChatArchiveImage {
        if (image.data != null) return stageEmbedded(image)
        val hash = image.hash ?: return image
        require(hash.matches(Regex("[0-9a-f]{64}"))) { "Invalid archive image hash" }
        if (hash !in mMimeTypes) {
            val file = File(mDirectory, hash)
            if (!file.isFile) return image
            // 附件文件名不能替代内容校验；真实 MIME 与普通图片导入使用同一规则。
            val mimeType = MessageImageValidator.mimeType(file)
            require(BackupArchiveFiles.sha256(file) == hash) { "Archive image hash mismatch" }
            mMimeTypes[hash] = mimeType
        }
        return image.copy(mimeType = mMimeTypes.getValue(hash), compression = "none")
    }

    /**
     * 为已校验的图片签发独立文件索引，缺少本地字节时返回 null。
     * @param image 已经过 [stage] 的图片描述。
     * @return 不携带文件路径的新索引，尚未写入任何数据库。
     */
    fun restoredFile(image: ChatArchiveImage): FileEntity? {
        val hash = image.hash ?: return null
        val mimeType = mMimeTypes[hash] ?: return null
        return FileEntity(uuid = UUID.randomUUID().toString(), hash = hash, mimeType = mimeType)
    }

    /** 解码、校验全部成功后才替换同哈希附件，失败时只删除本次临时文件。 */
    private fun stageEmbedded(image: ChatArchiveImage): ChatArchiveImage {
        check(mDirectory.isDirectory || mDirectory.mkdirs()) { "Cannot create backup image directory" }
        val temporary = File.createTempFile("image-", ".tmp", mDirectory)
        try {
            val hash = ChatArchiveImagePayload.open(image).use { input ->
                temporary.outputStream().use { output ->
                    ChatArchiveImagePayload.copyOriginal(input, output, image.hash)
                }
            }
            // 不使用归档声明的 MIME，不对恢复图片应用当前的发送压缩设置。
            val mimeType = MessageImageValidator.mimeType(temporary)
            BackupArchiveFiles.replaceFile(temporary, File(mDirectory, hash))
            mMimeTypes[hash] = mimeType
            return image.copy(data = null, hash = hash, mimeType = mimeType, compression = "none")
        } finally {
            temporary.delete()
        }
    }
}
