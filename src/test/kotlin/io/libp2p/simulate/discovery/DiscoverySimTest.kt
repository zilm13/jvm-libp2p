package io.libp2p.simulate.discovery

import io.libp2p.core.PeerId
import io.libp2p.core.multiformats.Multiaddr
import io.libp2p.mux.mplex.MplexStreamMuxer
import io.libp2p.security.secio.SecIoSecureChannel
import io.libp2p.simulate.connection.LoopbackNetwork
import io.libp2p.simulate.discovery.KeyUtils.Companion.genPrivKey
import io.libp2p.simulate.discovery.KeyUtils.Companion.privToPubCompressed
import io.libp2p.tools.schedulers.ControlledExecutorServiceImpl
import io.libp2p.tools.schedulers.TimeControllerImpl
import io.netty.handler.logging.LogLevel
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class DiscoverySimTest {

    @Test
    fun discoveryInSimNetwork() {
        val timeController = TimeControllerImpl()

        val net = LoopbackNetwork().also {
            it.simExecutor = ControlledExecutorServiceImpl(timeController)
        }
        val distanceDivisor = 8 // Reduce size of Kademlia Table
        val bucketSize = 4 // Same
        val proto = "/ipfs/rpc/1.0.0"
        val privKey1 = genPrivKey()
        val addr1 = Multiaddr("/ip4/1.2.3.0/tcp/30303")
        val enr1 = Enr(addr1, PeerId(privToPubCompressed(privKey1)))
        val privKey2 = genPrivKey()
        val addr2 = Multiaddr("/ip4/1.2.3.1/tcp/30303")
        val enr2 = Enr(addr2, PeerId(privToPubCompressed(privKey2)))
        val privKey3 = genPrivKey()
        val addr3 = Multiaddr("/ip4/1.2.3.2/tcp/30303")
        val enr3 = Enr(addr3, PeerId(privToPubCompressed(privKey3)))
        val table1 = KademliaTable(
            enr1, bucketSize, 256 / distanceDivisor, distanceDivisor,
            listOf(enr2, enr3)
        )
        val discovery1 = Discovery(table1)

        val host1 = net.newPeer {
            identity {
                withPrivKey(privKey1)
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
                beforeSecureHandler.setLogger(LogLevel.ERROR, "Host-1")
                afterSecureHandler.setLogger(LogLevel.ERROR, "Host-1")
                muxFramesHandler.setLogger(LogLevel.ERROR, "Host-1")
            }
        }

        val table2 = KademliaTable(
            enr2, bucketSize, 256 / distanceDivisor, distanceDivisor,
            listOf(enr1)
        )
        val discovery2 = Discovery(table2)
        val host2 = net.newPeer {
            identity {
                withPrivKey(privKey2)
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
                beforeSecureHandler.setLogger(LogLevel.ERROR, "Host-2")
                afterSecureHandler.setLogger(LogLevel.ERROR, "Host-2")
                muxFramesHandler.setLogger(LogLevel.ERROR, "Host-2")
            }
        }

        host1.start().get(1, TimeUnit.SECONDS)
        host2.start().get(1, TimeUnit.SECONDS)

        val future1: CompletableFuture<out NodesMessage>
        run {
            val ctr = host1.host.newStream<RpcController>(
                proto,
                enr2.id,
                enr2.addr
            )
                .controler.get(5, TimeUnit.SECONDS)
            println("Controller created for node 1")
            future1 = discovery1.findNode(1, ctr)
        }

        val future2: CompletableFuture<out NodesMessage>
        run {
            val ctr = host2.host.newStream<RpcController>(
                proto,
                enr1.id,
                enr1.addr
            )
                .controler.get(5, TimeUnit.SECONDS)
            println("Controller created for node 2")
            future2 = discovery2.findNode(1, ctr)
        }

        Thread.sleep(1000)
        host1.stop().get(5, TimeUnit.SECONDS)
        println("Host #1 stopped")
        host2.stop().get(5, TimeUnit.SECONDS)
        println("Host #2 stopped")

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