package me.kafuuneko.rpclient.feature.storycreate.model

/** 故事创建候选数据容器。 */
internal data class StoryCreateOptions(
    val characters: List<StoryCreateCharacterItem>,
    val lorebookGroups: List<StoryCreateLorebookGroupItem>
)
