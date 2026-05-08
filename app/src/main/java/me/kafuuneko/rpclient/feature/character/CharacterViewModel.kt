package me.kafuuneko.rpclient.feature.character

import me.kafuuneko.rpclient.feature.character.presentation.CharacterUiIntent
import me.kafuuneko.rpclient.feature.character.presentation.CharacterUiState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.model.RpCharacterUiModel

class CharacterViewModel : CoreViewModelWithEvent<CharacterUiIntent, CharacterUiState>(
    CharacterUiState.None
) {
    @UiIntentObserver(CharacterUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<CharacterUiState.None>()) return
        val characters = previewCharacters()
        CharacterUiState.Normal(
            selectedCharacterId = characters.first().id,
            characters = characters
        ).setup()
    }

    @UiIntentObserver(CharacterUiIntent.Resume::class)
    private fun onResume() = Unit

    @UiIntentObserver(CharacterUiIntent.Back::class)
    private fun onBack() {
        CharacterUiState.Finished.setup()
    }

    @UiIntentObserver(CharacterUiIntent.SelectCharacter::class)
    private fun onSelectCharacter(intent: CharacterUiIntent.SelectCharacter) {
        val uiState = getOrNull<CharacterUiState.Normal>() ?: return
        uiState.copy(selectedCharacterId = intent.characterId).setup()
    }

    @UiIntentObserver(CharacterUiIntent.ImportCharacter::class)
    private fun onImportCharacter() {
        AppViewEvent.PopupToastMessage("角色卡导入逻辑稍后接入").tryEmit()
    }

    @UiIntentObserver(CharacterUiIntent.CreateCharacter::class)
    private fun onCreateCharacter() {
        AppViewEvent.PopupToastMessage("角色创建逻辑稍后接入").tryEmit()
    }

    private fun previewCharacters() = listOf(
        RpCharacterUiModel(
            id = "lyra",
            name = "Lyra",
            subtitle = "雾港档案管理员",
            description = "记忆力惊人的城市档案员，擅长在旧报纸和失踪人口记录里找出被隐藏的线索。",
            avatarText = "L",
            tags = listOf("悬疑", "慢热", "现代奇幻"),
            sessions = 12,
            updatedAt = "18 分钟前",
            accentColor = 0xFF315EFD
        ),
        RpCharacterUiModel(
            id = "noah",
            name = "Noah",
            subtitle = "边境医师",
            description = "在荒原移动诊所工作，温柔、克制，对每个病人的秘密都守口如瓶。",
            avatarText = "N",
            tags = listOf("治愈", "旅行", "低魔"),
            sessions = 5,
            updatedAt = "昨天",
            accentColor = 0xFF0F9F8F
        ),
        RpCharacterUiModel(
            id = "seren",
            name = "Seren",
            subtitle = "星舰代理舰长",
            description = "临危受命的年轻舰长，需要在资源耗尽前带领船员穿过未标记星域。",
            avatarText = "S",
            tags = listOf("科幻", "群像", "策略"),
            sessions = 8,
            updatedAt = "周二",
            accentColor = 0xFFB55A12
        )
    )
}

