package io.libp2p.simulate.discovery

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.util.Supplier
import java.util.Random
import java.util.concurrent.CompletableFuture

class Discovery(private val table: KademliaTable) : RpcLogic {
    private val log = LogManager.getLogger(Discovery::class.java)

    override fun handleIncoming(message: ByteArray, replyCallback: (ByteArray) -> CompletableFuture<Unit>) {
        val msg = Message.fromBytes(message)
        log.trace(Supplier<Any> { "Handling incoming message $msg" })
        when (msg) {
            is FindNodeMessage -> replyCallback(handleFindNode(msg))
            else -> throw RuntimeException("$msg handling is not supported")
        }
    }

    private fun handleFindNode(message: FindNodeMessage): ByteArray {
        val nodes = table.find(message.distance)
        val nodesMessage = NodesMessage(nodes)
        return nodesMessage.getBytes()
    }

    private fun genRequestId(): ByteArray {
        val size = 8
        val random = Random()
        val res = ByteArray(size)
        random.nextBytes(res)
        return res
    }

    fun findNode(distance: Int, rpc: RpcController): CompletableFuture<NodesMessage> {
        val message = FindNodeMessage(distance)
        val requestId = genRequestId()
        val future = CompletableFuture<NodesMessage>()
        rpc.call<ByteArray>(message.getBytes(), requestId).thenApply {
            future.complete(NodesMessage(it))
        }
        return future
    }
}