package io.github.jameslunaa.taskora.system.tray

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.exceptions.DBusExecutionException
import org.freedesktop.dbus.interfaces.DBus
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import java.awt.Image
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/** One entry of the tray menu; [key]s come back through [SniTray.events]. */
sealed interface TrayItem {
    data class Action(val key: String, val label: String) : TrayItem
    data class Toggle(val key: String, val label: String, val checked: Boolean) : TrayItem
    data object Separator : TrayItem
}

/**
 * A tray icon the desktop draws itself: a StatusNotifierItem with a dbusmenu, over D-Bus. KDE
 * Plasma shows it natively (crisp at any scale, menu in the Plasma theme); GNOME needs the
 * AppIndicator extension, as for any tray icon. AWT's tray speaks the old XEmbed protocol instead,
 * which Plasma only shows through a proxy. [start] returns null when no tray host is running.
 */
class SniTray private constructor(
    private val connection: DBusConnection,
    private val busName: String,
    private val title: String,
    private val icon: List<Pixmap>,
) : AutoCloseable {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Keys of clicked menu items, or [ACTIVATE] for a click on the icon itself. */
    val events: SharedFlow<String> = _events.asSharedFlow()

    @Volatile
    private var items: List<TrayItem> = emptyList()
    private val revision = AtomicLong(1)

    /** Replaces the menu; the host fetches it again. */
    fun setMenu(items: List<TrayItem>) {
        if (items == this.items) return
        this.items = items
        val next = revision.incrementAndGet()
        runCatching { connection.sendMessage(DbusMenu.LayoutUpdated(MENU_PATH, UInt32(next), 0)) }
    }

    override fun close() {
        runCatching {
            connection.unExportObject(ITEM_PATH)
            connection.unExportObject(MENU_PATH)
            connection.releaseBusName(busName)
        }
        runCatching { connection.close() }
    }

    private fun register() {
        connection.getRemoteObject(WATCHER, WATCHER_PATH, StatusNotifierWatcher::class.java)
            .RegisterStatusNotifierItem(busName)
    }

    private val item = object : StatusNotifierItem, Properties {
        override fun getObjectPath() = ITEM_PATH

        override fun Activate(x: Int, y: Int) {
            _events.tryEmit(ACTIVATE)
        }

        override fun SecondaryActivate(x: Int, y: Int) {
            _events.tryEmit(ACTIVATE)
        }

        override fun ContextMenu(x: Int, y: Int) = Unit
        override fun Scroll(delta: Int, orientation: String) = Unit

        override fun GetAll(interfaceName: String): Map<String, Variant<*>> =
            if (interfaceName == ITEM_INTERFACE) itemProperties() else emptyMap()

        @Suppress("UNCHECKED_CAST")
        override fun <A : Any?> Get(interfaceName: String, propertyName: String): A =
            (GetAll(interfaceName)[propertyName] ?: throw DBusExecutionException("No property $propertyName")) as A

        override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) =
            throw DBusExecutionException("$propertyName is read-only")
    }

    private fun itemProperties(): Map<String, Variant<*>> = mapOf(
        "Category" to Variant("ApplicationStatus"),
        "Id" to Variant("taskora"),
        "Title" to Variant(title),
        "Status" to Variant("Active"),
        "WindowId" to Variant(0),
        "IconName" to Variant(""),
        "IconThemePath" to Variant(""),
        "IconPixmap" to Variant(icon, PIXMAPS),
        "OverlayIconName" to Variant(""),
        "OverlayIconPixmap" to Variant(emptyList<Pixmap>(), PIXMAPS),
        "AttentionIconName" to Variant(""),
        "AttentionIconPixmap" to Variant(emptyList<Pixmap>(), PIXMAPS),
        "AttentionMovieName" to Variant(""),
        "ToolTip" to Variant(ToolTip("", emptyList(), title, ""), "(sa(iiay)ss)"),
        "ItemIsMenu" to Variant(false),
        "Menu" to Variant(DBusPath(MENU_PATH)),
    )

    private val menu = object : DbusMenu, Properties {
        override fun getObjectPath() = MENU_PATH

        override fun GetLayout(parentId: Int, recursionDepth: Int, propertyNames: List<String>): TwoValues<UInt32, MenuNode> {
            val current = items
            val node = if (parentId == ROOT_ID) {
                menuLayout(current)
            } else {
                current.getOrNull(parentId - 1)?.let { MenuNode(parentId, menuItemProperties(it), emptyList()) }
                    ?: throw DBusExecutionException("No menu item $parentId")
            }
            return TwoValues(UInt32(revision.get()), node)
        }

        override fun GetGroupProperties(ids: List<Int>, propertyNames: List<String>): List<MenuItemProperties> {
            val current = items
            return ids.mapNotNull { id ->
                when (id) {
                    ROOT_ID -> MenuItemProperties(id, ROOT_PROPERTIES)
                    else -> current.getOrNull(id - 1)?.let { MenuItemProperties(id, menuItemProperties(it)) }
                }
            }
        }

        override fun GetProperty(id: Int, name: String): Variant<*> {
            val properties = if (id == ROOT_ID) ROOT_PROPERTIES else items.getOrNull(id - 1)?.let(::menuItemProperties)
            return properties?.get(name) ?: throw DBusExecutionException("No property $name on $id")
        }

        override fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32) {
            if (eventId != "clicked") return
            when (val clicked = items.getOrNull(id - 1)) {
                is TrayItem.Action -> _events.tryEmit(clicked.key)
                is TrayItem.Toggle -> _events.tryEmit(clicked.key)
                else -> Unit
            }
        }

        override fun EventGroup(events: List<MenuEvent>): List<Int> {
            events.forEach { Event(it.id, it.eventId, it.data, it.timestamp) }
            return emptyList()
        }

        override fun AboutToShow(id: Int) = false
        override fun AboutToShowGroup(ids: List<Int>) = TwoValues<List<Int>, List<Int>>(emptyList(), emptyList())

        override fun GetAll(interfaceName: String): Map<String, Variant<*>> =
            if (interfaceName == MENU_INTERFACE) MENU_PROPERTIES else emptyMap()

        @Suppress("UNCHECKED_CAST")
        override fun <A : Any?> Get(interfaceName: String, propertyName: String): A =
            (GetAll(interfaceName)[propertyName] ?: throw DBusExecutionException("No property $propertyName")) as A

        override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) =
            throw DBusExecutionException("$propertyName is read-only")
    }

    companion object {
        /** [events] value for a click on the icon. */
        const val ACTIVATE = "activate"

        private const val WATCHER = "org.kde.StatusNotifierWatcher"
        private const val WATCHER_PATH = "/StatusNotifierWatcher"
        private const val ITEM_INTERFACE = "org.kde.StatusNotifierItem"
        private const val MENU_INTERFACE = "com.canonical.dbusmenu"
        private const val ITEM_PATH = "/StatusNotifierItem"
        private const val MENU_PATH = "/MenuBar"
        private const val PIXMAPS = "a(iiay)"
        private const val ROOT_ID = 0
        private val ICON_SIZES = listOf(16, 22, 24, 32, 48, 64, 128)

        private val ROOT_PROPERTIES: Map<String, Variant<*>> = mapOf("children-display" to Variant("submenu"))

        private val MENU_PROPERTIES: Map<String, Variant<*>> = mapOf(
            "Version" to Variant(UInt32(3)),
            "TextDirection" to Variant("ltr"),
            "Status" to Variant("normal"),
            "IconThemePath" to Variant(emptyList<String>(), "as"),
        )

        /** The whole (flat) menu: item ids are positions starting at 1, the root is 0. */
        internal fun menuLayout(items: List<TrayItem>) = MenuNode(
            ROOT_ID,
            ROOT_PROPERTIES,
            items.mapIndexed { index, item ->
                Variant(MenuNode(index + 1, menuItemProperties(item), emptyList()), MenuNode.SIGNATURE)
            },
        )

        internal fun menuItemProperties(item: TrayItem): Map<String, Variant<*>> = when (item) {
            is TrayItem.Action -> mapOf("label" to Variant(item.label), "enabled" to Variant(true), "visible" to Variant(true))
            is TrayItem.Toggle -> mapOf(
                "label" to Variant(item.label),
                "enabled" to Variant(true),
                "visible" to Variant(true),
                "toggle-type" to Variant("checkmark"),
                "toggle-state" to Variant(if (item.checked) 1 else 0),
            )
            TrayItem.Separator -> mapOf("type" to Variant("separator"), "visible" to Variant(true))
        }

        /** ARGB32 in network byte order (A, R, G, B per pixel), as the spec asks. */
        internal fun pixmap(image: BufferedImage, size: Int): Pixmap {
            val scaled = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            scaled.createGraphics().apply {
                drawImage(image.getScaledInstance(size, size, Image.SCALE_SMOOTH), 0, 0, null)
                dispose()
            }
            val bytes = ByteBuffer.allocate(size * size * 4)
            for (y in 0 until size) for (x in 0 until size) bytes.putInt(scaled.getRGB(x, y))
            return Pixmap(size, size, bytes.array())
        }

        /**
         * Shows the tray icon, or returns null when there's no StatusNotifierItem host (no session
         * bus, or GNOME without the AppIndicator extension).
         */
        fun start(title: String, icon: BufferedImage): SniTray? {
            val connection = runCatching { DBusConnectionBuilder.forSessionBus().build() }.getOrNull() ?: return null
            return runCatching {
                val bus = connection.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus::class.java)
                if (!bus.NameHasOwner(WATCHER)) {
                    connection.close()
                    return null
                }
                val busName = "org.kde.StatusNotifierItem-${ProcessHandle.current().pid()}-1"
                val tray = SniTray(connection, busName, title, ICON_SIZES.map { pixmap(icon, it) })
                connection.exportObject(ITEM_PATH, tray.item)
                connection.exportObject(MENU_PATH, tray.menu)
                connection.requestBusName(busName)
                tray.register()
                // When Plasma restarts, the new watcher only knows the icons that register again.
                connection.addSigHandler(DBus.NameOwnerChanged::class.java) { signal ->
                    if (signal.name == WATCHER && signal.newOwner.isNotEmpty()) runCatching { tray.register() }
                }
                tray
            }.getOrElse {
                runCatching { connection.close() }
                null
            }
        }
    }
}
