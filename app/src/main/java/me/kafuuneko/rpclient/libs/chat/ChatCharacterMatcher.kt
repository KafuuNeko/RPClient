package me.kafuuneko.rpclient.libs.chat

import me.kafuuneko.rpclient.libs.room.entity.Character
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * 为对话导入提供角色卡匹配建议。
 *
 * 指纹与名称都只用于预选；最终关联仍必须由用户确认。重复匹配不会自动选择，避免同名角色
 * 或重复导入的角色卡被静默绑定。
 */
object ChatCharacterMatcher {
    fun suggestCharacterId(archive: ChatArchive, characters: List<Character>): Long? {
        val fingerprint = archive.characterFingerprint
        if (!fingerprint.isNullOrBlank()) {
            characters.singleOrNull { character ->
                fingerprint.equals(other = fingerprintOf(character), ignoreCase = true)
            }?.let { return it.id }
        }

        val name = archive.characterNameHint.trim()
        if (name.isBlank()) return null
        return characters.singleOrNull { character ->
            character.name.trim().equals(other = name, ignoreCase = true)
        }?.id
    }

    /**
     * 对角色卡可迁移内容生成稳定指纹。
     *
     * 本地头像文件 UUID 和世界书主键刻意不参与计算；它们在不同安装中不具备稳定含义。
     */
    fun fingerprintOf(character: Character): String {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(
            character.name,
            character.characterTags,
            character.description,
            character.creatorNotes,
            character.personality,
            character.scenario,
            character.firstMessages,
            character.examplesOfDialogue,
            character.postHistoryInstructions,
            character.systemPrompt,
            character.creator,
            character.characterVersion,
            character.alternateGreetings,
            character.extensionsJson,
            character.depthPromptPrompt,
            character.depthPromptDepth.toString(),
            character.depthPromptRole.toString(),
        ).forEach { value ->
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update(
                ByteBuffer.allocate(Int.SIZE_BYTES)
                .putInt(bytes.size)
                .array(),
            )
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
