package me.kafuuneko.rpclient.feature.character

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.character.presentation.CharacterUiIntent
import me.kafuuneko.rpclient.feature.character.presentation.CharacterUiState
import me.kafuuneko.rpclient.feature.character.presentation.CharacterViewEvent
import me.kafuuneko.rpclient.feature.character.ui.CharacterLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent

class CharacterActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<CharacterViewModel>()
    private val mAvatarPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { mViewModel.emit(CharacterUiIntent.AvatarSelected(it)) }
    }

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is CharacterUiState.Finished) finish()
        }

        CharacterLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(CharacterUiIntent.Init)
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(CharacterUiIntent.Resume)
    }

    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        super.onReceivedViewEvent(viewEvent)
        when (viewEvent) {
            CharacterViewEvent.OpenAvatarPicker -> mAvatarPickerLauncher.launch("image/*")
        }
    }
}
