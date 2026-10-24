package me.kafuuneko.rpclient.libs.appearance

/** 对话框独立外观；模糊保存无单位强度，不关联页面背景图片或数据库表。 */
data class DialogAppearance(
    val transparency: Int = 0,
    val blur: Int = 0,
    val controlTransparency: Int = 0,
    val dim: Int = 32,
    val behindBlur: Int = 0
) {
    /** 统一约束持久化和预览值，交互底板保留至少一成不透明度。 */
    fun normalized(): DialogAppearance = copy(
        transparency = transparency.coerceIn(0, 100),
        blur = blur.coerceIn(0, 100),
        controlTransparency = controlTransparency.coerceIn(0, 90),
        dim = dim.coerceIn(0, 80),
        behindBlur = behindBlur.coerceIn(0, 100)
    )

    /** 不支持窗口模糊时提高底色和遮罩可读性，不覆盖用户保存的目标参数。 */
    fun resolved(blurAvailable: Boolean): DialogAppearance {
        val settings = normalized()
        if (blurAvailable) return settings
        return settings.copy(
            transparency = if (settings.blur > 0) settings.transparency.coerceAtMost(15) else settings.transparency,
            dim = if (settings.behindBlur > 0) settings.dim.coerceAtLeast(50) else settings.dim,
            blur = 0,
            behindBlur = 0
        )
    }
}
