package io.libp2p.core.security.secio

import io.libp2p.core.crypto.KEY_TYPE
import io.libp2p.core.crypto.generateKeyPair
import io.libp2p.core.multistream.Negotiator
import io.libp2p.core.multistream.ProtocolSelect
import io.libp2p.core.types.addLastX
import io.libp2p.core.types.toByteArray
import io.libp2p.core.types.toByteBuf
import io.libp2p.core.util.netty.async.CachingHandler
import io.libp2p.tools.TestChannel
import io.libp2p.tools.TestChannel.Companion.interConnect
import io.libp2p.tools.TestHandler
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.util.ResourceLeakDetector
import org.apache.logging.log4j.LogManager
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Created by Anton Nashatyrev on 19.06.2019.
 */

class SecIoSecureChannelTest {

    init {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID)
    }

    @Test
    fun test1() {
        val (privKey1, pubKey1) = generateKeyPair(KEY_TYPE.ECDSA)
        val (privKey2, pubKey2) = generateKeyPair(KEY_TYPE.ECDSA)

        var rec1: String? = null
        var rec2: String? = null
        val latch = CountDownLatch(2)

        val protocolSelect1 = ProtocolSelect(listOf(SecIoSecureChannel(privKey1)))
        protocolSelect1.selectedFuture.thenAccept {
        }
        val eCh1 = TestChannel("#1", true, LoggingHandler("#1", LogLevel.ERROR),
            Negotiator.createInitializer("/secio/1.0.0"),
            protocolSelect1,
            addLastWhenActive(object : TestHandler("1") {
                override fun channelActive(ctx: ChannelHandlerContext) {
                    super.channelActive(ctx)
                    ctx.writeAndFlush("Hello World from $name".toByteArray().toByteBuf())
                }

                override fun channelWritabilityChanged(ctx: ChannelHandlerContext?) {
                    super.channelWritabilityChanged(ctx)
                }

                override fun channelRead(ctx: ChannelHandlerContext, msg: Any?) {
                    msg as ByteBuf
                    rec1 = msg.toByteArray().toString(StandardCharsets.UTF_8)
                    logger.debug("==$name== read: $rec1")
                    latch.countDown()
                }
            }))

        val eCh2 = TestChannel("#2", false,
            LoggingHandler("#2", LogLevel.ERROR),
            Negotiator.createInitializer("/secio/1.0.0"),
            ProtocolSelect(listOf(SecIoSecureChannel(privKey2))),
            addLastWhenActive(object : TestHandler("2") {
                override fun channelActive(ctx: ChannelHandlerContext) {
                    super.channelActive(ctx)
                    ctx.writeAndFlush("Hello World from $name".toByteArray().toByteBuf())
                }

                override fun channelRead(ctx: ChannelHandlerContext, msg: Any?) {
                    msg as ByteBuf
                    rec2 = msg.toByteArray().toString(StandardCharsets.UTF_8)
                    logger.debug("==$name== read: $rec2")
                    latch.countDown()
                }
            }))

        interConnect(eCh1, eCh2)

        latch.await(10, TimeUnit.SECONDS)

        Assertions.assertEquals("Hello World from 1", rec2)
        Assertions.assertEquals("Hello World from 2", rec1)

        System.gc()
        Thread.sleep(500)
        System.gc()
        Thread.sleep(500)
        System.gc()
    }

    @Test
    fun test3() {
        val eCh1 = TestChannel("#1", true, LoggingHandler("#1", LogLevel.ERROR))
        eCh1.pipeline().addLast(LoggingHandler("#2", LogLevel.ERROR))
    }
    @Test
    fun testAddLastX() {
        val (privKey1, pubKey1) = generateKeyPair(KEY_TYPE.ECDSA)
        val (privKey2, pubKey2) = generateKeyPair(KEY_TYPE.ECDSA)

        var rec1: String? = null
        var rec2: String? = null
        val latch = CountDownLatch(2)

        val eCh1 = TestChannel("#1", true, CachingHandler())
        val eCh2 = TestChannel("#2", false, CachingHandler())
        interConnect(eCh1, eCh2)

        Thread.sleep(200)
        eCh1.pipeline().addLastX(LoggingHandler("#1", LogLevel.ERROR))
        Thread.sleep(200)
        eCh1.pipeline().addLastX(Negotiator.createInitializer("/secio/1.0.0"))
        Thread.sleep(200)
        val protocolSelect1 = ProtocolSelect(listOf(SecIoSecureChannel(privKey1)))
        eCh1.pipeline().addLastX(protocolSelect1)

        Thread.sleep(200)
        eCh2.pipeline().addLastX(LoggingHandler("#2", LogLevel.ERROR))
        Thread.sleep(200)
        eCh2.pipeline().addLastX(Negotiator.createInitializer("/secio/1.0.0"))
        Thread.sleep(200)
        val protocolSelect2 = ProtocolSelect(listOf(SecIoSecureChannel(privKey2)))
        eCh2.pipeline().addLastX(protocolSelect2)

        protocolSelect1.selectedFuture.get(5, TimeUnit.SECONDS)
        println("Peer1 secio complete")
        protocolSelect2.selectedFuture.get(5, TimeUnit.SECONDS)
        println("Peer2 secio complete")

        eCh1.pipeline().addLastX(object : TestHandler("1") {
            override fun channelActive(ctx: ChannelHandlerContext) {
                super.channelActive(ctx)
                ctx.writeAndFlush("Hello World from $name".toByteArray().toByteBuf())
            }

            override fun channelWritabilityChanged(ctx: ChannelHandlerContext?) {
                super.channelWritabilityChanged(ctx)
            }

            override fun channelRead(ctx: ChannelHandlerContext, msg: Any?) {
                msg as ByteBuf
                rec1 = msg.toByteArray().toString(StandardCharsets.UTF_8)
                logger.debug("==$name== read: $rec1")
                latch.countDown()
            }
        })

        eCh1.pipeline().addLastX(object : TestHandler("2") {
            override fun channelActive(ctx: ChannelHandlerContext) {
                super.channelActive(ctx)
                ctx.writeAndFlush("Hello World from $name".toByteArray().toByteBuf())
            }

            override fun channelRead(ctx: ChannelHandlerContext, msg: Any?) {
                msg as ByteBuf
                rec2 = msg.toByteArray().toString(StandardCharsets.UTF_8)
                logger.debug("==$name== read: $rec2")
                latch.countDown()
            }
        })

        latch.await(10, TimeUnit.SECONDS)

        Assertions.assertEquals("Hello World from 1", rec2)
        Assertions.assertEquals("Hello World from 2", rec1)

        System.gc()
        Thread.sleep(500)
        System.gc()
        Thread.sleep(500)
        System.gc()
    }

    fun addLastWhenActive(h: ChannelHandler): ChannelHandler {
        return object : ChannelInboundHandlerAdapter() {
            override fun channelActive(ctx: ChannelHandlerContext) {
                ctx.pipeline().remove(this)
                ctx.pipeline().addLast(h)
            }
        }
    }

    companion object {
        private val logger = LogManager.getLogger(SecIoSecureChannelTest::class.java)
    }
}
