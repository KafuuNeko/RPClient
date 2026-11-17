package me.kafuuneko.rpclient.libs.core

import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

// 为已缓存的页面背景预留短暂的切换时间，较慢的解码继续在加载页完成。
private const val FIRST_DRAW_GRACE_MS = 200L

/**
 * 在短暂过渡期内等待页面应用必要资源，之后允许宿主独立绘制加载页。
 *
 * @param isReady 快速读取界面已提交的就绪标记，不在绘制回调中执行 IO。
 */
internal fun ComponentActivity.deferFirstDrawUntil(isReady: () -> Boolean) {
    val decorView = window.decorView
    // 同时观察绘制与宿主生命周期，加载期间退出也会解除监听。
    val observer = object : ViewTreeObserver.OnPreDrawListener, DefaultLifecycleObserver {
        // 系统主线程负责放行，即使 Compose 尚未提交状态也能完成 Activity 启动。
        val release = Runnable {
            stopObserving()
            decorView.invalidate()
        }

        /**
         * 资源已应用到页面时放行并结束首帧等待。
         * @return 当前绘制是否可以继续。
         */
        override fun onPreDraw(): Boolean {
            if (!isReady()) return false
            stopObserving()
            return true
        }

        /**
         * 页面在首帧之前关闭时清理监听。
         * @param owner 当前销毁的生命周期宿主。
         */
        override fun onDestroy(owner: LifecycleOwner) {
            stopObserving()
        }

        /** 监听绑定当前 ViewTreeObserver，移除时兼容窗口已经脱离的情况。 */
        private fun stopObserving() {
            decorView.removeCallbacks(release)
            val treeObserver = decorView.viewTreeObserver
            if (treeObserver.isAlive) treeObserver.removeOnPreDrawListener(this)
            lifecycle.removeObserver(this)
        }
    }
    decorView.viewTreeObserver.addOnPreDrawListener(observer)
    lifecycle.addObserver(observer)
    decorView.postDelayed(observer.release, FIRST_DRAW_GRACE_MS)
}
