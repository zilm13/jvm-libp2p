package io.libp2p.simulate

import java.util.concurrent.CompletableFuture

interface SimConnection {

    val dialer: SimPeer
    val listener: SimPeer
    val closed: CompletableFuture<Unit>

    fun close()

    fun isClosed() = closed.isDone

    /**
     * latency == 1.0 : lost packet
     */
    fun setLatency(latency: RandomValue): Unit = TODO()
}