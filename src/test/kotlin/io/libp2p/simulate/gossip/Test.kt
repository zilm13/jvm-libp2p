package io.libp2p.simulate.gossip

import io.libp2p.core.pubsub.Topic
import io.libp2p.etc.types.toByteBuf
import io.netty.handler.logging.LogLevel
import org.junit.jupiter.api.Test
import java.util.function.Consumer

class Test {
    @Test
    fun a() {
        val p1 = GossipSimPeer()
        val p2 = GossipSimPeer()
        p1.pubsubLogs = LogLevel.ERROR
        p2.pubsubLogs = LogLevel.ERROR
        val c1 = p1.connect(p2)
        p2.api.subscribe(Consumer { println("p2: $it") }, Topic("a"))
        val p1Pub = p1.api.createPublisher(p1.keyPair.first, 0)
        val m1 = p1Pub.publish("Hello".toByteArray().toByteBuf(), Topic("a"))

    }
}