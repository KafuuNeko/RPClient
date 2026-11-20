package me.kafuuneko.rpclient.libs.prompt.preset

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.room.repository.PromptPresetRepository
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * 提示词便携包的文件边界。
 * - 文件读写在 IO 调度器执行，Activity 仅提供系统选择的 URI。
 * - 限制输入体积并拒绝损坏的 UTF-8，解析成功后再由预设仓库原子写入。
 */
class PromptPresetTransferRepository(
    private val mContext: Context,
    private val mPresets: PromptPresetRepository,
) {
    /**
     * 导入系统选择的文件，不随导入切换活动预设。
     * @param uri 获得读取授权的文档 URI。
     * @return 新预设 ID。
     */
    suspend fun importPreset(uri: Uri): Long = withContext(Dispatchers.IO) {
        val source =
            requireNotNull(mContext.contentResolver.openInputStream(uri)) { "Cannot read prompt preset" }
        val bytes = source.use(::readBounded)
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        mPresets.importPreset(decoder.decode(ByteBuffer.wrap(bytes)).toString())
    }

    /**
     * 写入点击导出时捕获的快照，文件选择期间的编辑不会替换导出内容。
     * @param uri 获得写入授权的文档 URI。
     * @param json 已校验的便携包快照。
     */
    suspend fun exportPreset(uri: Uri, json: String) = withContext(Dispatchers.IO) {
        require(json.toByteArray(Charsets.UTF_8).size <= PromptPresetCodec.MAX_BYTES) { "Prompt preset exceeds size limit" }
        requireNotNull(
            mContext.contentResolver.openOutputStream(
                uri,
                "wt"
            )
        ) { "Cannot write prompt preset" }
            .bufferedWriter(Charsets.UTF_8).use { it.write(json) }
    }

    /** 在缓冲区追加前检查字节上限，不信任文档提供者报告的文件大小。 */
    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return output.toByteArray()
            require(output.size() + count <= PromptPresetCodec.MAX_BYTES) { "Prompt preset exceeds size limit" }
            output.write(buffer, 0, count)
        }
    }
}
