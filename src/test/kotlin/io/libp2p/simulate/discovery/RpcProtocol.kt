package io.libp2p.simulate.discovery

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.UnknownFieldSet
import io.libp2p.core.BadPeerException
import io.libp2p.core.ConnectionClosedException
import io.libp2p.core.Libp2pException
import io.libp2p.core.P2PAbstractChannel
import io.libp2p.core.P2PAbstractHandler
import io.libp2p.core.multistream.Mode
import io.libp2p.core.multistream.ProtocolBinding
import io.libp2p.core.multistream.ProtocolMatcher
import io.libp2p.etc.types.completedExceptionally
import io.libp2p.etc.types.forward
import io.libp2p.etc.types.lazyVar
import io.libp2p.etc.types.toByteArray
import io.libp2p.etc.types.toByteBuf
import io.libp2p.etc.types.toHex
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.SimpleChannelInboundHandler
import org.apache.logging.log4j.LogManager
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.util.Collections
import java.util.Random
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

interface RpcController {
    fun <TReply> call(
        msg: ByteArray,
        requestId: ByteArray
    ): CompletableFuture<TReply>
}

interface RpcLogic {
    fun handleIncoming(message: ByteArray, replyCallback: (ByteArray) -> CompletableFuture<Unit>)
}

class Rpc(logic: RpcLogic) : RpcBinding(RpcProtocol(logic))

open class RpcBinding(val rpc: RpcProtocol) : ProtocolBinding<RpcController> {
    final override val announce = "/ipfs/rpc"
    override val matcher = ProtocolMatcher(Mode.PREFIX, name = announce)

    override fun initChannel(ch: P2PAbstractChannel, selectedProtocol: String): CompletableFuture<out RpcController> {
        return rpc.initChannel(ch)
    }
}

class RpcTimeoutException : Libp2pException()

open class RpcProtocol(val logic: RpcLogic) : P2PAbstractHandler<RpcController> {
    var scheduler by lazyVar { Executors.newSingleThreadScheduledExecutor() }
    var curTime: () -> Long = { System.currentTimeMillis() }
    var callTimeout = Duration.ofSeconds(10)

    override fun initChannel(ch: P2PAbstractChannel): CompletableFuture<RpcController> {
        return if (ch.isInitiator) {
            val handler = RpcInitiatorChannelHandler()
            ch.nettyChannel.pipeline().addLast(handler)
            handler.activeFuture.thenApply { handler }
        } else {
            val handler = RpcResponderChannelHandler(logic)
            ch.nettyChannel.pipeline().addLast(handler)
            CompletableFuture.completedFuture(handler)
        }
    }

    inner class RpcResponderChannelHandler(val logic: RpcLogic) : ChannelInboundHandlerAdapter(), RpcController {
        private val log = LogManager.getLogger(RpcResponderChannelHandler::class.java)
        lateinit var ctx: ChannelHandlerContext

        override fun channelActive(ctx: ChannelHandlerContext) {
            log.trace(Supplier<Any> { "Channel active for $ctx" })
            this.ctx = ctx
        }

        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            val inputStream: CodedInputStream = CodedInputStream.newInstance((msg as ByteBuf).toByteArray())
            val fields: UnknownFieldSet = UnknownFieldSet.parseFrom(inputStream)
            val requestId = fields.getField(1).lengthDelimitedList[0].toByteArray()
            val dataBytes = fields.getField(2).lengthDelimitedList[0].toByteArray()
            logic.handleIncoming(dataBytes) { replyBytes -> callInContext(requestId, replyBytes, ctx) }
        }

        private fun callInContext(
            requestId: ByteArray,
            replyBytes: ByteArray,
            ctx: ChannelHandlerContext
        ): CompletableFuture<Unit> {
            val future = CompletableFuture<Unit>()
            ctx.executor().execute {
                call<Unit>(replyBytes, requestId).forward(future)
            }
            return future
        }

        override fun <TReply> call(
            msg: ByteArray,
            requestId: ByteArray
        ): CompletableFuture<TReply> {
            log.trace(Supplier<Any> { "Sending reply message $msg in $ctx" })
            val baos = ByteArrayOutputStream()
            val out: CodedOutputStream = CodedOutputStream.newInstance(baos)
            out.writeByteArray(1, requestId)
            out.writeByteArray(2, msg)
            out.flush()
            ctx.writeAndFlush(baos.toByteArray().toByteBuf())
            return CompletableFuture()
        }
    }

    inner class RpcInitiatorChannelHandler : SimpleChannelInboundHandler<ByteBuf>(),
        RpcController {
        val activeFuture = CompletableFuture<Unit>()
        val requests = Collections.synchronizedMap(mutableMapOf<String, Pair<Long, CompletableFuture<ByteArray>>>())
        val random = Random()
        var closed = false
        lateinit var ctx: ChannelHandlerContext
        private val log = LogManager.getLogger(RpcInitiatorChannelHandler::class.java)

        override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
            val inputStream: CodedInputStream = CodedInputStream.newInstance(msg.toByteArray())
            val fields: UnknownFieldSet = UnknownFieldSet.parseFrom(inputStream)
            val requestId = fields.getField(1).lengthDelimitedList[0].toByteArray()
            val requestIdS = requestId.toHex()
            val (_, future) = requests.remove(requestIdS)
                ?: throw BadPeerException("Unknown or expired call data in response: $requestIdS")
            val dataBytes = fields.getField(2).lengthDelimitedList[0].toByteArray()
            future.complete(dataBytes)
        }

        override fun channelUnregistered(ctx: ChannelHandlerContext) {
            closed = true
            activeFuture.completeExceptionally(ConnectionClosedException())
            synchronized(requests) {
                requests.values.forEach { it.second.completeExceptionally(ConnectionClosedException()) }
                requests.clear()
            }
            super.channelUnregistered(ctx)
        }

        override fun channelActive(ctx: ChannelHandlerContext) {
            log.trace(Supplier<Any> { "Channel active for $ctx" })
            this.ctx = ctx
            activeFuture.complete(null)
        }

        @Suppress("UNCHECKED_CAST")
        override fun <TReply> call(
            msg: ByteArray,
            requestId: ByteArray
        ): CompletableFuture<TReply> {
            if (closed) return completedExceptionally(ConnectionClosedException())
            log.trace(Supplier<Any> { "Sending outbound message $msg in $ctx" })
            val ret = CompletableFuture<TReply>()
            val requestIdS = requestId.toHex()
            requests[requestIdS] = curTime() to ret as CompletableFuture<ByteArray>
            scheduler.schedule({
                requests.remove(requestIdS)?.second?.completeExceptionally(RpcTimeoutException())
            }, callTimeout.toMillis(), TimeUnit.MILLISECONDS)
            val baos = ByteArrayOutputStream()
            val out: CodedOutputStream = CodedOutputStream.newInstance(baos)
            out.writeByteArray(1, requestId)
            out.writeByteArray(2, msg)
            out.flush()
            ctx.writeAndFlush(baos.toByteArray().toByteBuf())
            return ret
        }
    }
}