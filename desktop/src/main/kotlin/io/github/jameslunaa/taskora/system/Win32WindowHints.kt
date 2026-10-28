package io.github.jameslunaa.taskora.system

import com.sun.jna.CallbackReference
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser
import io.github.jameslunaa.taskora.domain.model.WindowLevel
import io.github.jameslunaa.taskora.domain.model.WindowLook
import java.awt.Window
import java.util.concurrent.ConcurrentHashMap

/**
 * The same hints through the Win32 API:
 * - always below: the window's procedure is wrapped so every z-order change puts it at the
 *   bottom (clicking it would otherwise raise it);
 * - opacity: a layered window with a constant alpha;
 * - no taskbar button: a tool window.
 */
class Win32WindowHints private constructor(private val user32: User32) : WindowHints {
    override val isAvailable = true

    /** Wrapped window procedures by window handle, kept here so JNA doesn't collect the callbacks. */
    private val keepers = ConcurrentHashMap<Pointer, KeepAtBottom>()

    @Synchronized
    override fun apply(window: Window, look: WindowLook, skipTaskbar: Boolean): Boolean {
        if (!window.isDisplayable) return false
        val pointer = runCatching { Native.getComponentPointer(window) }.getOrNull() ?: return false
        val hwnd = HWND(pointer)
        setBelow(hwnd, look.level == WindowLevel.BELOW)
        setToolWindow(hwnd, skipTaskbar)
        setOpacity(hwnd, look.opacity)
        return true
    }

    private fun setBelow(hwnd: HWND, below: Boolean) {
        val keeper = keepers[hwnd.pointer]
        if (!below) {
            keeper?.enabled = false
            return
        }
        if (keeper == null) keepers[hwnd.pointer] = KeepAtBottom(hwnd) else keeper.enabled = true
        user32.SetWindowPos(hwnd, HWND_BOTTOM, 0, 0, 0, 0, SWP_NOSIZE or SWP_NOMOVE or SWP_NOACTIVATE)
    }

    /** The shell only reads the style when a window is shown, so a visible window is shown again. */
    private fun setToolWindow(hwnd: HWND, enabled: Boolean) {
        val style = user32.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE)
        if ((style and WS_EX_TOOLWINDOW != 0) == enabled) return
        val visible = user32.IsWindowVisible(hwnd)
        if (visible) user32.ShowWindow(hwnd, WinUser.SW_HIDE)
        user32.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, if (enabled) style or WS_EX_TOOLWINDOW else style and WS_EX_TOOLWINDOW.inv())
        if (visible) user32.ShowWindow(hwnd, WinUser.SW_SHOWNOACTIVATE)
    }

    private fun setOpacity(hwnd: HWND, opacity: Float) {
        val style = user32.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE)
        if (opacity >= 1f) {
            if (style and WS_EX_LAYERED != 0) user32.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, style and WS_EX_LAYERED.inv())
            return
        }
        if (style and WS_EX_LAYERED == 0) user32.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, style or WS_EX_LAYERED)
        user32.SetLayeredWindowAttributes(hwnd, 0, (opacity * 255).toInt().coerceIn(0, 255).toByte(), LWA_ALPHA)
    }

    /**
     * Wraps the window's procedure (AWT's) and, while [enabled], turns every z-order change in
     * `WM_WINDOWPOSCHANGING` into "bottom". Unwraps itself when the window is destroyed.
     */
    private inner class KeepAtBottom(hwnd: HWND) : WinUser.WindowProc {
        @Volatile
        var enabled = true

        private val previous: Pointer = user32.GetWindowLongPtr(hwnd, WinUser.GWL_WNDPROC).toPointer()

        init {
            user32.SetWindowLongPtr(hwnd, WinUser.GWL_WNDPROC, CallbackReference.getFunctionPointer(this))
        }

        override fun callback(hwnd: HWND, uMsg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT {
            try {
                when (uMsg) {
                    WM_WINDOWPOSCHANGING -> if (enabled) {
                        // WINDOWPOS: hwnd, hwndInsertAfter, x, y, cx, cy, flags.
                        val pos = Pointer(lParam.toLong())
                        val flags = pos.getInt(2L * Native.POINTER_SIZE + 16)
                        if (flags and SWP_NOZORDER == 0) pos.setPointer(Native.POINTER_SIZE.toLong(), HWND_BOTTOM.pointer)
                    }
                    WM_NCDESTROY -> {
                        user32.SetWindowLongPtr(hwnd, WinUser.GWL_WNDPROC, previous)
                        keepers.remove(hwnd.pointer)
                    }
                }
            } catch (_: Throwable) {
                // Never let an error here keep the message from AWT.
            }
            return user32.CallWindowProc(previous, hwnd, uMsg, wParam, lParam)
        }
    }

    companion object {
        private val HWND_BOTTOM = HWND(Pointer.createConstant(1))
        private const val SWP_NOSIZE = 0x0001
        private const val SWP_NOMOVE = 0x0002
        private const val SWP_NOZORDER = 0x0004
        private const val SWP_NOACTIVATE = 0x0010
        private const val WS_EX_TOOLWINDOW = 0x00000080
        private const val WS_EX_LAYERED = 0x00080000
        private const val LWA_ALPHA = 0x2
        private const val WM_WINDOWPOSCHANGING = 0x0046
        private const val WM_NCDESTROY = 0x0082

        fun open(): Win32WindowHints? = runCatching { Win32WindowHints(User32.INSTANCE) }.getOrNull()
    }
}
