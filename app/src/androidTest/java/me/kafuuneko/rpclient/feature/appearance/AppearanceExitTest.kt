package me.kafuuneko.rpclient.feature.appearance

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.appearance.presentation.AppearanceUiIntent
import me.kafuuneko.rpclient.feature.appearance.presentation.AppearanceUiState
import me.kafuuneko.rpclient.feature.appearance.presentation.BackgroundAdjustment
import me.kafuuneko.rpclient.feature.appearance.presentation.BackgroundEditorDialogState
import me.kafuuneko.rpclient.feature.appearance.presentation.BackgroundEditorLoadState
import me.kafuuneko.rpclient.feature.appearance.ui.AppearanceLayout
import me.kafuuneko.rpclient.feature.imagecrop.model.BackgroundCropResult
import me.kafuuneko.rpclient.libs.appearance.BackgroundImageRuntime
import me.kafuuneko.rpclient.libs.appearance.BackgroundPage
import me.kafuuneko.rpclient.libs.appearance.PageBackground
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.PageBackgroundRepository
import me.kafuuneko.rpclient.model.ImageCropSelection
import me.kafuuneko.rpclient.ui.theme.AppTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import java.io.File
import java.util.UUID

/** 使用隔离 Room 和真实 MVI、弹窗交互验证返回确认，避免修改用户背景。 */
class AppearanceExitTest {
    @get:Rule
    val compose = createComposeRule()
    private lateinit var mDatabase: AppDatabase
    private lateinit var mDirectory: File
    private lateinit var mRepository: PageBackgroundRepository
    private lateinit var mOriginalRepository: PageBackgroundRepository
    private lateinit var mOriginalRuntime: BackgroundImageRuntime
    private lateinit var mViewModel: AppearanceViewModel
    private lateinit var mContext: Context
    private val mStore = ViewModelStore()

    /** 替换本测试的数据依赖，所有意图仍通过实际 ViewModel 分发。 */
    @Before
    fun setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().targetContext
        mDirectory =
            File(mContext.cacheDir, "appearance-exit-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(mContext) {
            override fun getCacheDir(): File = File(mDirectory, "cache").apply { mkdirs() }
            override fun getDir(name: String, mode: Int): File =
                File(mDirectory, name).apply { mkdirs() }
        }
        mDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val files = FileRepository(context, mDatabase)
        mRepository = PageBackgroundRepository(mDatabase, files)
        runBlocking {
            mRepository.save(
                value = PageBackground(page = BackgroundPage.Chat, followGeneral = false),
                prepared = null,
            )
        }
        val runtime = BackgroundImageRuntime(context, files)
        // 只覆盖背景依赖并在结束时恢复，主题偏好只读。
        mOriginalRepository = GlobalContext.get().get()
        mOriginalRuntime = GlobalContext.get().get()
        loadKoinModules(
            module = module {
                single { mRepository }
                single { runtime }
            },
        )
        compose.runOnUiThread {
            mViewModel = AppearanceViewModel()
            mStore.put("appearance", mViewModel)
        }
        compose.setContent {
            val state by mViewModel.uiStateFlow.collectAsState()
            AppTheme(dynamicColor = false) { AppearanceLayout(state, mViewModel::emit) }
        }
        openEditor()
    }

    /** 先清理 ViewModel 协程，再恢复注入与隔离文件。 */
    @After
    fun tearDown() {
        compose.runOnUiThread { mStore.clear() }
        loadKoinModules(
            module = module {
                single { mOriginalRepository }
                single { mOriginalRuntime }
            },
        )
        mDatabase.close()
        mDirectory.deleteRecursively()
    }

    /** 未修改或恢复原值时直接返回，关闭确认后仍保留编辑中的值。 */
    @Test
    fun backComparesCurrentDraftAndCancelKeepsEdits() {
        val first = compose.onNodeWithText("Message1").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("Message2").fetchSemanticsNode().boundsInRoot
        assertTrue(second.left > first.right)
        compose.onNodeWithText(mContext.getString(R.string.appearance_preview_input))
            .assertIsDisplayed()
        send(AppearanceUiIntent.Back)
        compose.waitUntil(timeoutMillis = 5_000) { state().editor == null }
        openEditor()
        changeBlur(value = 45f)
        send(AppearanceUiIntent.Back)
        awaitDialog()
        compose.onNodeWithText(mContext.getString(R.string.cancel)).performClick()
        compose.waitUntil(timeoutMillis = 5_000) { state().editor?.dialogState == BackgroundEditorDialogState.None }
        assertEquals(45, state().editor?.settings?.bubbleBlur)
        // 回到原始值后不应留下粘性的已修改标志。
        changeBlur(value = 0f)
        send(AppearanceUiIntent.Back)
        compose.waitUntil(timeoutMillis = 5_000) { state().editor == null }
    }

    /** 保存经实际仓库提交，放弃不会覆盖已提交值，下一次进入重新建立基准。 */
    @Test
    fun dialogSavesOrDiscardsWithoutLosingCommittedSettings() {
        changeBlur(value = 45f)
        send(AppearanceUiIntent.Back)
        awaitDialog()
        compose.onNodeWithText(mContext.getString(R.string.save)).performClick()
        compose.waitUntil(timeoutMillis = 5_000) { state().editor == null }
        assertEquals(
            45,
            runBlocking { mRepository.observe(BackgroundPage.Chat).first().bubbleBlur })
        openEditor()
        assertEquals(45, state().editor?.settings?.bubbleBlur)
        // 放弃后只丢弃本次修改，重新进入仍显示上次保存值。
        changeBlur(value = 80f)
        send(AppearanceUiIntent.Back)
        awaitDialog()
        compose.onNodeWithText(mContext.getString(R.string.discard_changes)).performClick()
        compose.waitUntil(timeoutMillis = 5_000) { state().editor == null }
        openEditor()
        assertEquals(45, state().editor?.settings?.bubbleBlur)
        send(AppearanceUiIntent.Back)
        compose.waitUntil(timeoutMillis = 5_000) { state().editor == null }
    }

    /** 选图属于未保存修改，恢复原本的无图状态后不再要求保存。 */
    @Test
    fun pickedImagePromptsAndResetToOriginalCanLeave() {
        val source = File(mDirectory, "source.png")
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        send(
            AppearanceUiIntent.ImageCropped(
                BackgroundCropResult(
                    sourceUri = Uri.fromFile(source),
                    selection = ImageCropSelection(0.5f, 0.5f, 1f, 1f),
                )
            )
        )
        compose.waitUntil(timeoutMillis = 5_000) {
            state().editor?.settings?.fileUuid != null
                && state().editor?.loadState == BackgroundEditorLoadState.None
        }
        // 图像处理完成后通过同一返回确认保护新图片，不仅检测滑块值。
        send(AppearanceUiIntent.Back)
        awaitDialog()
        compose.onNodeWithText(mContext.getString(R.string.cancel)).performClick()
        compose.waitUntil(timeoutMillis = 5_000) { state().editor?.dialogState == BackgroundEditorDialogState.None }
        send(AppearanceUiIntent.ResetBackground)
        compose.waitUntil(timeoutMillis = 5_000) { state().editor?.settings?.fileUuid == null }
        send(AppearanceUiIntent.Back)
        compose.waitUntil(timeoutMillis = 5_000) { state().editor == null }
    }

    /** 保存异常恢复空闲状态并保留草稿，用户仍能重新选择保留或放弃。 */
    @Test
    fun failedSaveKeepsDraftAndAllowsAnotherExitChoice() {
        changeBlur(value = 65f)
        send(AppearanceUiIntent.Back)
        awaitDialog()
        mDatabase.close()
        compose.onNodeWithText(mContext.getString(R.string.save)).performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            state().editor?.error != null
                && state().editor?.loadState == BackgroundEditorLoadState.None
        }
        assertEquals(65, state().editor?.settings?.bubbleBlur)
        send(AppearanceUiIntent.Back)
        awaitDialog()
        compose.onNodeWithText(mContext.getString(R.string.discard_changes)).performClick()
        compose.waitUntil(timeoutMillis = 5_000) { state().editor == null }
    }

    private fun state() = mViewModel.uiStateFlow.value as AppearanceUiState.Normal

    /** 等待加载结束后再允许测试操作草稿。 */
    private fun openEditor() {
        send(AppearanceUiIntent.EditBackground(BackgroundPage.Chat))
        compose.waitUntil(timeoutMillis = 5_000) {
            state().editor?.loadState == BackgroundEditorLoadState.None
        }
    }

    private fun send(intent: AppearanceUiIntent) = compose.runOnIdle { mViewModel.emit(intent) }

    /** 使用真实意图修改参数，保证检测覆盖 ViewModel 草稿而非构造的假状态。 */
    private fun changeBlur(value: Float) {
        send(AppearanceUiIntent.ChangeAdjustment(BackgroundAdjustment.BubbleBlur, value))
        compose.waitUntil(timeoutMillis = 5_000) { state().editor?.settings?.bubbleBlur == value.toInt() }
    }

    private fun awaitDialog() {
        compose.waitUntil(timeoutMillis = 5_000) {
            state().editor?.dialogState == BackgroundEditorDialogState.UnsavedChanges
        }
    }
}
