package io.libp2p.simulate.main

import io.libp2p.core.pubsub.MessageApi
import io.libp2p.core.pubsub.Subscriber
import io.libp2p.core.pubsub.Topic
import io.libp2p.etc.types.toByteBuf
import io.libp2p.pubsub.gossip.GossipRouter
import io.libp2p.simulate.SimConnection
import io.libp2p.simulate.gossip.GossipSimPeer
import io.libp2p.simulate.topology.RandomNPeers
import io.libp2p.tools.schedulers.ControlledExecutorServiceImpl
import io.libp2p.tools.schedulers.TimeControllerImpl
import org.junit.jupiter.api.Test
import java.lang.Long.max
import java.time.Duration
import java.util.Random

class Test1 {

    val topic = Topic("Topic-1")

    inner class TestGossip(
        val peer: GossipSimPeer
    ) {

        val subscription = peer.api.subscribe(Subscriber { onNewMsg(it) }, topic)
        var lastMsg: MessageApi? = null
        var lastMsgTime = 0L

        fun onNewMsg(msg: MessageApi) {
            lastMsg = msg
            lastMsgTime = peer.router.curTime()
        }
    }

    data class NetworkStats(
        val msgCount: Long,
        val msgSize: Long
    ) {
        operator fun minus(other: NetworkStats) =
            NetworkStats(msgCount - other.msgCount, msgSize - other.msgSize)
    }

    data class GossipStats(
        val msgDelivered: Long,
        val msgMaxDelay: Long,
        val missingPeers: List<TestGossip> = emptyList()
    )

    @Test
    fun test1() {

        val timeController = TimeControllerImpl()
        val commonRnd = Random(2)

        println("Creating peers")
        val peers = (0..9999).map {
            GossipSimPeer().apply {
                routerInstance = GossipRouter().apply {
                    withDConstants(6, 6, 6, 100)
                    serialize = true
                    curTime = timeController::getTime
                    random = commonRnd
                }
//                if (name == "133") {
//                    pubsubLogs = LogLevel.ERROR
//                }
                simExecutor = ControlledExecutorServiceImpl(timeController)
            }
        }
        println("Creating test peers")
        val testPeers = peers.map { TestGossip(it) }

        val topology = RandomNPeers(10).apply {
            random = commonRnd
        }
        println("Connecting peers")
        val connections = topology.connect(peers)

        println("Some warm up")
        timeController.addTime(Duration.ofMinutes(1))

        var lastNS = calcNetStats(connections)
        println("Initial stat: $lastNS")

        for (i in 0..9) {
            println("Sending message #$i...")
            val sentTime = timeController.time
            testPeers[i].peer.apiPublisher.publish("Message-$i".toByteArray().toByteBuf(), topic)

//            timeController.addTime(Duration.ofMillis(1))
            val ns1 = calcNetStats(connections)
            println("Net stats-1: ${ns1 - lastNS}")
            val gs1 = calcGossipStats(testPeers - testPeers[i], sentTime)
            println("Gossip-1: $gs1")

            timeController.addTime(Duration.ofMinutes(1))

            val ns = calcNetStats(connections)
            val nsDiff = ns - lastNS
            lastNS = ns
            println("Net stats: $nsDiff")

            val gs = calcGossipStats(testPeers - testPeers[i], sentTime)
            println("Gossip-2: $gs")

            clearGossipStats(testPeers)
        }
    }

    private fun clearGossipStats(peers: List<TestGossip>) {
        peers.forEach { it.lastMsg = null }
    }

    private fun calcGossipStats(peers: List<TestGossip>, msgSentTime: Long): GossipStats {
        var msgDeliveredCount = 0L
        var maxTime = 0L
        val missingPeers = mutableListOf<TestGossip>()
        peers.forEach {
            if (it.lastMsg != null) {
                msgDeliveredCount++
                maxTime = max(maxTime, it.lastMsgTime - msgSentTime)
            } else {
                missingPeers += it
            }
        }
        return GossipStats(msgDeliveredCount, maxTime, missingPeers)
    }

    private fun calcNetStats(connections: List<SimConnection>): NetworkStats {
        var msgCount = 0L
        var msgSize = 0L
        connections.forEach {
            msgCount += it.dialerStat.msgCount + it.listenerStat.msgCount
            msgSize += it.dialerStat.msgSize + it.listenerStat.msgSize
        }
        return NetworkStats(msgCount, msgSize)
    }

//    private fun calcNetStats(peers: List<TestGossip>, sendingPeer: TestGossip?, sentTime: Long): NetworkStats {
//        var maxDeliverTime = 0L
//        peers.forEach {
//            if (it.lastMsg == null) {
//                maxDeliverTime = Long.MAX_VALUE
//            } else {
//                maxDeliverTime = max(maxDeliverTime, )
//            }
//        }
//    }
}