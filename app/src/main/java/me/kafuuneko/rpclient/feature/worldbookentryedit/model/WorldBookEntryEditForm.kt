package me.kafuuneko.rpclient.feature.worldbookentryedit.model

import com.google.gson.Gson
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.utils.toJsonString

data class WorldBookEntryEditForm(
    val id: Long = 0L,
    val lorebookId: Long = 0L,
    val name: String = "",
    val keywords: List<String> = listOf(""),
    val secondaryKeywords: List<String> = listOf(""),
    val order: String = "100",
    val depth: String = "0",
    val category: List<String> = listOf(""),
    val content: String = ""
) {
    val isNew: Boolean
        get() = id == 0L

    companion object {
        fun from(entry: LorebookEntry): WorldBookEntryEditForm {
            return WorldBookEntryEditForm(
                id = entry.id,
                lorebookId = entry.lorebookId,
                name = entry.name,
                keywords = entry.getKeywordList().ifEmpty { listOf("") },
                secondaryKeywords = entry.getSecondaryKeywordList().ifEmpty { listOf("") },
                order = entry.order.toString(),
                depth = entry.depth.toString(),
                category = entry.getCategoryList().ifEmpty { listOf("") },
                content = entry.content
            )
        }
    }

    fun toLorebookEntryOrNull(): LorebookEntry? {
        val orderValue = order.trim().toIntOrNull() ?: return null
        val depthValue = depth.trim().toIntOrNull() ?: return null
        return LorebookEntry(
            id = id,
            lorebookId = lorebookId,
            name = name.trim(),
            keywords = Gson().toJsonString(keywords.cleanList()),
            secondaryKeywords = Gson().toJsonString(secondaryKeywords.cleanList()),
            order = orderValue,
            depth = depthValue,
            category = Gson().toJsonString(category.cleanList()),
            content = content.trim()
        )
    }
}

fun WorldBookEntryEditForm.toComparableForm(): WorldBookEntryEditForm {
    return copy(
        name = name.trim(),
        keywords = keywords.cleanList(),
        secondaryKeywords = secondaryKeywords.cleanList(),
        order = order.trim(),
        depth = depth.trim(),
        category = category.cleanList(),
        content = content.trim()
    )
}

private fun List<String>.cleanList(): List<String> {
    return map { it.trim() }.filter { it.isNotEmpty() }
}

