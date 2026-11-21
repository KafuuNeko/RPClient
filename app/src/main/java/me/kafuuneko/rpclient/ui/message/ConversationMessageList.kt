package me.kafuuneko.rpclient.ui.message

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.model.MessageHistoryLoadState
import me.kafuuneko.rpclient.ui.widgets.draggableLazyListScrollIndicator

/** 当前窗口顶部进入该范围时预取更早消息。 */
private const val HISTORY_LOAD_THRESHOLD = 4

/**
 * 单聊与群聊共用的消息列表容器，统一滚动跟随、历史分页、搜索定位、软键盘视口补偿与列表边距。
 *
 * - 列表水平边距、消息间距和水平安全区统一采用单聊标准。
 * - 支持通过 [startHeader] 在历史顶部渲染介绍卡，以及通过 [emptyContent] 在空会话时渲染占位页。
 *
 * @param messages 当前分页窗口内展示的消息列表
 * @param itemId 提取单条消息稳定唯一标识的函数
 * @param itemContentType 提取单条消息复用类型的函数
 * @param itemContentKey 提取尾部流式内容变更监听键的函数
 * @param canLoadOlderMessages 当前窗口之前是否还有更早历史
 * @param historyLoadState 当前双向历史分页加载状态
 * @param searchAnchorMessageId 搜索跳转定位的目标消息 ID；非空时禁止流式更新抢回底部
 * @param canLoadNewerMessages 搜索定位窗口之后是否还有更新消息
 * @param expandedThinkBlockIds 已展开的推理块 ID 集合，用于折叠变动时保持底端跟随
 * @param onLoadOlderMessages 触顶请求更早历史消息的回调
 * @param onLoadNewerMessages 搜索窗口底部请求更新消息的回调
 * @param onReturnToLatest 从搜索定位一键返回最新消息的回调
 * @param onReachLatestMessage 滚动到真实末尾时回传尾消息以退出搜索定位的回调
 * @param modifier 列表区域修饰符
 * @param startHeader 没有更早历史时展示在消息顶部的可选头部内容
 * @param emptyContent 消息为空时展示的可选占位内容；为空时仍渲染包含 [startHeader] 的列表
 * @param messageContent 单条消息气泡的渲染槽位
 */
@Composable
internal fun <T> ConversationMessageList(
    messages: List<T>,
    itemId: (T) -> String,
    itemContentType: (T) -> Any?,
    itemContentKey: (T) -> Any?,
    canLoadOlderMessages: Boolean,
    historyLoadState: MessageHistoryLoadState,
    searchAnchorMessageId: String?,
    canLoadNewerMessages: Boolean,
    expandedThinkBlockIds: Set<String>,
    onLoadOlderMessages: () -> Unit,
    onLoadNewerMessages: () -> Unit,
    onReturnToLatest: () -> Unit,
    onReachLatestMessage: (T) -> Unit,
    modifier: Modifier = Modifier,
    startHeader: (@Composable () -> Unit)? = null,
    emptyContent: (@Composable (Modifier) -> Unit)? = null,
    messageContent: @Composable (index: Int, message: T) -> Unit,
) {
    val listState = rememberLazyListState()
    val flags = rememberConversationScrollFlags()
    val showOlderLoader = canLoadOlderMessages || historyLoadState.isLoadingOlder
    val headerCount = if (showOlderLoader || startHeader != null) 1 else 0

    // 先注册滚动与分页副作用，再按需展示空态，确保首次发送消息后仍能正确触底。
    ObserveScrollFollowAndPagination(
        listState = listState,
        flags = flags,
        messages = messages,
        itemId = itemId,
        canLoadOlderMessages = canLoadOlderMessages,
        historyLoadState = historyLoadState,
        searchAnchorMessageId = searchAnchorMessageId,
        canLoadNewerMessages = canLoadNewerMessages,
        hasStartHeader = startHeader != null,
        onLoadOlderMessages = onLoadOlderMessages,
        onReachLatestMessage = onReachLatestMessage,
    )
    ObserveAutoScrollPosition(
        listState = listState,
        flags = flags,
        messages = messages,
        itemId = itemId,
        itemContentKey = itemContentKey,
        headerCount = headerCount,
        searchAnchorMessageId = searchAnchorMessageId,
        expandedThinkBlockIds = expandedThinkBlockIds,
    )

    if (messages.isEmpty() && emptyContent != null) {
        emptyContent(modifier)
        return
    }

    val coroutineScope = rememberCoroutineScope()
    val activeSearchAnchor by rememberUpdatedState(searchAnchorMessageId)
    val latestMessagesSize by rememberUpdatedState(messages.size)
    val latestHeaderCount by rememberUpdatedState(headerCount)
    var previousListHeight by remember { mutableIntStateOf(0) }

    // 软键盘弹出或收起改变视口高度时，将高度差转化为滚动偏移以保持当前阅读位置稳定。
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
            )
            .onSizeChanged(
                onSizeChanged = { size ->
                    val newHeight = size.height
                    val shouldCompensateViewportHeight = activeSearchAnchor == null
                        && previousListHeight > 0
                        && newHeight > 0
                        && newHeight != previousListHeight
                    if (shouldCompensateViewportHeight) {
                        val delta = (previousListHeight - newHeight).toFloat()
                        coroutineScope.launch {
                            // 尺寸回调仍处于测量阶段，下一帧再滚动以免重入布局。
                            withFrameNanos { }
                            if (flags.shouldFollowBottom && delta > 0) {
                                listState.scrollToItem(latestMessagesSize + latestHeaderCount)
                            } else {
                                listState.dispatchRawDelta(delta)
                            }
                        }
                    }
                    previousListHeight = newHeight
                },
            )
            .draggableLazyListScrollIndicator(
                state = listState,
                onDragStateChanged = { dragging ->
                    flags.isScrollIndicatorDragged = dragging
                    flags.shouldFollowBottom = if (dragging) {
                        false
                    } else {
                        activeSearchAnchor == null && !listState.canScrollForward
                    }
                },
            ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showOlderLoader) {
            item(key = "older-messages-loader") {
                OlderMessagesLoadIndicator(loading = historyLoadState.isLoadingOlder)
            }
        } else if (startHeader != null) {
            item(key = "conversation-start") {
                startHeader()
            }
        }
        itemsIndexed(
            items = messages,
            key = { _, message -> itemId(message) },
            contentType = { _, message -> itemContentType(message) },
        ) { index, message ->
            messageContent(index, message)
        }
        item(key = "conversation-end") {
            if (activeSearchAnchor != null && canLoadNewerMessages) {
                SearchNavigationFooter(
                    isLoadingNewer = historyLoadState.isLoadingNewer,
                    onLoadNewerMessages = onLoadNewerMessages,
                    onReturnToLatest = onReturnToLatest,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** 集中托管列表滚动跟随与首次定位标记，保证往返设置页时阅读位置不被重置。 */
@Stable
private class ConversationScrollFlags(
    shouldFollowBottomState: MutableState<Boolean>,
    isFirstLoadState: MutableState<Boolean>,
    lastTailMessageIdState: MutableState<String?>,
    lastSearchAnchorState: MutableState<String?>,
) {
    var shouldFollowBottom by shouldFollowBottomState
    var isFirstLoad by isFirstLoadState
    var isScrollIndicatorDragged by mutableStateOf(false)
    var lastTailMessageId by lastTailMessageIdState
    var lastSearchAnchor by lastSearchAnchorState
}

/** 创建可跨页面子视图切换保存的滚动标记。 */
@Composable
private fun rememberConversationScrollFlags(): ConversationScrollFlags {
    // 跟随标记与列表位置一起恢复，避免从设置页返回时重新触发首次定位。
    val shouldFollowBottom = rememberSaveable { mutableStateOf(true) }
    val isFirstLoad = rememberSaveable { mutableStateOf(true) }
    val lastTailMessageId = rememberSaveable { mutableStateOf<String?>(value = null) }
    val lastSearchAnchor = rememberSaveable { mutableStateOf<String?>(value = null) }
    return remember {
        ConversationScrollFlags(
            shouldFollowBottomState = shouldFollowBottom,
            isFirstLoadState = isFirstLoad,
            lastTailMessageIdState = lastTailMessageId,
            lastSearchAnchorState = lastSearchAnchor,
        )
    }
}

/** 监听用户滚动、滚动条拖拽、历史顶部分页与搜索窗口到达最新消息状态。 */
@Composable
private fun <T> ObserveScrollFollowAndPagination(
    listState: LazyListState,
    flags: ConversationScrollFlags,
    messages: List<T>,
    itemId: (T) -> String,
    canLoadOlderMessages: Boolean,
    historyLoadState: MessageHistoryLoadState,
    searchAnchorMessageId: String?,
    canLoadNewerMessages: Boolean,
    hasStartHeader: Boolean,
    onLoadOlderMessages: () -> Unit,
    onReachLatestMessage: (T) -> Unit,
) {
    val isListDragged by listState.interactionSource.collectIsDraggedAsState()
    val activeSearchAnchor by rememberUpdatedState(searchAnchorMessageId)
    val latestMessages by rememberUpdatedState(messages)
    val latestItemId by rememberUpdatedState(itemId)
    val latestCanLoadOlder by rememberUpdatedState(canLoadOlderMessages)
    val latestIsLoadingOlder by rememberUpdatedState(historyLoadState.isLoadingOlder)
    val latestCanLoadNewer by rememberUpdatedState(canLoadNewerMessages)
    val latestIsLoadingNewer by rememberUpdatedState(historyLoadState.isLoadingNewer)
    val latestHasStartHeader by rememberUpdatedState(hasStartHeader)
    val latestOnLoadOlder by rememberUpdatedState(onLoadOlderMessages)
    val latestOnReachLatest by rememberUpdatedState(onReachLatestMessage)

    // 恢复列表尚未完成测量时 canScrollForward 暂为 false，不能据此重新开启底部跟随。
    LaunchedEffect(listState) {
        snapshotFlow {
            if (listState.layoutInfo.visibleItemsInfo.isEmpty()) {
                null
            } else {
                listState.canScrollForward to flags.isScrollIndicatorDragged
            }
        }.collect { position ->
            val (canScrollForward, indicatorDragged) = position ?: return@collect
            if (indicatorDragged) {
                flags.shouldFollowBottom = false
            } else if (activeSearchAnchor == null && !canScrollForward) {
                flags.shouldFollowBottom = true
            }
        }
    }

    // 手势拖动列表时实时同步是否贴底，用户上滑阅读历史后立即停止自动跟底。
    LaunchedEffect(isListDragged) {
        if (!isListDragged) return@LaunchedEffect
        snapshotFlow { listState.canScrollForward }
            .collect { canScrollForward ->
                flags.shouldFollowBottom = activeSearchAnchor == null && !canScrollForward
            }
    }

    // 页头项计入布局总数，防止分页切换后旧测量结果提前退出搜索定位模式。
    LaunchedEffect(listState) {
        snapshotFlow {
            val headerCount =
                if (latestCanLoadOlder || latestIsLoadingOlder || latestHasStartHeader) 1 else 0
            val layout = listState.layoutInfo
            latestMessages.lastOrNull()?.takeIf {
                !flags.isFirstLoad
                    && activeSearchAnchor != null
                    && !latestCanLoadNewer
                    && !latestIsLoadingNewer
                    && layout.totalItemsCount == latestMessages.size + headerCount + 1
                    && layout.visibleItemsInfo.isNotEmpty()
                    && !listState.canScrollForward
            }
        }.filterNotNull()
            .distinctUntilChangedBy { message -> latestItemId(message) }
            .collect { message -> latestOnReachLatest(message) }
    }

    // 首次定位完成后，用户滚动接近当前窗口顶部时预取更早历史消息。
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to flags.isFirstLoad }
            .distinctUntilChanged()
            .collect { (firstVisibleItemIndex, firstLoad) ->
                val shouldLoadOlder = !firstLoad
                    && firstVisibleItemIndex <= HISTORY_LOAD_THRESHOLD
                    && latestCanLoadOlder
                    && !latestIsLoadingOlder
                if (shouldLoadOlder) {
                    latestOnLoadOlder()
                }
            }
    }
}

/** 响应搜索锚点定位、尾消息变化、流式内容增长与思考块展开变化，按需保持列表在目标位置。 */
@Composable
private fun <T> ObserveAutoScrollPosition(
    listState: LazyListState,
    flags: ConversationScrollFlags,
    messages: List<T>,
    itemId: (T) -> String,
    itemContentKey: (T) -> Any?,
    headerCount: Int,
    searchAnchorMessageId: String?,
    expandedThinkBlockIds: Set<String>,
) {
    val tailMessageId = messages.lastOrNull()?.let(itemId)
    val tailContentKey = messages.lastOrNull()?.let(itemContentKey)

    // 仅在首次加载、锚点进入或尾部消息身份变化时跳转，头部追加历史不打断当前阅读位置。
    LaunchedEffect(tailMessageId, searchAnchorMessageId) {
        if (searchAnchorMessageId != null) {
            if (flags.isFirstLoad) {
                val targetIndex = messages.indexOfFirst { message ->
                    itemId(message) == searchAnchorMessageId
                }
                if (targetIndex >= 0) {
                    listState.scrollToItem(targetIndex + headerCount)
                }
                flags.isFirstLoad = false
            }
            flags.shouldFollowBottom = false
        } else if (tailMessageId == null) {
            flags.isFirstLoad = true
        } else if (flags.isFirstLoad || tailMessageId != flags.lastTailMessageId || flags.lastSearchAnchor != null) {
            flags.shouldFollowBottom = true
            listState.scrollToItem(messages.size + headerCount)
            flags.isFirstLoad = false
        }
        flags.lastTailMessageId = tailMessageId
        flags.lastSearchAnchor = searchAnchorMessageId
    }

    // 内容流式生成或思考块折叠变动时，若处于跟随状态则持续贴底。
    LaunchedEffect(
        key1 = tailContentKey,
        key2 = expandedThinkBlockIds,
    ) {
        if (messages.isEmpty()) return@LaunchedEffect
        val shouldFollowLatest =
            searchAnchorMessageId == null && (flags.isFirstLoad || flags.shouldFollowBottom)
        if (!shouldFollowLatest) return@LaunchedEffect
        listState.scrollToItem(messages.size + headerCount)
        flags.isFirstLoad = false
    }
}

/** 在消息窗口顶部保留稳定高度，并在读取历史时展示轻量进度。 */
@Composable
private fun OlderMessagesLoadIndicator(loading: Boolean) {
    // 空闲时保留加载项高度，防止预取状态切换推动正在阅读的历史消息。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

/** 在搜索定位窗口底端提供加载更新消息与一键回到最新的导航操作。 */
@Composable
private fun SearchNavigationFooter(
    isLoadingNewer: Boolean,
    onLoadNewerMessages: () -> Unit,
    onReturnToLatest: () -> Unit,
) {
    // 分页加载时只禁用重复加载，保留直接返回最新窗口的入口。
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextButton(
            onClick = onLoadNewerMessages,
            enabled = !isLoadingNewer,
        ) {
            Text(stringResource(R.string.content_search_newer))
        }
        TextButton(onClick = onReturnToLatest) {
            Text(stringResource(R.string.content_search_latest))
        }
    }
}
