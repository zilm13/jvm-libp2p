package io.libp2p.simulate

import java.util.concurrent.CompletableFuture

interface SimConnection {

    val dialer: SimPeer
    val listener: SimPeer
    val closed: CompletableFuture<Unit>
    val dialerStat: ConnectionStat
    val listenerStat: ConnectionStat

    fun close()

    fun isClosed() = closed.isDone

    fun setLatency(latency: RandomValue): Unit = TODO()
}

data class ConnectionStat(
    val msgCount: Long = 0L,
    val msgSize: Long = 0L
)
