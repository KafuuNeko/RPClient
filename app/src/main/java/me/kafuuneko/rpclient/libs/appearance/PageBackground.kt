package me.kafuuneko.rpclient.libs.appearance

/** 通用外观与三类内容页独立保存背景，标识不依赖枚举顺序。 */
enum class BackgroundPage(val key: String) {
    General("general"),

    Chat("chat"),

    Group("group"),

    Story("story")
}

/** 背景原始参数；实际显示前解析通用继承，图片永不参与模型请求。 */
data class PageBackground(
    val page: BackgroundPage,
    val fileUuid: String? = null,
    val blur: Int = 0,
    val scrim: Float = 0.35f,
    val opacity: Float = 1f,
    val topBarOpacity: Float = 1f,
    val bottomBarOpacity: Float = 1f,
    val bubbleOpacity: Float = 1f,
    val focalX: Float = 0.5f,
    val focalY: Float = 0.5f,
    val topBarBlur: Int = 0,
    val bottomBarBlur: Int = 0,
    val bubbleBlur: Int = 0,
    val contentBlur: Int = 0,
    val followGeneral: Boolean = true,
    val useCharacterBackground: Boolean = false
) {
    /** 在持久化与渲染边界统一限制数值，拒绝非有限浮点数。 */
    fun normalized(): PageBackground = copy(
        blur = blur.coerceIn(minimumValue = 0, maximumValue = 100),
        topBarBlur = topBarBlur.coerceIn(minimumValue = 0, maximumValue = 100),
        bottomBarBlur = bottomBarBlur.coerceIn(minimumValue = 0, maximumValue = 100),
        bubbleBlur = bubbleBlur.coerceIn(minimumValue = 0, maximumValue = 100),
        contentBlur = contentBlur.coerceIn(minimumValue = 0, maximumValue = 100),
        scrim = scrim.finite(fallback = 0.35f).coerceIn(minimumValue = 0f, maximumValue = 0.9f),
        opacity = opacity.finite(fallback = 1f).coerceIn(minimumValue = 0f, maximumValue = 1f),
        topBarOpacity = topBarOpacity.finite(fallback = 1f)
            .coerceIn(minimumValue = 0f, maximumValue = 1f),
        bottomBarOpacity = bottomBarOpacity.finite(fallback = 1f)
            .coerceIn(minimumValue = 0f, maximumValue = 1f),
        bubbleOpacity = bubbleOpacity.finite(fallback = 1f)
            .coerceIn(minimumValue = 0f, maximumValue = 1f),
        focalX = focalX.finite(fallback = 0.5f).coerceIn(minimumValue = 0f, maximumValue = 1f),
        focalY = focalY.finite(fallback = 0.5f).coerceIn(minimumValue = 0f, maximumValue = 1f),
    )
}

/** 各栏位独立渲染；Bubble 沿用旧标识，覆盖聊天消息、介绍卡和群聊成员卡组成的内容区。 */
enum class PageSurfaceKind {
    TopBar,

    BottomBar,

    Bubble,

    Content
}

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
 * 按跟随开关解析页面外观，继承结果仅用于显示，不能写回独立配置。
 * - 关闭跟随时完整使用自身配置，没有独立图片时显示主题底色。
 * - 开启跟随时优先使用独立图片，缺省回退通用图片，其余外观参数来自通用配置。
 *
 * @param general 通用页面的原始配置。
 * @return 当前页面实际显示的背景配置。
 */
fun PageBackground.resolveWith(general: PageBackground): PageBackground {
    if (page == BackgroundPage.General || !followGeneral) return this
    val image = fileUuid ?: general.fileUuid
    return general.copy(
        page = page,
        fileUuid = image,
        bubbleOpacity = general.opacity,
        bubbleBlur = general.contentBlur,
        followGeneral = true,
        useCharacterBackground = useCharacterBackground,
    )
}
