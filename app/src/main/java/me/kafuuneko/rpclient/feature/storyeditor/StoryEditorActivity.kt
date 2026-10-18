package me.kafuuneko.rpclient.feature.storyeditor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.flowWithLifecycle
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.appearance.PageBackgroundViewModel
import me.kafuuneko.rpclient.feature.appearance.presentation.PageBackgroundUiState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorUiIntent
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorUiState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorViewEvent
import me.kafuuneko.rpclient.feature.storyeditor.ui.StoryEditorLayout
import me.kafuuneko.rpclient.libs.appearance.BackgroundPage
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent
import me.kafuuneko.rpclient.libs.search.SearchNavigation

/** 分卷/章节编辑器宿主，负责页面生命周期与剪贴板系统能力。 */
class StoryEditorActivity : CoreActivityWithEvent() {
    private val mBackgroundViewModel by viewModels<PageBackgroundViewModel>()
    private val mBackgroundFlow by lazy { mBackgroundViewModel.observe(BackgroundPage.Story).flowWithLifecycle(lifecycle) }
    private val mViewModel by viewModels<StoryEditorViewModel>()

    private val mTextImporter = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { mViewModel.emit(StoryEditorUiIntent.ImportTextResult(it)) } }

    private val mStoryImporter = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { mViewModel.emit(StoryEditorUiIntent.ImportStoryResult(it)) } }

    private val mTextExporter = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let { mViewModel.emit(StoryEditorUiIntent.ExportTextResult(it)) } }

    private val mMarkdownExporter = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri -> uri?.let { mViewModel.emit(StoryEditorUiIntent.ExportTextResult(it)) } }

    private val mStoryExporter = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { mViewModel.emit(StoryEditorUiIntent.ExportStoryResult(it)) } }

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()
        val background by mBackgroundFlow.collectAsState(PageBackgroundUiState())
        val document by mViewModel.documentFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is StoryEditorUiState.Finished) finish()
        }

        StoryEditorLayout(
            uiState = uiState,
            background = background,
            document = document,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storyId = intent.getLongExtra(EXTRA_STORY_ID, 0L)
        mViewModel.emit(StoryEditorUiIntent.Init(storyId,
            intent.getLongExtra(SearchNavigation.EXTRA_SOURCE_ID, 0).takeIf { it > 0 },
            intent.getStringExtra(SearchNavigation.EXTRA_QUERY).orEmpty()))
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(StoryEditorUiIntent.Resume)
    }

    override fun onStop() {
        mViewModel.emit(StoryEditorUiIntent.FlushDraft)
        super.onStop()
    }

    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        when (viewEvent) {
            is StoryEditorViewEvent.CopyDraft -> {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText(getString(R.string.story_draft_clip_label), viewEvent.content)
                )
            }
            is StoryEditorViewEvent.CopyGeneratedText -> {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                        getString(R.string.story_generated_text_clip_label),
                        viewEvent.content
                    )
                )
            }
            is StoryEditorViewEvent.CopyPromptText -> {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                        getString(R.string.prompt_inspector_title),
                        viewEvent.content
                    )
                )
            }
            StoryEditorViewEvent.OpenTextImporter -> mTextImporter.launch(
                arrayOf("text/plain", "text/markdown", "text/x-markdown")
            )
            StoryEditorViewEvent.OpenStoryImporter -> mStoryImporter.launch(
                arrayOf("application/json", "text/json")
            )
            is StoryEditorViewEvent.OpenTextExporter -> {
                if (viewEvent.markdown) {
                    mMarkdownExporter.launch(viewEvent.fileName)
                } else {
                    mTextExporter.launch(viewEvent.fileName)
                }
            }
            is StoryEditorViewEvent.OpenStoryExporter -> mStoryExporter.launch(viewEvent.fileName)
            is StoryEditorViewEvent.OpenStory -> {
                startActivity(
                    Intent(this, StoryEditorActivity::class.java).apply {
                        putExtra(EXTRA_STORY_ID, viewEvent.storyId)
                    }
                )
                finish()
            }
            else -> super.onReceivedViewEvent(viewEvent)
        }
    }

    companion object {
        /** 启动故事编辑器 Activity 时传入的故事 ID 键名。 */
        const val EXTRA_STORY_ID = "extra_story_id"
    }
}
