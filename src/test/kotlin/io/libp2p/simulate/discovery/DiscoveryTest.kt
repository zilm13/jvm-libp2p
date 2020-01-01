package io.libp2p.simulate.discovery

import io.libp2p.core.PeerId
import io.libp2p.core.StreamHandler
import io.libp2p.core.crypto.KEY_TYPE
import io.libp2p.core.crypto.PrivKey
import io.libp2p.core.crypto.generateKeyPair
import io.libp2p.core.dsl.host
import io.libp2p.core.multiformats.Multiaddr
import io.libp2p.mux.mplex.MplexStreamMuxer
import io.libp2p.security.secio.SecIoSecureChannel
import io.libp2p.transport.tcp.TcpTransport
import io.netty.handler.logging.LogLevel
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.lang.RuntimeException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class DiscoveryTest {
    private val pubkeySize = 32

    private fun genPrivKey(): PrivKey {
        return generateKeyPair(KEY_TYPE.SECP256K1).first
    }

    private fun privToPubCompressed(privKey: PrivKey): ByteArray {
        val pubKey = privKey.publicKey().raw()
        return when (pubKey.size) {
            pubkeySize -> pubKey
            in 0 until pubkeySize -> {
                val res = ByteArray(pubkeySize)
                System.arraycopy(pubKey, 0, res, pubkeySize - pubKey.size, pubKey.size)
                res
            }
            in (pubkeySize + 1)..Int.MAX_VALUE -> pubKey.takeLast(pubkeySize).toByteArray()
            else -> throw RuntimeException("Not expected")
        }
    }

    @Test
    fun test1() {
        val distanceDivisor = 8 // Reduce size of Kademlia Table
        val bucketSize = 4 // Same
        val proto = "/ipfs/rpc/1.0.0"
        val privKey1 = genPrivKey()
        val addr1 = Multiaddr("/ip4/0.0.0.0/tcp/1234")
        val enr1 = Enr(addr1, PeerId(privToPubCompressed(privKey1)))
        val privKey2 = genPrivKey()
        val addr2 = Multiaddr("/ip4/0.0.0.0/tcp/1235")
        val enr2 = Enr(addr2, PeerId(privToPubCompressed(privKey2)))
        val privKey3 = genPrivKey()
        val addr3 = Multiaddr("/ip4/0.0.0.0/tcp/1236")
        val enr3 = Enr(addr3, PeerId(privToPubCompressed(privKey3)))
        val table1 = KademliaTable(
            enr1, bucketSize, 256 / distanceDivisor, distanceDivisor,
            listOf(enr2, enr3)
        )
        val discovery1 = Discovery(table1)
        val host1 = host {
            identity {
                withPrivKey(privKey1)
            }
            transports {
                +::TcpTransport
            }
            secureChannels {
                add(::SecIoSecureChannel)
            }
            muxers {
                +::MplexStreamMuxer
            }
            protocols {
                +Rpc(discovery1)
            }
            network {
                listen(enr1.addr.toString())
            }
            debug {
                muxFramesHandler.setLogger(LogLevel.ERROR, "Host-1")
            }
        }

        val table2 = KademliaTable(
            enr2, bucketSize, 256 / distanceDivisor, distanceDivisor,
            listOf(enr1)
        )
        val discovery2 = Discovery(table2)
        val host2 = host {
            identity {
                withPrivKey(privKey2)
            }
            transports {
                +::TcpTransport
            }
            secureChannels {
                add(::SecIoSecureChannel)
            }
            muxers {
                +::MplexStreamMuxer
            }
            protocols {
                +Rpc(Discovery(table2))
            }
            network {
                listen(enr2.addr.toString())
            }
            debug {
                muxFramesHandler.setLogger(LogLevel.ERROR, "Host-2")
            }
        }

        val start1 = host1.start()
        val start2 = host2.start()
        start1.get(5, TimeUnit.SECONDS)
        println("Host #1 started")
        start2.get(5, TimeUnit.SECONDS)
        println("Host #2 started")

        var streamCounter1 = 0
        host1.addStreamHandler(StreamHandler.create {
            streamCounter1++
        })
        var streamCounter2 = 0
        host2.addStreamHandler(StreamHandler.create {
            streamCounter2++
        })

        val future1: CompletableFuture<out NodesMessage>
        run {
            val ctr = host1.newStream<RpcController>(
                proto,
                enr2.id,
                enr2.addr
            )
                .controler.get(5, TimeUnit.SECONDS)
            println("Controller created for node 1")
            future1 = discovery1.findNode(1, ctr)
        }

        Assertions.assertEquals(1, host1.network.connections.size)
        Assertions.assertEquals(1, host2.network.connections.size)
        Assertions.assertEquals(1, streamCounter2)
        Assertions.assertEquals(1, streamCounter1)

        val future2: CompletableFuture<out NodesMessage>
        run {
            val ctr = host2.newStream<RpcController>(
                proto,
                enr1.id,
                enr1.addr
            )
                .controler.get(5, TimeUnit.SECONDS)
            println("Controller created for node 2")
            future2 = discovery2.findNode(1, ctr)
        }

        Assertions.assertEquals(2, host1.network.connections.size)
        Assertions.assertEquals(2, host2.network.connections.size)
        Assertions.assertEquals(2, streamCounter1)
        Assertions.assertEquals(2, streamCounter2)

        Thread.sleep(1000)
        host1.stop().get(5, TimeUnit.SECONDS)
        println("Host #1 stopped")
        host2.stop().get(5, TimeUnit.SECONDS)
        println("Host #2 stopped")

        Assertions.assertEquals(0, host1.network.connections.size)
        Assertions.assertEquals(0, host2.network.connections.size)

        val res1 = future1.get()
        println("Received result at node 1 connected to node 2: $res1")
        Assertions.assertEquals(1, res1.nodes.size)
        Assertions.assertEquals(enr1, res1.nodes.first())

        val res2 = future2.get()
        println("Received result at node 2 connected to node 1: $res2")
        Assertions.assertEquals(2, res2.nodes.size)
        Assertions.assertTrue(res2.nodes.contains(enr2))
        Assertions.assertTrue(res2.nodes.contains(enr3))

        println("Test finished successfully!!!")
    }
}