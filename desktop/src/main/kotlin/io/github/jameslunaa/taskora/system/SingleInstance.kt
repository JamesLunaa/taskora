package io.github.jameslunaa.taskora.system

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.StandardOpenOption
import kotlin.concurrent.thread

/**
 * Keeps Taskora to one running copy per user: two would share the database and the settings
 * file, which DataStore doesn't allow. The first copy holds a lock on `instance.lock` in the data
 * folder and listens on a Unix socket (Windows 10 has them too); a later launch asks it to show its
 * window and exits. That's also how the window comes back when there's no tray to open it from.
 */
class SingleInstance private constructor(
    // Held for the process's lifetime; the OS releases the lock when it exits.
    @Suppress("unused") private val lock: FileLock,
    private val server: ServerSocketChannel?,
    private val socket: java.nio.file.Path,
) {
    private val _showRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Another launch asked for the window. */
    val showRequests: SharedFlow<Unit> = _showRequests.asSharedFlow()

    init {
        server?.let { channel ->
            thread(isDaemon = true, name = "taskora-instance") {
                while (channel.isOpen) {
                    val client = runCatching { channel.accept() }.getOrNull() ?: continue
                    client.use { if (it.readMessage() == SHOW) _showRequests.tryEmit(Unit) }
                }
            }
        }
    }

    fun close() {
        server?.close()
        if (server != null) socket.toFile().delete()
    }

    companion object {
        private const val SHOW = "show"
        private const val QUIET = "quiet"

        /**
         * Becomes the running copy and returns it, or, if one is already running, passes it the
         * launch (showing its window unless [showWindow] is false) and returns null.
         */
        fun claim(dir: File, showWindow: Boolean = true, socketDir: File = defaultSocketDir()): SingleInstance? {
            dir.mkdirs()
            socketDir.mkdirs()
            // Socket paths are limited to about 100 characters, so the name is short: one per data folder.
            val id = Integer.toHexString(dir.absoluteFile.normalize().path.hashCode())
            val socket = File(socketDir, "taskora-$id.sock").toPath()
            val channel = FileChannel.open(File(dir, "instance.lock").toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null // Held by this same process (only in tests).
            }
            if (lock == null) {
                channel.close()
                signal(socket, if (showWindow) SHOW else QUIET)
                return null
            }
            // A socket file left by a copy that crashed would block bind().
            val server = runCatching {
                socket.toFile().delete()
                ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(UnixDomainSocketAddress.of(socket))
            }.getOrNull()
            return SingleInstance(lock, server, socket)
        }

        /** `$XDG_RUNTIME_DIR` (per user, made for sockets), else the temp folder (per user on Windows). */
        private fun defaultSocketDir(): File =
            System.getenv("XDG_RUNTIME_DIR")?.takeIf { it.isNotBlank() }?.let(::File)
                ?: File(System.getProperty("java.io.tmpdir"))

        /** The running copy may still be starting up, so this retries for a moment. */
        private fun signal(socket: java.nio.file.Path, message: String) {
            repeat(20) {
                val sent = runCatching {
                    SocketChannel.open(UnixDomainSocketAddress.of(socket)).use {
                        it.write(ByteBuffer.wrap("$message\n".toByteArray()))
                    }
                }.isSuccess
                if (sent) return
                Thread.sleep(100)
            }
        }

        private fun SocketChannel.readMessage(): String {
            val buffer = ByteBuffer.allocate(64)
            while (buffer.hasRemaining() && read(buffer) > 0) Unit
            return String(buffer.array(), 0, buffer.position()).trim()
        }
    }
}
