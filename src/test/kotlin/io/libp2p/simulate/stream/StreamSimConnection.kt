package io.libp2p.simulate.stream

import io.libp2p.etc.types.forward
import io.libp2p.simulate.ConnectionStat
import io.libp2p.simulate.RandomValue
import io.libp2p.simulate.SimConnection
import java.util.concurrent.CompletableFuture

class StreamSimConnection(
    override val dialer: StreamSimPeer<*>,
    override val listener: StreamSimPeer<*>,
    val dialerConnection: StreamSimChannel.Connection,
    var listenerConnection: StreamSimChannel.Connection? = null
) : SimConnection {

    override val closed = CompletableFuture<Unit>()

    override fun close() {
        CompletableFuture.allOf(
            dialerConnection.disconnect(),
            listenerConnection?.disconnect() ?: CompletableFuture.completedFuture(Unit)
        ).thenApply { Unit }
            .forward(closed)
    }

    override val dialerStat: ConnectionStat
        get() = ConnectionStat(
            dialerConnection.ch1.msgCount.get() + (listenerConnection?.ch2?.msgCount?.get() ?: 0),
            dialerConnection.ch1.totSize.get() + (listenerConnection?.ch2?.totSize?.get() ?: 0))
    override val listenerStat: ConnectionStat
        get() = ConnectionStat(
            dialerConnection.ch2.msgCount.get() + (listenerConnection?.ch1?.msgCount?.get() ?: 0),
            dialerConnection.ch2.totSize.get() + (listenerConnection?.ch1?.totSize?.get() ?: 0))

    override fun setLatency(latency: RandomValue) {
        super.setLatency(latency)
    }
}