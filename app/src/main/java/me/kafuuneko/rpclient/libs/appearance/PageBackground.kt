package me.kafuuneko.rpclient.libs.appearance

/** 通用外观与三类内容页独立保存背景，标识不依赖枚举顺序。 */
enum class BackgroundPage(val key: String) {
    General("general"), Chat("chat"), Group("group"), Story("story")
}

/** 背景原始参数；实际显示前解析通用继承，图片永不参与模型请求。 */
data class PageBackground(
    val page: BackgroundPage,
    val fileUuid: String? = null,
    val blur: Int = 25,
    val scrim: Float = 0.25f,
    val opacity: Float = 1f,
    val topBarOpacity: Float = 1f,
    val bottomBarOpacity: Float = 1f,
    val bubbleOpacity: Float = 1f,
    val focalX: Float = 0.5f,
    val focalY: Float = 0.5f,
    val topBarBlur: Int = 0,
    val bottomBarBlur: Int = 0,
    val bubbleBlur: Int = 0,
    val contentBlur: Int = 0
) {
    /** 在持久化与渲染边界统一限制数值，拒绝非有限浮点数。 */
    fun normalized(): PageBackground = copy(
        blur = blur.coerceIn(0, 100),
        topBarBlur = topBarBlur.coerceIn(0, 100),
        bottomBarBlur = bottomBarBlur.coerceIn(0, 100),
        bubbleBlur = bubbleBlur.coerceIn(0, 100),
        contentBlur = contentBlur.coerceIn(0, 100),
        scrim = scrim.finite(0.25f).coerceIn(0f, 0.9f),
        opacity = opacity.finite(1f).coerceIn(0f, 1f),
        topBarOpacity = topBarOpacity.finite(1f).coerceIn(0f, 1f),
        bottomBarOpacity = bottomBarOpacity.finite(1f).coerceIn(0f, 1f),
        bubbleOpacity = bubbleOpacity.finite(1f).coerceIn(0f, 1f),
        focalX = focalX.finite(0.5f).coerceIn(0f, 1f),
        focalY = focalY.finite(0.5f).coerceIn(0f, 1f)
    )
}

/** 各栏位独立渲染；Bubble 沿用旧标识，覆盖聊天消息、介绍卡和群聊成员卡组成的内容区。 */
enum class PageSurfaceKind { TopBar, BottomBar, Bubble, Content }

/** 取得栏位背景不透明度；完全不透明时不应生成模糊派生图。 */
fun PageBackground.surfaceOpacity(kind: PageSurfaceKind): Float = when (kind) {
    PageSurfaceKind.TopBar -> topBarOpacity
    PageSurfaceKind.BottomBar -> bottomBarOpacity
    PageSurfaceKind.Bubble -> bubbleOpacity
    PageSurfaceKind.Content -> opacity
}

/** 取得栏位附加模糊强度，页面不使用的栏位不参与图像工作。 */
fun PageBackground.surfaceBlur(kind: PageSurfaceKind): Int = when (kind) {
    PageSurfaceKind.TopBar -> topBarBlur
    PageSurfaceKind.BottomBar -> bottomBarBlur
    PageSurfaceKind.Bubble -> if (page == BackgroundPage.Chat || page == BackgroundPage.Group) bubbleBlur else 0
    PageSurfaceKind.Content -> if (page == BackgroundPage.Story || page == BackgroundPage.General) contentBlur else 0
}

private fun Float.finite(fallback: Float): Float = if (isFinite()) this else fallback

/**
 * 解析内容页的通用区域设置，继承结果仅用于显示，不能写回独立配置。
 * - UI 的零透明度对应不透明度 1；区域模糊只要非零就视为自定义。
 * - 有独立图片时保留图片、全图模糊、遮罩和焦点，无图片时继承通用背景。
 */
fun PageBackground.resolveWith(general: PageBackground): PageBackground {
    if (page == BackgroundPage.General ||
        topBarOpacity != 1f || bottomBarOpacity != 1f || bubbleOpacity != 1f || opacity != 1f ||
        topBarBlur != 0 || bottomBarBlur != 0 || bubbleBlur != 0 || contentBlur != 0
    ) return this
    // 图片归属与区域外观分别决定，独立图片不会因跟随通用区域设置而被替换。
    val source = if (fileUuid != null) this else general.copy(page = page)
    return source.copy(
        topBarOpacity = general.topBarOpacity, bottomBarOpacity = general.bottomBarOpacity,
        opacity = general.opacity, bubbleOpacity = general.opacity,
        topBarBlur = general.topBarBlur, bottomBarBlur = general.bottomBarBlur,
        contentBlur = general.contentBlur, bubbleBlur = general.contentBlur
    )
}
