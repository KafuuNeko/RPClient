package me.kafuuneko.rpclient.feature.characteredit.model

import com.google.gson.Gson
import me.kafuuneko.rpclient.libs.room.entity.Character
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
    val postHistoryInstructions: String = ""
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
            postHistoryInstructions = character.postHistoryInstructions
        )
    }

    fun toCharacter(): Character {
        return Character(
            id = id,
            name = name.trim(),
            avatar = avatar.trim(),
            characterTags = Gson().toJsonString(tags.map { it.trim() }.filter { it.isNotEmpty() }),
            description = description.trim(),
            creatorNotes = creatorNotes.trim(),
            personality = personality.trim(),
            scenario = scenario.trim(),
            firstMessages = firstMessages
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("<START>"),
            examplesOfDialogue = examplesOfDialogue.trim(),
            postHistoryInstructions = postHistoryInstructions.trim()
        )
    }
}
