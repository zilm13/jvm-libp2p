package io.libp2p.simulate.stream

import io.libp2p.etc.types.forward
import io.libp2p.simulate.SimConnection
import io.libp2p.tools.TestChannel
import java.util.concurrent.CompletableFuture

class StreamSimConnection(
    override val dialer: StreamSimPeer<*>,
    override val listener: StreamSimPeer<*>,
    val dialerConnection: TestChannel.TestConnection,
    var listenerConnection: TestChannel.TestConnection? = null
) : SimConnection {

    override val closed = CompletableFuture<Unit>()

    override fun close() {
        CompletableFuture.allOf(
            dialerConnection.disconnect(),
            listenerConnection?.disconnect() ?: CompletableFuture.completedFuture(Unit)
        ).thenApply { Unit }
            .forward(closed)
    }
}