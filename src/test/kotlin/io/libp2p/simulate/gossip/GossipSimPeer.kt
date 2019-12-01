package io.libp2p.simulate.gossip

import io.libp2p.core.Stream
import io.libp2p.core.pubsub.createPubsubApi
import io.libp2p.etc.types.lazyVar
import io.libp2p.pubsub.PubsubRouterDebug
import io.libp2p.pubsub.flood.FloodRouter
import io.libp2p.simulate.stream.StreamSimPeer
import io.libp2p.simulate.util.MsgSizeEstimator
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import pubsub.pb.Rpc
import java.util.concurrent.CompletableFuture

class GossipSimPeer : StreamSimPeer<Unit>(true) {

    var routerInstance: PubsubRouterDebug by lazyVar { FloodRouter() }
    var router by lazyVar {
        routerInstance.also {
            it.executor = simExecutor
        }
    }
    val api by lazy { createPubsubApi(router) }
    val apiPublisher by lazy { api.createPublisher(keyPair.first, 0L) }
    var pubsubLogs: LogLevel? = null

    override fun handleStream(stream: Stream): CompletableFuture<out Unit> {
        router.addPeerWithDebugHandler(stream, pubsubLogs?.let {
            LoggingHandler(name, it)
        })
        return dummy
    }

    companion object {
        private val dummy = CompletableFuture.completedFuture(Unit)

        fun rawPubSubMsgSizeEstimator(avrgMsgLen: Int, measureTcpOverhead: Boolean = true): MsgSizeEstimator = { msg: Any ->
            val payloadSize = (msg as Rpc.RPC).run {
                subscriptionsList.sumBy { it.topicid.length + 2 } +
                        control.graftList.sumBy { it.topicID.length + 1 } +
                        control.pruneList.sumBy { it.topicID.length + 1 } +
                        control.ihaveList.flatMap { it.messageIDsList }.sumBy { it.length + 1 } +
                        control.iwantList.flatMap { it.messageIDsList }.sumBy { it.length + 1 } +
                        publishList.sumBy { avrgMsgLen + it.topicIDsList.sumBy { it.length } + 224 } +
                        6
            }
            payloadSize + ((payloadSize / 1460) + 1) * 40
        }
    }
}
