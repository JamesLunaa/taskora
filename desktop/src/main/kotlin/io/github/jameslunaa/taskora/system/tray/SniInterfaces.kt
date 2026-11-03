@file:Suppress("FunctionName", "unused")

package io.github.jameslunaa.taskora.system.tray

import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.Tuple
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

// The D-Bus interfaces of a StatusNotifierItem tray icon and its dbusmenu, as KDE Plasma (and
// GNOME's AppIndicator extension, Cinnamon, XFCE...) expect them. Member names are D-Bus names,
// hence the capitals. Specs:
// https://www.freedesktop.org/wiki/Specifications/StatusNotifierItem/
// https://github.com/AyatanaIndicators/libdbusmenu/blob/master/libdbusmenu-glib/dbus-menu.xml

@DBusInterfaceName("org.kde.StatusNotifierWatcher")
interface StatusNotifierWatcher : DBusInterface {
    fun RegisterStatusNotifierItem(service: String)
}

@DBusInterfaceName("org.kde.StatusNotifierItem")
interface StatusNotifierItem : DBusInterface {
    /** A left click on the icon. */
    fun Activate(x: Int, y: Int)

    /** A middle click. */
    fun SecondaryActivate(x: Int, y: Int)

    /** Only called by hosts that don't use the [DbusMenu]. */
    fun ContextMenu(x: Int, y: Int)

    fun Scroll(delta: Int, orientation: String)

    class NewIcon(path: String) : DBusSignal(path)
    class NewToolTip(path: String) : DBusSignal(path)
    class NewStatus(path: String, status: String) : DBusSignal(path, status)
}

@DBusInterfaceName("com.canonical.dbusmenu")
interface DbusMenu : DBusInterface {
    /** Revision and layout. */
    fun GetLayout(parentId: Int, recursionDepth: Int, propertyNames: List<String>): TwoValues<UInt32, MenuNode>
    fun GetGroupProperties(ids: List<Int>, propertyNames: List<String>): List<MenuItemProperties>
    fun GetProperty(id: Int, name: String): Variant<*>
    fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32)
    fun EventGroup(events: List<MenuEvent>): List<Int>
    fun AboutToShow(id: Int): Boolean
    /** Ids that need an update, and ids that weren't found. */
    fun AboutToShowGroup(ids: List<Int>): TwoValues<List<Int>, List<Int>>

    class LayoutUpdated(path: String, revision: UInt32, parent: Int) : DBusSignal(path, revision, parent)
}

/** An icon as width, height and ARGB32 pixels in network byte order: `(iiay)`. */
class Pixmap(
    @field:Position(0) @JvmField val width: Int,
    @field:Position(1) @JvmField val height: Int,
    @field:Position(2) @JvmField val data: ByteArray,
) : Struct()

/** `(sa(iiay)ss)`: icon name, icon pixmaps, title, description. */
class ToolTip(
    @field:Position(0) @JvmField val iconName: String,
    @field:Position(1) @JvmField val icon: List<Pixmap>,
    @field:Position(2) @JvmField val title: String,
    @field:Position(3) @JvmField val description: String,
) : Struct()

/** A menu node, `(ia{sv}av)`; each child is a variant holding another node. */
class MenuNode(
    @field:Position(0) @JvmField val id: Int,
    @field:Position(1) @JvmField val properties: Map<String, Variant<*>>,
    @field:Position(2) @JvmField val children: List<Variant<*>>,
) : Struct() {
    companion object {
        const val SIGNATURE = "(ia{sv}av)"
    }
}

/**
 * Two return values. dbus-java reads their D-Bus types from the type arguments, so tuples must
 * be generic like this one.
 */
class TwoValues<A, B>(
    @field:Position(0) @JvmField val first: A,
    @field:Position(1) @JvmField val second: B,
) : Tuple()

class MenuItemProperties(
    @field:Position(0) @JvmField val id: Int,
    @field:Position(1) @JvmField val properties: Map<String, Variant<*>>,
) : Struct()

class MenuEvent(
    @field:Position(0) @JvmField val id: Int,
    @field:Position(1) @JvmField val eventId: String,
    @field:Position(2) @JvmField val data: Variant<*>,
    @field:Position(3) @JvmField val timestamp: UInt32,
) : Struct()
