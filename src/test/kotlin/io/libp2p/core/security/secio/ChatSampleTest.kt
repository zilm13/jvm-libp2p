package io.libp2p.core.security.secio

import io.libp2p.core.StreamHandler
import io.libp2p.core.crypto.KEY_TYPE
import io.libp2p.core.crypto.generateKeyPair
import io.libp2p.core.multiformats.Multiaddr
import io.libp2p.core.multistream.*
import io.libp2p.core.mux.mplex.MplexStreamMuxer
import io.libp2p.core.transport.ConnectionUpgrader
import io.libp2p.core.transport.tcp.TcpTransport
import io.libp2p.core.types.toByteArray
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import org.apache.logging.log4j.LogManager
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class ChatController : ChannelInboundHandlerAdapter() {
    var ctx: ChannelHandlerContext? = null
    var respFuture = CompletableFuture<String>()
    val activeFuture = CompletableFuture<ChatController>()
    val logger = LogManager.getLogger("ChatController")

    fun chat(str: String): CompletableFuture<String> {
        respFuture = CompletableFuture<String>()
        var strToWrite = if (str.endsWith("\n")) {
            str
        } else {
            str + "\n"
        }

        ctx!!.writeAndFlush(Unpooled.copiedBuffer(strToWrite.toByteArray()))
        return respFuture
    }

    fun read(waitForSecs: Long = 600): String? {
        return try {
            respFuture.get(waitForSecs, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            null
        }
    }

    fun chatAndGet(str: String, waitForSecs: Long = 600): String? {
        return try {
            chat(str).get(waitForSecs, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            null
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        this.ctx = ctx
        activeFuture.complete(this)
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        msg as ByteBuf
        val msgRead = String(msg.toByteArray())
        logger.info("READ MESSAGE: $msgRead")
        respFuture.complete(msgRead)//String(msg.toByteArray()))
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext?) {
        logger.info("READ COMPLETED")
    }
}

class ChatProtocol : ProtocolBinding<ChatController> {
    override val announce = "/chat/1.0.0"
    override val matcher = ProtocolMatcher(Mode.STRICT, announce)
    override fun initializer(selectedProtocol: String): ProtocolBindingInitializer<ChatController> {
        val controller = ChatController()
        return ProtocolBindingInitializer(controller, controller.activeFuture)
    }
}

class ChatSampleTest {

    /**
     * Requires running go chat sample
     * https://github.com/libp2p/go-libp2p-examples/tree/master/chat
     * > chat -sp 10000
     */
    @Test
//    @Disabled
    fun connect1() {
        val logger = LogManager.getLogger("test")

        val (privKey1, _) = generateKeyPair(KEY_TYPE.ECDSA)
        val upgrader = ConnectionUpgrader(
            listOf(SecIoSecureChannel(privKey1)),
            listOf(MplexStreamMuxer().also {
                it.intermediateFrameHandler = LoggingHandler("#3", LogLevel.INFO)
            })
        ).also {
            it.beforeSecureHandler = LoggingHandler("#1", LogLevel.INFO)
            it.afterSecureHandler = LoggingHandler("#2", LogLevel.INFO)
        }

        val tcpTransport = TcpTransport(upgrader)
        val applicationProtocols = listOf(ChatProtocol())
        val inboundStreamHandler = StreamHandler.create(Multistream.create(applicationProtocols, false))
        logger.info("Dialing...")
        val connFuture = tcpTransport.dial(Multiaddr("/ip4/127.0.0.1/tcp/10000"), inboundStreamHandler)

        val chatController = connFuture.thenCompose {
            logger.info("Connection made")
            val chatInitiator: Multistream<ChatController> = Multistream.create(applicationProtocols, true)
            val (channelHandler, completableFuture) =
                chatInitiator.initializer()
            logger.info("Creating stream")
            it.muxerSession.get().createStream(StreamHandler.create(channelHandler))
            completableFuture
        }.get()

        var s1 = chatController.read()
        logger.info("RECEIVED INITIAL: '$s1'")
        s1 = chatController.chatAndGet("Hello, my name is kotlin-libp2p. What is your name?")
        logger.info("GOT: '$s1'")
        s1 = chatController.chatAndGet("Nice to meet you, '$s1'")
        logger.info("Finally got: '$s1'")
        logger.info("Test has completed!")
    }
}