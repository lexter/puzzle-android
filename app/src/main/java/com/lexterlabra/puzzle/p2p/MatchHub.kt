package com.lexterlabra.puzzle.p2p

import android.content.Context
import com.lexterlabra.puzzle.game.monotonicMillis
import com.lexterlabra.puzzle.image.PuzzleImage
import com.lexterlabra.puzzle.platform.PLATFORM_NAME
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.ServerSocket

/**
 * App-wide owner of the current match, so it survives the Lobby → Race screen change.
 * Lives on the Main dispatcher; at most one connection at a time.
 */
class MatchHub(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val appContext = context.applicationContext
    private val _controller = MutableStateFlow<MatchController?>(null)
    val controller: StateFlow<MatchController?> = _controller.asStateFlow()

    val discovery: Discovery by lazy { Discovery(appContext) }
    private val clock = monotonicMillis()
    private var server: ServerSocket? = null
    private var hostJob: Job? = null

    /** Opens a socket, advertises it, and accepts the first player who connects. */
    fun host(name: String, onError: (Throwable) -> Unit) {
        end()
        hostJob = scope.launch {
            try {
                val socket = PeerSockets.listen()
                server = socket
                discovery.advertise(name, PeerSockets.port(socket))
                val connection = PeerSockets.accept(socket)
                discovery.stopAdvertising()
                socket.close()
                server = null
                _controller.value = newController(Role.HOST, connection, name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Closing the listening socket in end() unblocks accept() with an error; that's a cancel, not a failure.
                currentCoroutineContext().ensureActive()
                onError(e)
            }
        }
    }

    suspend fun join(host: FoundHost, name: String) {
        end()
        val connection = PeerSockets.connect(host.host, host.port)
        _controller.value = newController(Role.GUEST, connection, name)
    }

    /** Leaves any match, stops advertising and closes the listening socket. */
    fun end() {
        hostJob?.cancel()
        hostJob = null
        runCatching { discovery.stopAdvertising() }
        server?.close()
        server = null
        _controller.value?.leave()
        _controller.value = null
    }

    private fun newController(role: Role, connection: PeerConnection, name: String) = MatchController(
        role = role,
        connection = connection,
        myName = name,
        platform = PLATFORM_NAME,
        scope = scope,
        now = clock,
        validateImage = { PuzzleImage.prepare(it).isSuccess },
    )
}
