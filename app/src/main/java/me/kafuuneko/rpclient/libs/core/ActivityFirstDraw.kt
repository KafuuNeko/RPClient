package me.kafuuneko.rpclient.libs.core

import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * 等待页面已应用必要资源后再绘制首帧，完成或销毁时移除监听。
 *
 * @param isReady 快速读取界面已提交的就绪标记，不在绘制回调中执行 IO。
 */
internal fun ComponentActivity.deferFirstDrawUntil(isReady: () -> Boolean) {
    val decorView = window.decorView
    // 同时观察绘制与宿主生命周期，加载期间退出也会解除监听。
    val observer = object : ViewTreeObserver.OnPreDrawListener, DefaultLifecycleObserver {
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
            val treeObserver = decorView.viewTreeObserver
            if (treeObserver.isAlive) treeObserver.removeOnPreDrawListener(this)
            lifecycle.removeObserver(this)
        }
    }
    decorView.viewTreeObserver.addOnPreDrawListener(observer)
    lifecycle.addObserver(observer)
}
