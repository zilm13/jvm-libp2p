package io.libp2p.simulate.gossip

import io.libp2p.core.Stream
import io.libp2p.core.StreamHandler
import io.libp2p.core.pubsub.createPubsubApi
import io.libp2p.etc.types.lazyVar
import io.libp2p.pubsub.PubsubRouterDebug
import io.libp2p.pubsub.flood.FloodRouter
import io.libp2p.simulate.stream.StreamSimPeer
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import pubsub.pb.Rpc
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue

class GossipSimPeer : StreamSimPeer<Unit>(true), StreamHandler<Unit> {
    val inboundMessages = LinkedBlockingQueue<Rpc.Message>()
    var routerHandler: (Rpc.Message) -> Unit = {
        inboundMessages += it
    }

    var routerInstance: PubsubRouterDebug by lazyVar { FloodRouter() }
    var router by lazyVar {
        routerInstance.also {
//            it.initHandler(routerHandler)
            it.executor = testExecutor
        }
    }
    val api by lazyVar { createPubsubApi(router) }
    var pubsubLogs: LogLevel? = null
    val dummy = CompletableFuture.completedFuture(Unit)

    override fun getStreamHandler(): StreamHandler<Unit> = this

    override fun handleStream(stream: Stream): CompletableFuture<out Unit> {
        router.addPeerWithDebugHandler(stream, pubsubLogs?.let {
            LoggingHandler(name, it)
        })
        return dummy
    }
}
