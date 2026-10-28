package io.github.jameslunaa.taskora.system

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers

class SingleInstanceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun secondLaunch_showsTheFirst() = runBlocking(Dispatchers.Default) {
        val first = SingleInstance.claim(tmp.root, socketDir = tmp.root)!!
        val shown = async(start = CoroutineStart.UNDISPATCHED) { first.showRequests.first() }
        assertNull(SingleInstance.claim(tmp.root, socketDir = tmp.root))
        withTimeout(5_000) { shown.await() }
        first.close()
    }

    @Test
    fun minimizedSecondLaunch_leavesTheWindowAlone() = runBlocking(Dispatchers.Default) {
        val first = SingleInstance.claim(tmp.root, socketDir = tmp.root)!!
        val shown = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(1_000) { first.showRequests.first() }
        }
        assertNull(SingleInstance.claim(tmp.root, showWindow = false, socketDir = tmp.root))
        assertNull(shown.await())
        first.close()
    }

    @Test
    fun staleSocketFile_isReplaced() = runBlocking(Dispatchers.Default) {
        val id = Integer.toHexString(tmp.root.absoluteFile.normalize().path.hashCode())
        tmp.newFile("taskora-$id.sock").writeText("left over by a crash")
        val first = SingleInstance.claim(tmp.root, socketDir = tmp.root)!!
        val shown = async(start = CoroutineStart.UNDISPATCHED) { first.showRequests.first() }
        assertNull(SingleInstance.claim(tmp.root, socketDir = tmp.root))
        withTimeout(5_000) { shown.await() }
        first.close()
    }
}
