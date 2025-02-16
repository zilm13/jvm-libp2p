package io.libp2p.tools

import io.libp2p.core.Connection
import io.libp2p.core.ConnectionHandler
import io.libp2p.core.multiformats.Multiaddr
import io.libp2p.core.transport.Transport
import java.util.concurrent.CompletableFuture

class NullTransport : Transport {
    override val activeConnections: Int
        get() = stub()
    override val activeListeners: Int
        get() = stub()

    override fun initialize() = stub()
    override fun listenAddresses() = stub()
    override fun close(): CompletableFuture<Unit> = stub()
    override fun listen(addr: Multiaddr, connHandler: ConnectionHandler): CompletableFuture<Unit> = stub()
    override fun unlisten(addr: Multiaddr): CompletableFuture<Unit> = stub()
    override fun dial(addr: Multiaddr, connHandler: ConnectionHandler): CompletableFuture<Connection> = stub()
    override fun handles(addr: Multiaddr): Boolean = stub()

    private fun stub(): Nothing {
        throw NotImplementedError("Test stub")
    }
}