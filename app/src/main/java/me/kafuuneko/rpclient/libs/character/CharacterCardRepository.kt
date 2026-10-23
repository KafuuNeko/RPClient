package me.kafuuneko.rpclient.libs.character

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.regex.RegexScriptCodec
import me.kafuuneko.rpclient.libs.regex.RegexScriptRepository
import me.kafuuneko.rpclient.libs.regex.RegexScriptScope
import me.kafuuneko.rpclient.libs.regex.RegexScriptTarget
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import java.io.ByteArrayOutputStream

/**
 * 角色卡文件导入导出的应用层协调器。
 *
 * 负责 Android URI 读取、头像文件保存、嵌入世界书落库，以及 JSON/PNG 两种导出形式；
 * 格式映射和 PNG chunk 操作分别委托给 [CharacterCardMapper] 与 [CharacterCardPngCodec]。
 */
class CharacterCardRepository(
    private val mContext: Context,
    private val mGson: Gson,
    private val mCharacterRepository: CharacterRepository,
    private val mLorebookRepository: LorebookRepository,
    private val mFileRepository: FileRepository,
    private val mRegexCodec: RegexScriptCodec,
    private val mRegexRepository: RegexScriptRepository
) {
    /** 无状态格式映射器，在 Repository 生命周期内复用。 */
    private val mMapper = CharacterCardMapper(mGson, mRegexCodec)

    /** 从 URI 读取并解析 JSON 或 PNG 角色卡，但不保存头像或业务实体。 */
    suspend fun readImportFromUri(uri: Uri): CharacterCardImportDraft =
        withContext(Dispatchers.IO) {
            val bytes =
                mContext.contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
                    ?: error("Cannot read character card")
            val mime = mContext.contentResolver.getType(uri).orEmpty()
            val isPng = CharacterCardPngCodec.isPng(bytes)
            val json = when {
                isPng -> CharacterCardPngCodec.readCharacterJson(bytes)
                else -> bytes.toString(Charsets.UTF_8)
            }
            CharacterCardImportDraft(
                card = mMapper.parseCharacter(json),
                avatarSourceUri = uri.takeIf { isPng || mime.startsWith("image/") },
                avatarMimeType = mime.ifBlank { "image/png" },
            )
        }

    /**
     * 保存已解析的角色卡，并返回新角色 ID。
     *
     * 头像与内嵌世界书先保存以建立有效引用；后续步骤失败时会清理本次新增资源。
     */
    suspend fun saveImport(draft: CharacterCardImportDraft): Long = withContext(Dispatchers.IO) {
        var avatar = ""
        var lorebookId = 0L
        try {
            avatar = draft.avatarSourceUri?.let { uri ->
                mFileRepository.saveFile(uri, draft.avatarMimeType)
            }.orEmpty()
            val parsed = draft.card
            lorebookId = parsed.embeddedLorebook?.let { book ->
                mLorebookRepository.saveImport(
                    parsed = book.copy(
                        lorebook = book.lorebook.copy(
                            name = book.lorebook.name.ifBlank {
                                "${parsed.character.name}'s Lorebook"
                            },
                        ),
                    ),
                )
            } ?: 0L
            mCharacterRepository.saveCharacter(
                character = parsed.character.copy(
                    avatar = avatar,
                    characterLorebookId = lorebookId,
                    extensionsJson = mRegexCodec.injectIntoCharacterExtensions(
                        json = parsed.character.extensionsJson,
                        scripts = parsed.regexScripts,
                    ),
                ),
            )
        } catch (error: Exception) {
            // 导入任务可能已被取消；补偿清理必须脱离已取消的 Job 才能执行挂起操作。
            withContext(NonCancellable) {
                if (lorebookId != 0L) {
                    runCatching { mLorebookRepository.deleteLorebook(lorebookId) }
                }
                if (avatar.isNotBlank()) {
                    runCatching { mFileRepository.deleteFile(avatar) }
                }
            }
            throw error
        }
    }

    /** 读取并导入角色卡；无需在写入前检查解析结果时使用。 */
    suspend fun importFromUri(uri: Uri): Long = withContext(Dispatchers.IO) {
        saveImport(readImportFromUri(uri))
    }

    /** 导出 Character Card V2 JSON，并在角色已绑定世界书时一并嵌入。 */
    suspend fun exportJson(characterId: Long): String = withContext(Dispatchers.IO) {
        val character =
            mCharacterRepository.getCharacterById(characterId) ?: error("Character not found")
        val regexScripts = mRegexRepository.getScripts(
            target = RegexScriptTarget(RegexScriptScope.Character, characterId),
        )
        mMapper.toV2Json(
            character = character,
            lorebook = character.characterLorebookId.takeIf { id -> id != 0L }
                ?.let { id -> mLorebookRepository.getLorebookById(id) },
            entries = character.characterLorebookId.takeIf { id -> id != 0L }
                ?.let { id -> mLorebookRepository.getEntriesByLorebookId(id) }.orEmpty(),
            regexScripts = regexScripts,
        )
    }

    /** 将角色卡 JSON 直接写入用户选择的文档 URI。 */
    suspend fun exportJsonToUri(characterId: Long, uri: Uri) = withContext(Dispatchers.IO) {
        val json = exportJson(characterId)
        mContext.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(json.toByteArray(Charsets.UTF_8))
        } ?: error("Cannot open character export destination")
    }

    /**
     * 将角色导出为带角色卡元数据的 PNG。
     *
     * 非 PNG 头像会先转换格式；无有效头像时使用最小占位图承载元数据。
     */
    suspend fun exportPng(characterId: Long): ByteArray = withContext(Dispatchers.IO) {
        val character =
            mCharacterRepository.getCharacterById(characterId) ?: error("Character not found")
        val json = exportJson(characterId)
        val avatarBytes = character.avatar
            .takeIf { text -> text.isNotBlank() }
            ?.let { text -> mFileRepository.getFile(text) }
            ?.takeIf { file -> file.exists() }
            ?.readBytes()
            ?: ByteArray(0)
        val pngBytes = avatarBytes.toPngOrFallback()
        CharacterCardPngCodec.writeCharacterJson(pngBytes, json)
    }

    private fun ByteArray.toPngOrFallback(): ByteArray {
        if (CharacterCardPngCodec.isPng(this)) return this
        val bitmap = runCatching { BitmapFactory.decodeByteArray(this, 0, size) }.getOrNull()
        if (bitmap != null) {
            return ByteArrayOutputStream().use { output ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
                output.toByteArray()
            }
        }
        return OnePixelPng
    }

    private companion object {
        val OnePixelPng = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
            0x00,
            0x00,
            0x00,
            0x0D,
            0x49,
            0x48,
            0x44,
            0x52,
            0x00,
            0x00,
            0x00,
            0x01,
            0x00,
            0x00,
            0x00,
            0x01,
            0x08,
            0x06,
            0x00,
            0x00,
            0x00,
            0x1F,
            0x15,
            0xC4.toByte(),
            0x89.toByte(),
            0x00,
            0x00,
            0x00,
            0x0A,
            0x49,
            0x44,
            0x41,
            0x54,
            0x78,
            0x9C.toByte(),
            0x63,
            0x00,
            0x01,
            0x00,
            0x00,
            0x05,
            0x00,
            0x01,
            0x0D,
            0x0A,
            0x2D,
            0xB4.toByte(),
            0x00,
            0x00,
            0x00,
            0x00,
            0x49,
            0x45,
            0x4E,
            0x44,
            0xAE.toByte(),
            0x42,
            0x60,
            0x82.toByte(),
        )
    }
}

/** 从文件解析出的、尚未持久化的角色卡及头像来源。 */
data class CharacterCardImportDraft(
    val card: CharacterCardImport,
    val avatarSourceUri: Uri?,
    val avatarMimeType: String
)
