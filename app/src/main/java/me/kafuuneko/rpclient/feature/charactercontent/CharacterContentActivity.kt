package me.kafuuneko.rpclient.feature.charactercontent

import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.charactercontent.presentation.CharacterContentUiIntent
import me.kafuuneko.rpclient.feature.charactercontent.presentation.CharacterContentUiState
import me.kafuuneko.rpclient.feature.charactercontent.ui.CharacterContentLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent

/** 角色内容页宿主，沿用通用页面背景与应用导航事件。 */
class CharacterContentActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<CharacterContentViewModel>()
    private val mCharacterId get() = intent.getLongExtra(EXTRA_CHARACTER_ID, 0L)

    /** 用当前角色的背景替换宿主底板，加载和退出时也沿用同一张图片。 */
    override fun observePageBackground() = mViewModel.observeBackground(mCharacterId)

    /** 交由通用宿主执行导航和提示。 */
    override fun getViewEventFlow() = mViewModel.viewEventFlow

    /** 首次进入和从子页面返回时，都刷新当前角色的关联内容。 */
    override fun onResume() {
        super.onResume()
        mViewModel.emit(CharacterContentUiIntent.Resume(characterId = mCharacterId))
    }

    /** 保留退出动画所需的最后一帧页面。 */
    @Composable
    override fun ViewContent() {
        val state by mViewModel.uiStateFlow.collectAsState()
        LaunchedEffect(state) { if (state is CharacterContentUiState.Finished) finish() }
        CharacterContentLayout(state = state, emit = { mViewModel.emit(this) })
    }

    companion object {
        const val EXTRA_CHARACTER_ID = "character_id"
    }
}
