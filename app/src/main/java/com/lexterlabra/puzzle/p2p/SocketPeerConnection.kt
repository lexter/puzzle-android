package com.lexterlabra.puzzle.p2p

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/** [PeerConnection] over a blocking TCP socket. Reads run on an IO scope it owns. */
class SocketPeerConnection(private val socket: Socket) : PeerConnection {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val reader = socket.getInputStream()
    private val writer = socket.getOutputStream()
    private val writeLock = Mutex()
    private val inbox = Channel<Frame>(Channel.UNLIMITED)
    private val _closed = MutableStateFlow(false)

    override val incoming: Flow<Frame> = inbox.receiveAsFlow()
    override val closed: StateFlow<Boolean> = _closed.asStateFlow()

    init {
        socket.tcpNoDelay = true
        scope.launch {
            val decoder = Framing.Decoder()
            val buffer = ByteArray(READ_CHUNK)
            try {
                while (true) {
                    val read = reader.read(buffer, 0, buffer.size)
                    if (read < 0) break
                    if (read == 0) continue
                    decoder.feed(buffer.copyOf(read)).forEach { raw -> MatchCodec.fromRaw(raw)?.let { inbox.send(it) } }
                }
            } catch (_: Throwable) {
                // Socket reset or malformed stream: treated as a disconnect.
            } finally {
                close()
            }
        }
    }

    override suspend fun send(frame: Frame) {
        if (_closed.value) return
        try {
            writeLock.withLock {
                withContext(Dispatchers.IO) {
                    writer.write(MatchCodec.encode(frame))
                    writer.flush()
                }
            }
        } catch (_: Throwable) {
            close()
        }
    }

    override fun close() {
        if (_closed.value) return
        _closed.value = true
        inbox.close()
        runCatching { socket.close() }
        scope.cancel()
    }

    private companion object {
        const val READ_CHUNK = 16 * 1024
    }
}

/** Opening, accepting and dialing LAN TCP sockets (IPv4). */
object PeerSockets {
    suspend fun listen(): ServerSocket = withContext(Dispatchers.IO) {
        ServerSocket().apply { bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), 0)) }
    }

    fun port(server: ServerSocket): Int = server.localPort

    /** Blocks (interruptibly) until a peer connects; closing [server] also ends the wait. */
    suspend fun accept(server: ServerSocket): PeerConnection =
        SocketPeerConnection(runInterruptible(Dispatchers.IO) { server.accept() })

    suspend fun connect(host: String, port: Int): PeerConnection = withContext(Dispatchers.IO) {
        SocketPeerConnection(Socket().apply { connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS) })
    }

    private const val CONNECT_TIMEOUT_MS = 10_000
}
