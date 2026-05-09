package me.kafuuneko.rpclient.feature.worldbook

import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.worldbook.presentation.WorldBookUiIntent
import me.kafuuneko.rpclient.feature.worldbook.presentation.WorldBookUiState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.model.LoreBookUiModel
import me.kafuuneko.rpclient.libs.model.LoreEntryUiModel

class WorldBookViewModel : CoreViewModelWithEvent<WorldBookUiIntent, WorldBookUiState>(
    WorldBookUiState.None
) {
    @UiIntentObserver(WorldBookUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<WorldBookUiState.None>()) return
        val loreBooks = previewLoreBooks()
        WorldBookUiState.Normal(
            selectedLoreBookId = loreBooks.first().id,
            loreBooks = loreBooks,
            entries = previewEntries()
        ).setup()
    }

    @UiIntentObserver(WorldBookUiIntent.Resume::class)
    private fun onResume() = Unit

    @UiIntentObserver(WorldBookUiIntent.Back::class)
    private fun onBack() {
        WorldBookUiState.Finished.setup()
    }

    @UiIntentObserver(WorldBookUiIntent.SelectLoreBook::class)
    private fun onSelectLoreBook(intent: WorldBookUiIntent.SelectLoreBook) {
        val uiState = getOrNull<WorldBookUiState.Normal>() ?: return
        uiState.copy(selectedLoreBookId = intent.loreBookId).setup()
    }

    @UiIntentObserver(WorldBookUiIntent.CreateLoreBook::class)
    private fun onCreateLoreBook() {
        AppViewEvent.PopupToastMessageByResId(R.string.world_book_creation_coming_soon).tryEmit()
    }

    @UiIntentObserver(WorldBookUiIntent.CreateEntry::class)
    private fun onCreateEntry() {
        AppViewEvent.PopupToastMessageByResId(R.string.world_book_entry_creation_coming_soon).tryEmit()
    }

    private fun previewLoreBooks() = listOf(
        LoreBookUiModel("fog-port", "雾港旧城区", "世界资料", 18, true, "今天"),
        LoreBookUiModel("case-seven", "第七份卷宗", "剧情资料", 9, true, "昨天"),
        LoreBookUiModel("night-watch", "海关夜巡队", "组织设定", 12, false, "周二")
    )

    private fun previewEntries() = listOf(
        LoreEntryUiModel(
            id = "fog-port-old-town",
            title = "旧城区排水系统",
            keywords = listOf("旧城区", "排水系统", "档案馆"),
            content = "旧城区被三条高架铁路分割，地下仍保留战前排水系统。",
            priority = 80,
            isEnabled = true
        ),
        LoreEntryUiModel(
            id = "case-seven-rule",
            title = "第七份卷宗规律",
            keywords = listOf("第七", "卷宗", "失踪名单"),
            content = "每当名单出现第七名失踪者，档案馆都会收到无署名的补充材料。",
            priority = 95,
            isEnabled = true
        )
    )
}
