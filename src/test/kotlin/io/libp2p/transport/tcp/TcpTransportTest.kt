package io.libp2p.transport.tcp

import io.libp2p.core.Connection
import io.libp2p.core.ConnectionHandler
import io.libp2p.core.Libp2pException
import io.libp2p.core.crypto.KEY_TYPE
import io.libp2p.core.crypto.generateKeyPair
import io.libp2p.core.multiformats.Multiaddr
import io.libp2p.mux.mplex.MplexStreamMuxer
import io.libp2p.security.secio.SecIoSecureChannel
import io.libp2p.transport.ConnectionUpgrader
import org.apache.logging.log4j.LogManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.SECONDS

class TcpTransportTest {

    companion object {
        @JvmStatic
        fun validMultiaddrs() = listOf(
            "/ip4/1.2.3.4/tcp/1234",
            "/ip6/fe80::6f77:b303:aa6e:a16/tcp/42"
        ).map { Multiaddr(it) }

        @JvmStatic
        fun invalidMultiaddrs() = listOf(
            "/ip4/1.2.3.4/udp/42",
            "/unix/a/file/named/tcp"
        ).map { Multiaddr(it) }

        val logger = LogManager.getLogger("test")
    }

    private val upgrader = ConnectionUpgrader(emptyList(), emptyList())

    @ParameterizedTest
    @MethodSource("validMultiaddrs")
    fun `handles(addr) succeeds when addr is a tcp protocol`(addr: Multiaddr) {
        val tcp = TcpTransport(upgrader)
        assertTrue(tcp.handles(addr))
    }

    @ParameterizedTest
    @MethodSource("invalidMultiaddrs")
    fun `handles(addr) fails when addr is not a tcp protocol`(addr: Multiaddr) {
        val tcp = TcpTransport(upgrader)
        assertFalse(tcp.handles(addr))
    }

    @Test
    fun testListenClose() {
        val (privKey1, pubKey1) = generateKeyPair(KEY_TYPE.ECDSA)
        val upgrader = ConnectionUpgrader(
            listOf(SecIoSecureChannel(privKey1)),
            listOf(MplexStreamMuxer())
        )

        val tcpTransport = TcpTransport(upgrader)
        val connHandler: ConnectionHandler = object : ConnectionHandler {
            override fun handleConnection(conn: Connection) {
            }
        }

        bindListeners(tcpTransport, 5)

        val unbindFuts = mutableListOf<CompletableFuture<Unit>>()
        for (i in 0..5) {
            val unbindFuture = tcpTransport.unlisten(
                Multiaddr("/ip4/0.0.0.0/tcp/${20000 + i}")
            )
            unbindFuture.handle { t, u -> logger.info("Unbound #$i", u) }
            unbindFuts += unbindFuture
            logger.info("Unbinding #$i")
        }

        CompletableFuture.allOf(*unbindFuts.toTypedArray())
            .get(5, SECONDS)
        assertEquals(0, tcpTransport.activeListeners.size)

        bindListeners(tcpTransport, 5)

        for (i in 1..50) {
            if (tcpTransport.activeListeners.size == 6) break
            Thread.sleep(100)
        }
        assertEquals(6, tcpTransport.activeListeners.size)

        tcpTransport.close().get(5, SECONDS)
        for (i in 1..50) {
            if (tcpTransport.activeListeners.isEmpty()) break
            Thread.sleep(100)
        }
        assertEquals(0, tcpTransport.activeListeners.size)

        assertThrows(Libp2pException::class.java) {
            tcpTransport.listen(
                Multiaddr("/ip4/0.0.0.0/tcp/20000"),
                connHandler)
                .get(5, SECONDS)
        }
    }

    fun bindListeners(tcpTransport: TcpTransport, count: Int) {
        for (i in 0..count) {
            val bindFuture = tcpTransport.listen(
                Multiaddr("/ip4/0.0.0.0/tcp/${20000 + i}"),
                ConnectionHandler.create { }
            )
            bindFuture.handle { t, u -> logger.info("Bound #$i", u) }
            logger.info("Binding #$i")
        }
    }

    @Test
    fun testDialClose() {
        val (privKey1, pubKey1) = generateKeyPair(KEY_TYPE.ECDSA)
        val upgrader = ConnectionUpgrader(
            listOf(SecIoSecureChannel(privKey1)),
            listOf(MplexStreamMuxer())
        )

        val tcpTransportServer = TcpTransport(upgrader)
        val serverConnections = mutableListOf<Connection>()
        val connHandler: ConnectionHandler = object : ConnectionHandler {
            override fun handleConnection(conn: Connection) {
                logger.info("Inbound connection: $conn")
                serverConnections += conn
            }
        }

        tcpTransportServer.listen(
            Multiaddr("/ip4/0.0.0.0/tcp/20000"),
            connHandler
        ).get(5, SECONDS)
        logger.info("Server is listening")

        val tcpTransportClient = TcpTransport(upgrader)

        val dialFutures = mutableListOf<CompletableFuture<Connection>>()
        for (i in 0..50) {
            logger.info("Connecting #$i")
            dialFutures +=
                tcpTransportClient.dial(Multiaddr("/ip4/127.0.0.1/tcp/20000"), ConnectionHandler.create { })
            dialFutures.last().whenComplete { t, u -> logger.info("Connected #$i: $t ($u)") }
        }
        logger.info("Active channels: ${tcpTransportClient.activeChannels.size}")

        CompletableFuture.anyOf(*dialFutures.toTypedArray()).get(5, SECONDS)
        logger.info("The first negotiation succeeded. Closing now...")

        tcpTransportClient.close().get(5, SECONDS)
        logger.info("Client transport closed")
        tcpTransportServer.close().get(5, SECONDS)
        logger.info("Server transport closed")

        // checking that all dial futures are complete (successfully or not)
        val dialCompletions = dialFutures.map { it.handle { t, u -> t to u } }
        CompletableFuture.allOf(*dialCompletions.toTypedArray()).get(5, SECONDS)
    }
}
