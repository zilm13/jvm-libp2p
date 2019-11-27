package io.libp2p.simulate.gossip

import io.libp2p.core.Stream
import io.libp2p.core.pubsub.createPubsubApi
import io.libp2p.etc.types.lazyVar
import io.libp2p.pubsub.PubsubRouterDebug
import io.libp2p.pubsub.flood.FloodRouter
import io.libp2p.simulate.stream.StreamSimPeer
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
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
    }
}
