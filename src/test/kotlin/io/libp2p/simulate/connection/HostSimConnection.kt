package io.libp2p.simulate.connection

import io.libp2p.core.Connection
import io.libp2p.simulate.ConnectionStat
import io.libp2p.simulate.SimConnection

class HostSimConnection(
    override val dialer: HostSimPeer,
    override val listener: HostSimPeer,
    val conn: Connection
) : SimConnection {

    override val closed = conn.closeFuture()

    override fun close() {
        conn.nettyChannel.close()
    }

    override val dialerStat: ConnectionStat
        get() = TODO("not implemented")
    override val listenerStat: ConnectionStat
        get() = TODO("not implemented")
}