package io.libp2p.tools

import com.google.common.util.concurrent.ThreadFactoryBuilder
import io.libp2p.transport.implementation.ConnectionOverNetty
import io.libp2p.etc.CONNECTION
import io.libp2p.etc.types.lazyVar
import io.libp2p.etc.util.netty.nettyInitializer
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelId
import io.netty.channel.embedded.EmbeddedChannel
import org.apache.logging.log4j.LogManager
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

private val threadFactory = ThreadFactoryBuilder().setDaemon(true).setNameFormat("TestChannel-interconnect-executor-%d").build()

class TestChannelId(val id: String) : ChannelId {
    override fun compareTo(other: ChannelId) = asLongText().compareTo(other.asLongText())
    override fun asShortText() = id
    override fun asLongText() = id
}

class TestChannel(id: String = "test", initiator: Boolean, vararg handlers: ChannelHandler?) :
    EmbeddedChannel(
        TestChannelId(id),
        nettyInitializer {
            it.attr(CONNECTION).set(
                ConnectionOverNetty(
                    it,
                    NullTransport(),
                    initiator
                )
            )
        },
        *handlers
    ) {

    var link: TestChannel? = null
    val sentMsgCount = AtomicLong()
    var executor: Executor by lazyVar {
        Executors.newSingleThreadExecutor(threadFactory)
    }

    @Synchronized
    fun connect(other: TestChannel) {
        link = other
        outboundMessages().forEach(this::send)
    }

    @Synchronized
    override fun handleOutboundMessage(msg: Any?) {
        super.handleOutboundMessage(msg)
        if (link != null) {
            send(msg!!)
        }
    }

    fun send(msg: Any) {
        link!!.executor.execute {
            sentMsgCount.incrementAndGet()
            link!!.writeInbound(msg)
        }
    }

    companion object {
        fun interConnect(ch1: TestChannel, ch2: TestChannel): TestConnection {
            ch1.connect(ch2)
            ch2.connect(ch1)
            return TestConnection(ch1, ch2)
        }

        private val logger = LogManager.getLogger(TestChannel::class.java)
    }

    class TestConnection(val ch1: TestChannel, val ch2: TestChannel) {
        fun getMessageCount() = ch1.sentMsgCount.get() + ch2.sentMsgCount.get()
        fun disconnect() {
            ch1.close()
            ch2.close()
        }
    }
}