package io.github.jameslunaa.taskora.system

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.platform.unix.X11
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.NativeLongByReference
import com.sun.jna.ptr.PointerByReference
import io.github.jameslunaa.taskora.domain.model.WindowLevel
import io.github.jameslunaa.taskora.domain.model.WindowLook
import java.awt.Toolkit
import java.awt.Window

/**
 * Window-manager hints AWT has no API for. "Always on top" goes through AWT itself; this covers
 * the rest.
 */
interface WindowHints {
    /** Whether [WindowLevel.BELOW] and opacity work here. */
    val isAvailable: Boolean

    /**
     * Applies [look] (except [WindowLevel.ABOVE], which is AWT's `isAlwaysOnTop`). Returns false
     * if the window manager doesn't manage the window yet (it was only just shown), so the caller
     * can try again shortly.
     */
    fun apply(window: Window, look: WindowLook, skipTaskbar: Boolean = false): Boolean

    object None : WindowHints {
        override val isAvailable = false
        override fun apply(window: Window, look: WindowLook, skipTaskbar: Boolean) = true
    }

    companion object {
        fun forThisDesktop(): WindowHints =
            (if (Platform.isWindows) Win32WindowHints.open() else X11WindowHints.open()) ?: None
    }
}

/**
 * EWMH hints over X11: `_NET_WM_STATE_BELOW`, `_NET_WM_STATE_SKIP_TASKBAR` and
 * `_NET_WM_WINDOW_OPACITY` (KWin honors it). On a Wayland session the JVM's AWT runs under
 * XWayland, so this works there too.
 */
class X11WindowHints private constructor(private val x11: X11, private val display: X11.Display) : WindowHints {
    override val isAvailable = true

    private val root = x11.XDefaultRootWindow(display)

    @Synchronized
    override fun apply(window: Window, look: WindowLook, skipTaskbar: Boolean): Boolean {
        val client = clientWindow(window) ?: return false
        setState(client, look.level == WindowLevel.BELOW, "_NET_WM_STATE_BELOW")
        setState(client, skipTaskbar, "_NET_WM_STATE_SKIP_TASKBAR", "_NET_WM_STATE_SKIP_PAGER")
        setOpacity(client, look.opacity)
        x11.XFlush(display)
        return true
    }

    /**
     * The toplevel the window manager manages: AWT's window id is a child of it, so walk up to
     * the ancestor with `WM_STATE`, which the window manager sets once it manages the window.
     */
    private fun clientWindow(window: Window): X11.Window? {
        val id = runCatching { Native.getWindowID(window) }.getOrDefault(0L)
        if (id == 0L) return null
        var current = X11.Window(id)
        val wmState = atom("WM_STATE")
        while (true) {
            if (hasProperty(current, wmState)) return current
            current = parentOf(current)?.takeIf { it != root } ?: return null
        }
    }

    private fun parentOf(window: X11.Window): X11.Window? {
        val rootReturn = X11.WindowByReference()
        val parent = X11.WindowByReference()
        val children = PointerByReference()
        val count = IntByReference()
        if (x11.XQueryTree(display, window, rootReturn, parent, children, count) == 0) return null
        children.value?.let { x11.XFree(it) }
        return parent.value
    }

    private fun hasProperty(window: X11.Window, property: X11.Atom): Boolean {
        val type = X11.AtomByReference()
        val format = IntByReference()
        val items = NativeLongByReference()
        val after = NativeLongByReference()
        val data = PointerByReference()
        val status = x11.XGetWindowProperty(
            display, window, property, NativeLong(0), NativeLong(0), false, X11.Atom(X11.AnyPropertyType.toLong()),
            type, format, items, after, data,
        )
        data.value?.let { x11.XFree(it) }
        return status == X11.Success && type.value != null && type.value.toLong() != 0L
    }

    /** Asks the window manager to add or remove up to two `_NET_WM_STATE` atoms. */
    private fun setState(window: X11.Window, enabled: Boolean, first: String, second: String? = null) {
        val event = X11.XEvent()
        event.type = X11.ClientMessage
        event.setType(X11.XClientMessageEvent::class.java)
        event.xclient.apply {
            type = X11.ClientMessage
            serial = NativeLong(0)
            send_event = 1
            this.display = this@X11WindowHints.display
            this.window = window
            message_type = atom("_NET_WM_STATE")
            format = 32
            data.setType(Array<NativeLong>::class.java)
            data.l[0] = NativeLong(if (enabled) NET_WM_STATE_ADD else NET_WM_STATE_REMOVE)
            data.l[1] = NativeLong(atom(first).toLong())
            data.l[2] = NativeLong(second?.let { atom(it).toLong() } ?: 0L)
            // Source indication: a normal application.
            data.l[3] = NativeLong(1)
            data.l[4] = NativeLong(0)
        }
        x11.XSendEvent(
            display, root, 0,
            NativeLong(X11.SubstructureRedirectMask.toLong() or X11.SubstructureNotifyMask.toLong()),
            event,
        )
    }

    private fun setOpacity(window: X11.Window, opacity: Float) {
        val property = atom("_NET_WM_WINDOW_OPACITY")
        if (opacity >= 1f) {
            x11.XDeleteProperty(display, window, property)
            return
        }
        // A 32-bit CARDINAL from 0 (clear) to 0xFFFFFFFF (opaque); Xlib passes format-32 data as longs.
        val value = Memory(Native.LONG_SIZE.toLong())
        value.setNativeLong(0, NativeLong((opacity.toDouble() * 0xFFFFFFFFL).toLong()))
        x11.XChangeProperty(display, window, property, X11.XA_CARDINAL, 32, X11.PropModeReplace, value, 1)
    }

    private val atoms = mutableMapOf<String, X11.Atom>()

    private fun atom(name: String): X11.Atom = atoms.getOrPut(name) { x11.XInternAtom(display, name, false) }

    companion object {
        private const val NET_WM_STATE_REMOVE = 0L
        private const val NET_WM_STATE_ADD = 1L

        /** Null unless AWT itself runs on X11 (or XWayland), so window ids are X11 ids. */
        fun open(): X11WindowHints? = runCatching {
            if (Toolkit.getDefaultToolkit().javaClass.name != "sun.awt.X11.XToolkit") return null
            val x11 = X11.INSTANCE
            val display = x11.XOpenDisplay(null) ?: return null
            X11WindowHints(x11, display)
        }.getOrNull()
    }
}
