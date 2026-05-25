package me.kafuuneko.rpclient.feature.characteredit.model

import com.google.gson.Gson
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.utils.trimmedNotBlank
import me.kafuuneko.rpclient.utils.toJsonString

data class CharacterEditForm(
    val id: Long = 0L,
    val name: String = "",
    val avatar: String = "",
    val originalAvatar: String = "",
    val tags: List<String> = emptyList(),
    val description: String = "",
    val creatorNotes: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMessages: List<String> = emptyList(),
    val examplesOfDialogue: String = "",
    val postHistoryInstructions: String = "",
    val systemPrompt: String = "",
    val creator: String = "",
    val characterVersion: String = "",
    val alternateGreetings: List<String> = emptyList(),
    val extensionsJson: String = "{}",
    val depthPromptPrompt: String = "",
    val depthPromptDepth: String = "4",
    val depthPromptRole: String = "0",
    val characterLorebookId: Long = 0L
) {
    val isNew: Boolean
        get() = id == 0L

    companion object {
        fun from(character: Character) = CharacterEditForm(
            id = character.id,
            name = character.name,
            avatar = character.avatar,
            originalAvatar = character.avatar,
            tags = character.getCharacterTagList(),
            description = character.description,
            creatorNotes = character.creatorNotes,
            personality = character.personality,
            scenario = character.scenario,
            firstMessages = character.getFirstMessageList(),
            examplesOfDialogue = character.examplesOfDialogue,
            postHistoryInstructions = character.postHistoryInstructions,
            systemPrompt = character.systemPrompt,
            creator = character.creator,
            characterVersion = character.characterVersion,
            alternateGreetings = character.getAlternateGreetingList(),
            extensionsJson = character.extensionsJson,
            depthPromptPrompt = character.depthPromptPrompt,
            depthPromptDepth = character.depthPromptDepth.toString(),
            depthPromptRole = character.depthPromptRole.toString(),
            characterLorebookId = character.characterLorebookId
        )
    }

    fun toCharacter(): Character {
        return Character(
            id = id,
            name = name.trim(),
            avatar = avatar.trim(),
            characterTags = Gson().toJsonString(tags.trimmedNotBlank()),
            description = description.trim(),
            creatorNotes = creatorNotes.trim(),
            personality = personality.trim(),
            scenario = scenario.trim(),
            firstMessages = firstMessages
                .trimmedNotBlank()
                .joinToString("<START>"),
            examplesOfDialogue = examplesOfDialogue.trim(),
            postHistoryInstructions = postHistoryInstructions.trim(),
            systemPrompt = systemPrompt.trim(),
            creator = creator.trim(),
            characterVersion = characterVersion.trim(),
            alternateGreetings = Gson().toJsonString(alternateGreetings.trimmedNotBlank()),
            extensionsJson = extensionsJson.trim().ifBlank { "{}" },
            depthPromptPrompt = depthPromptPrompt.trim(),
            depthPromptDepth = depthPromptDepth.trim().toIntOrNull()?.coerceAtLeast(0) ?: 4,
            depthPromptRole = depthPromptRole.trim().toIntOrNull()?.coerceIn(0, 2) ?: 0,
            characterLorebookId = characterLorebookId
        )
    }
}
