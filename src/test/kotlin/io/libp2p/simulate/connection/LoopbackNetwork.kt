package io.libp2p.simulate.connection

import io.libp2p.core.Host
import io.libp2p.core.dsl.Builder
import io.libp2p.core.dsl.TransportsBuilder
import io.libp2p.core.multiformats.Multiaddr
import io.libp2p.core.multiformats.Protocol
import io.libp2p.etc.types.lazyVar
import io.libp2p.host.HostImpl
import io.libp2p.simulate.Network
import io.libp2p.transport.ConnectionUpgrader
import java.util.Random
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicInteger

class LoopbackNetwork : Network {
    var random = Random(0)
    var simExecutor: ScheduledExecutorService by lazyVar { TODO() }

    private var ipCounter = AtomicInteger(1)
    internal val ipTransportMap = mutableMapOf<String, LoopbackTransport>()

    fun createLoopbackTransportCtor(newIp: String = newIp()): (ConnectionUpgrader) -> LoopbackTransport {
        return { upgrader ->
            LoopbackTransport(upgrader, this, simExecutor, newIp).also {
                ipTransportMap[it.localIp] = it
            }
        }
    }

    fun newPeer(host: Host): HostSimPeer =
        HostSimPeer(host).also {
            it.transport // check the right transport
            peers += it
        }

    fun newPeer(fn: Builder.() -> Unit): HostSimPeer {
        val host = (object : Builder() {
            override fun transports(fn: TransportsBuilder.() -> Unit): Builder {
                throw UnsupportedOperationException("Transports shouldn't be configured by client code")
            }

            override fun build(): HostImpl {
                transports += if (network.listen.isEmpty()) {
                    createLoopbackTransportCtor()
                } else {
                    val ipBytes = Multiaddr(network.listen[0]).getComponent(Protocol.IP4)
                        ?: throw RuntimeException("IP4 address in listen block is required")
                    createLoopbackTransportCtor(Protocol.IP4.bytesToAddress(ipBytes))
                }
                return super.build()
            }
        }).apply(fn).build()
        return newPeer(host)
    }

    private fun newIp(): String {
        val ip = ipCounter.getAndIncrement()
        return "" + ip.shr(24) +
                "." + ip.shr(16).and(0xFF) +
                "." + ip.shr(8).and(0xFF) +
                "." + ip.and(0xFF)
    }

    override val peers: MutableList<HostSimPeer> = mutableListOf()

    override fun resetStats() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}