package io.libp2p.simulate.main

import io.libp2p.core.pubsub.MessageApi
import io.libp2p.core.pubsub.RESULT_INVALID
import io.libp2p.core.pubsub.RESULT_VALID
import io.libp2p.core.pubsub.Topic
import io.libp2p.core.pubsub.Validator
import io.libp2p.etc.types.toByteBuf
import io.libp2p.pubsub.gossip.GossipRouter
import io.libp2p.simulate.SimConnection
import io.libp2p.simulate.gossip.GossipSimPeer
import io.libp2p.simulate.topology.RandomNPeers
import io.libp2p.tools.get
import io.libp2p.tools.schedulers.ControlledExecutorServiceImpl
import io.libp2p.tools.schedulers.TimeControllerImpl
import org.junit.jupiter.api.Test
import java.lang.Long.max
import java.time.Duration
import java.util.Random

class Test1 {

    val topic = Topic("Topic-1")
    val AvrgBlockMessageSize = 32 * 1024

    inner class TestGossip(
        val peer: GossipSimPeer
    ) {

        var validationResult = RESULT_VALID
        val subscription = peer.api.subscribe(Validator {
            onNewMsg(it)
            validationResult
        }, topic)
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
        val commonRnd = Random(3)

        println("Creating peers")
        val peers = (0..9999).map {
            GossipSimPeer().apply {
                routerInstance = GossipRouter().apply {
                    withDConstants(4, 4, 4, 100)
                    serialize = false
                    curTime = timeController::getTime
                    random = commonRnd
                }
//                api.subscribe(Validator { RESULT_INVALID }, topic)
//                if (name == "1") {
//                    pubsubLogs = LogLevel.ERROR
//                    wireLogs = LogLevel.ERROR
//                }
                simExecutor = ControlledExecutorServiceImpl(timeController)
                msgSizeEstimator = GossipSimPeer.rawPubSubMsgSizeEstimator(AvrgBlockMessageSize)
                msgDelayer = { 1 }
            }
        }
        println("Creating test peers")
        val testPeers = peers.map { TestGossip(it) }
        testPeers[1..5000].forEach { it.validationResult = RESULT_INVALID }

        val topology = RandomNPeers(10).apply {
            random = commonRnd
        }
        println("Connecting peers")
        val connections = topology.connect(peers)

        println("Some warm up")
        timeController.addTime(Duration.ofSeconds(5))

        var lastNS = calcNetStats(connections)
        println("Initial stat: $lastNS")

        for (i in 0..9) {
            println("Sending message #$i...")
            val sentTime = timeController.time
            testPeers[i].peer.apiPublisher.publish("Message-$i".toByteArray().toByteBuf(), topic)

            timeController.addTime(Duration.ofMillis(500))
            val ns1 = calcNetStats(connections)
            println("Net stats-1: ${ns1 - lastNS}")
            val gs1 = calcGossipStats(testPeers - testPeers[i], sentTime)
            println("Gossip-1: $gs1")

            timeController.addTime(Duration.ofSeconds(12))

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