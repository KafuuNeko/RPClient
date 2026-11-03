package me.kafuuneko.rpclient.libs.appearance

import android.graphics.Bitmap

/** 同一背景配置对应的底图与栏位位图，与缓存共享，使用方不可修改或回收。 */
data class RenderedPageBackground(
    val settings: PageBackground,
    val image: Bitmap? = null,
    val surfaceImages: Map<PageSurfaceKind, Bitmap> = emptyMap(),
)
