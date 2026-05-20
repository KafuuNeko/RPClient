package me.kafuuneko.rpclient.libs.character

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import java.io.ByteArrayOutputStream

class CharacterCardRepository(
    private val mContext: Context,
    private val mGson: Gson,
    private val mCharacterRepository: CharacterRepository,
    private val mLorebookRepository: LorebookRepository,
    private val mFileRepository: FileRepository
) {
    private val mapper = CharacterCardMapper(mGson)

    suspend fun importFromUri(uri: Uri): Long = withContext(Dispatchers.IO) {
        val bytes = mContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Cannot read character card")
        val mime = mContext.contentResolver.getType(uri).orEmpty()
        val json = when {
            CharacterCardPngCodec.isPng(bytes) -> CharacterCardPngCodec.readCharacterJson(bytes)
            else -> bytes.toString(Charsets.UTF_8)
        }
        val avatar = if (CharacterCardPngCodec.isPng(bytes) || mime.startsWith("image/")) {
            mFileRepository.saveFile(uri, mime.ifBlank { "image/png" })
        } else {
            ""
        }
        val parsed = mapper.parseCharacter(json, avatar = avatar)
        val lorebookId = parsed.embeddedLorebook?.let { book ->
            val bookId = mLorebookRepository.saveLorebook(book.lorebook.copy(
                name = book.lorebook.name.ifBlank { "${parsed.character.name}'s Lorebook" }
            ))
            book.entries.forEach { entry ->
                mLorebookRepository.saveEntry(entry.copy(lorebookId = bookId))
            }
            bookId
        } ?: 0L
        mCharacterRepository.saveCharacter(parsed.character.copy(characterLorebookId = lorebookId))
    }

    suspend fun exportJson(characterId: Long): String = withContext(Dispatchers.IO) {
        val character = mCharacterRepository.getCharacterById(characterId) ?: error("Character not found")
        mapper.toV2Json(
            character = character,
            lorebook = character.characterLorebookId.takeIf { it != 0L }?.let { mLorebookRepository.getLorebookById(it) },
            entries = character.characterLorebookId.takeIf { it != 0L }?.let { mLorebookRepository.getEntriesByLorebookId(it) }.orEmpty()
        )
    }

    suspend fun exportPng(characterId: Long): ByteArray = withContext(Dispatchers.IO) {
        val character = mCharacterRepository.getCharacterById(characterId) ?: error("Character not found")
        val json = exportJson(characterId)
        val avatarBytes = character.avatar
            .takeIf { it.isNotBlank() }
            ?.let { mFileRepository.getFile(it) }
            ?.takeIf { it.exists() }
            ?.readBytes()
            ?: ByteArray(0)
        val pngBytes = avatarBytes.toPngOrFallback()
        CharacterCardPngCodec.writeCharacterJson(pngBytes, json)
    }

    private fun ByteArray.toPngOrFallback(): ByteArray {
        if (isNotEmpty() && CharacterCardPngCodec.isPng(this)) return this
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
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(), 0x89.toByte(),
            0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54,
            0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00, 0x05,
            0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(), 0x00, 0x00,
            0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte()
        )
    }
}
