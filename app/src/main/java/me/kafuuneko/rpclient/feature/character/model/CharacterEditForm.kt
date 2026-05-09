package me.kafuuneko.rpclient.feature.character.model

import com.google.gson.Gson
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.utils.toJsonString

data class CharacterEditForm(
    val id: Long = 0L,
    val name: String = "",
    val avatar: String = "",
    val originalAvatar: String = "",
    val tagsText: String = "",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMessages: String = "",
    val examplesOfDialogue: String = "",
    val postHistoryInstructions: String = ""
) {
    val isNew: Boolean
        get() = id == 0L

    val parsedTags: List<String>
        get() = tagsText.split(",", "，", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    companion object {
        fun from(character: Character) = CharacterEditForm(
            id = character.id,
            name = character.name,
            avatar = character.avatar,
            originalAvatar = character.avatar,
            tagsText = character.getCharacterTagList().joinToString(", "),
            description = character.description,
            personality = character.personality,
            scenario = character.scenario,
            firstMessages = character.firstMessages,
            examplesOfDialogue = character.examplesOfDialogue,
            postHistoryInstructions = character.postHistoryInstructions
        )
    }

    fun toCharacter(gson: Gson): Character {
        return Character(
            id = id,
            name = name.trim(),
            avatar = avatar.trim(),
            characterTags = gson.toJsonString(parsedTags),
            description = description.trim(),
            personality = personality.trim(),
            scenario = scenario.trim(),
            firstMessages = firstMessages.trim(),
            examplesOfDialogue = examplesOfDialogue.trim(),
            postHistoryInstructions = postHistoryInstructions.trim()
        )
    }
}
