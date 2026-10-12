package me.kafuuneko.rpclient.libs.appearance

/** 三类内容页独立保存背景，标识不依赖枚举顺序。 */
enum class BackgroundPage(val key: String) {
    Chat("chat"), Group("group"), Story("story")
}

/** 背景展示参数；图片为空时恢复主题底色，图片永不参与模型请求。 */
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
    val focalY: Float = 0.5f
) {
    /** 在持久化与渲染边界统一限制数值，拒绝非有限浮点数。 */
    fun normalized(): PageBackground = copy(
        blur = blur.coerceIn(0, 100),
        scrim = scrim.finite(0.25f).coerceIn(0f, 0.9f),
        opacity = opacity.finite(1f).coerceIn(0f, 1f),
        topBarOpacity = topBarOpacity.finite(1f).coerceIn(0f, 1f),
        bottomBarOpacity = bottomBarOpacity.finite(1f).coerceIn(0f, 1f),
        bubbleOpacity = bubbleOpacity.finite(1f).coerceIn(0f, 1f),
        focalX = focalX.finite(0.5f).coerceIn(0f, 1f),
        focalY = focalY.finite(0.5f).coerceIn(0f, 1f)
    )
}

private fun Float.finite(fallback: Float): Float = if (isFinite()) this else fallback
