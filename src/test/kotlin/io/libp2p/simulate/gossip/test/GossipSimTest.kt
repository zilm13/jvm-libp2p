package io.libp2p.simulate.gossip.test

import io.libp2p.core.pubsub.Topic
import io.libp2p.etc.types.toByteBuf
import io.libp2p.pubsub.gossip.GossipRouter
import io.libp2p.simulate.gossip.GossipSimPeer
import io.libp2p.tools.regroup
import io.libp2p.tools.schedulers.ControlledExecutorServiceImpl
import io.libp2p.tools.schedulers.TimeControllerImpl
import io.netty.handler.logging.LogLevel
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.function.Consumer

class GossipSimTest {

    @Test
    fun simplest1() {
        val timeController = TimeControllerImpl()

        val createPeer = {
            val peer = GossipSimPeer(Topic("aaa"))
            peer.routerInstance = GossipRouter().also { it.serialize = true }
            peer.pubsubLogs = LogLevel.ERROR
            peer.simExecutor = ControlledExecutorServiceImpl(timeController)
            peer
        }

        val p1 = createPeer()
        val p2 = createPeer()

        val c1 = p1.connect(p2).get()
        var gotIt = false
        p2.api.subscribe(Consumer { gotIt = true }, Topic("a"))
        val p1Pub = p1.api.createPublisher(p1.keyPair.first, 0)
        val m1 = p1Pub.publish("Hello".toByteArray().toByteBuf(), Topic("a"))

        Assertions.assertTrue(gotIt)
        val c1_1 = c1.dialerStat
        val c1_2 = c1.listenerStat

        println("$c1_1, $c1_2")
    }

    @Test
    fun regroupTest() {
        val tl: Map<String, List<Int>> = listOf(
            mapOf(
                "a" to 1,
                "b" to 1,
                "c" to 1
            ),
            mapOf(
                "a" to 2,
                "b" to 2,
                "d" to 2
            )
        ).regroup()

        println(tl)
    }
}