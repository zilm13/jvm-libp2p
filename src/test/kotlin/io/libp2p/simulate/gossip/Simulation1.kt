package io.libp2p.simulate.gossip

import io.libp2p.core.pubsub.RESULT_INVALID
import io.libp2p.core.pubsub.Topic
import io.libp2p.etc.types.toByteBuf
import io.libp2p.pubsub.gossip.GossipRouter
import io.libp2p.simulate.NetworkStats
import io.libp2p.simulate.Topology
import io.libp2p.simulate.stats.StatsFactory
import io.libp2p.simulate.stats.WritableStats
import io.libp2p.simulate.topology.RandomNPeers
import io.libp2p.tools.get
import io.libp2p.tools.schedulers.ControlledExecutorServiceImpl
import io.libp2p.tools.schedulers.TimeControllerImpl
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.Random

class Simulation1 {

    val Topic = Topic("Topic-1")
    val AvrgBlockMessageSize = 32 * 1024
    val MaxMissingPeers = 32

    data class GossipStats(
        val msgDelay: WritableStats,
        val someMissingPeers: List<GossipSimPeer> = emptyList()
    )

    data class SimConfig(
        val totalPeers: Int = 10000,
        val badPeers: Int = 0,
        val peerConnections: Int = 10,

        val gossipD: Int = 6,
        val gossipDLow: Int = 3,
        val gossipDHigh: Int = 12,
        val gossipDGossip: Int = 6,

        val topology: Topology = RandomNPeers(peerConnections),
        val latency: Long = 1L
    )

    data class SimOptions(
        val warmUpDelay: Duration = Duration.ofSeconds(5),
        val zeroHeartbeatsDelay: Duration = Duration.ofMillis(500),
        val manyHeartbeatsDelay: Duration = Duration.ofSeconds(30),
        val generatedNetworksCount: Int = 1,
        val sentMessageCount: Int = 10,
        val startRandomSeed: Long = 0
    )

    data class SimResult(
        val packetCountPerMessage: WritableStats = StatsFactory.DEFAULT.createStats(),
        val trafficPerMessage: WritableStats = StatsFactory.DEFAULT.createStats(),
        val deliveredPart: WritableStats = StatsFactory.DEFAULT.createStats(),
        val deliverDelay: WritableStats = StatsFactory.DEFAULT.createStats()
    )

    data class SimDetailedResult (
        val zeroHeartbeats: SimResult = SimResult(),
        val manyHeartbeats: SimResult = SimResult()
    )

    @Test
    fun test1() {
        val peerConnections = 20
        val ress = mutableListOf<SimDetailedResult>()
        for (badPeers in arrayOf(0, 5000, 6000, 7000, 8000, 9000, 9300, 9500, 9600, 9700)) {
            val res = sim(
                SimConfig(
                    totalPeers = 10000,
                    badPeers = badPeers,
                    peerConnections = peerConnections,

                    gossipD = 6,
                    gossipDLow = 3,
                    gossipDHigh = 12,
                    gossipDGossip = 15,

                    topology = RandomNPeers(peerConnections),
                    latency = 1L
                ),
                SimOptions(
                    generatedNetworksCount = 10,
                    sentMessageCount = 10,
                    startRandomSeed = 0
                )
            )
            println("Complete: $res")
            ress += res
        }
        println("Results: ")
        println("==============")
        ress.forEachIndexed { i, res ->
            println("$i: $res")
        }
    }
    fun sim(cfg: SimConfig, opt: SimOptions): SimDetailedResult {

        println("Starting sim: \n\t$cfg\n\t$opt")

        val ret = SimDetailedResult()
        for (n in 0 until opt.generatedNetworksCount) {
            val commonRnd = Random(opt.startRandomSeed + n)

            val timeController = TimeControllerImpl()
            println("Creating peers")
            val peers = (0 until cfg.totalPeers).map {
                GossipSimPeer(Topic).apply {
                    routerInstance = GossipRouter().apply {
                        withDConstants(cfg.gossipD, cfg.gossipDLow, cfg.gossipDHigh, cfg.gossipDGossip)
                        serialize = false
                        curTime = timeController::getTime
                        random = commonRnd
                    }
//                if (name == "7") {
//                    pubsubLogs = LogLevel.ERROR
//                    wireLogs = LogLevel.ERROR
//                }

                    simExecutor = ControlledExecutorServiceImpl(timeController)
                    msgSizeEstimator = GossipSimPeer.rawPubSubMsgSizeEstimator(AvrgBlockMessageSize)
                    msgDelayer = { cfg.latency }

                    start()
                }
            }
            println("Creating test peers")
            peers[(cfg.totalPeers - cfg.badPeers) until cfg.totalPeers]
                .forEach { it.validationResult = RESULT_INVALID }

            cfg.topology.random = commonRnd

            println("Connecting peers")
            val net = cfg.topology.connect(peers)
//        val psGroup = mutableSetOf<TestGossip>()
//        val ps = mutableSetOf(testPeers[7])
//        var found = false
//        while (ps.isNotEmpty()) {
//            val p = ps.first()
//            if (p.peer.name == "0") {
//                found = true
//                break
//            }
//            ps -= p
//            psGroup += p
//            val connectedToP = p.peer.connections
//                .flatMap { listOf(it.dialer, it.listener) }
//                .distinct()
//                .mapNotNull { p -> testPeers.find { it.peer == p } }
//                .filter { it != p }
//                .filter { it.validationResult == RESULT_VALID }
//                .filter { !psGroup.contains(it) }
//            ps += connectedToP
//        }

            println("Some warm up")
            timeController.addTime(opt.warmUpDelay)

            var lastNS = net.networkStats
            println("Initial stat: $lastNS")
            net.resetStats()

            for (i in 0 until opt.sentMessageCount) {
                println("Sending message #$i...")

                val sentTime = timeController.time
                peers[i].apiPublisher.publish("Message-$i".toByteArray().toByteBuf(), Topic)

                timeController.addTime(opt.zeroHeartbeatsDelay)

                val receivePeers = peers - peers[i]
                run {
                    val ns = net.networkStats
                    val gs = calcGossipStats(receivePeers, sentTime)
                    ret.zeroHeartbeats.packetCountPerMessage.addValue(ns.msgCount)
                    ret.zeroHeartbeats.trafficPerMessage.addValue(ns.traffic)
                    receivePeers.filter { it.lastMsg != null }
                        .map { it.lastMsgTime - sentTime }
                        .forEach { ret.zeroHeartbeats.deliverDelay.addValue(it) }
                    ret.zeroHeartbeats.deliveredPart.addValue(gs.msgDelay.getCount().toDouble() / receivePeers.size)
                    println("Zero heartbeats: $ns\t\t$gs")
                }

                timeController.addTime(opt.manyHeartbeatsDelay)

                val ns0: NetworkStats
                run {
                    val ns = net.networkStats
                    ns0 = ns
                    val gs = calcGossipStats(receivePeers, sentTime)
                    ret.manyHeartbeats.packetCountPerMessage.addValue(ns.msgCount)
                    ret.manyHeartbeats.trafficPerMessage.addValue(ns.traffic)
                    receivePeers.filter { it.lastMsg != null }
                        .map { it.lastMsgTime - sentTime }
                        .forEach { ret.manyHeartbeats.deliverDelay.addValue(it) }
                    ret.manyHeartbeats.deliveredPart.addValue(gs.msgDelay.getCount().toDouble() / receivePeers.size)
                    println("Many heartbeats: $ns\t\t$gs")
                }

                timeController.addTime(Duration.ofSeconds(10))
                val nsDiff = net.networkStats - ns0
                println("Empty time: $nsDiff")

                net.resetStats()
                clearGossipStats(peers)
            }
        }
        return ret
    }

    private fun clearGossipStats(peers: List<GossipSimPeer>) {
        peers.forEach { it.lastMsg = null }
    }

    private fun calcGossipStats(peers: List<GossipSimPeer>, msgSentTime: Long): GossipStats {
        val stats = StatsFactory.DEFAULT.createStats()
        val missingPeers = mutableListOf<GossipSimPeer>()
        peers.forEach {
            if (it.lastMsg != null) {
                stats.addValue(it.lastMsgTime - msgSentTime)
            } else {
                if (missingPeers.size < MaxMissingPeers) missingPeers += it
            }
        }
        return GossipStats(stats, missingPeers)
    }
}