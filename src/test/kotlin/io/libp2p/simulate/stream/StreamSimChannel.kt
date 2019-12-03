package io.libp2p.simulate.stream

import io.libp2p.etc.types.lazyVar
import io.libp2p.etc.types.toVoidCompletableFuture
import io.libp2p.simulate.util.GeneralSizeEstimator
import io.libp2p.simulate.util.MessageDelayer
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelId
import io.netty.channel.embedded.EmbeddedChannel
import org.apache.logging.log4j.LogManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class StreamSimChannel(id: String, vararg handlers: ChannelHandler?) :
    EmbeddedChannel(
        SimChannelId(id),
        *handlers
    ) {

    var link: StreamSimChannel? = null
    var executor: ScheduledExecutorService by lazyVar { Executors.newSingleThreadScheduledExecutor() }
    var msgSizeEstimator = GeneralSizeEstimator
    var msgDelayer: MessageDelayer = { 0L }
    var msgSizeHandler: (Int) -> Unit = {}

    @Synchronized
    fun connect(other: StreamSimChannel) {
        link = other
        outboundMessages().forEach(this::send)
    }

    @Synchronized
    override fun handleOutboundMessage(msg: Any?) {
        if (link != null) {
            send(msg!!)
        } else {
            super.handleOutboundMessage(msg)
        }
    }

    private fun send(msg: Any) {
        val size = msgSizeEstimator(msg)
        val delay = msgDelayer(size)

        val sendNow: () -> Unit = {
            link!!.writeInbound(msg)
            msgSizeHandler(size)
        }
        if (delay > 0) {
            link!!.executor.schedule(sendNow, delay, TimeUnit.MILLISECONDS)
        } else {
            link!!.executor.execute(sendNow)
        }
    }

    companion object {
        fun interConnect(ch1: StreamSimChannel, ch2: StreamSimChannel): Connection {
            ch1.connect(ch2)
            ch2.connect(ch1)
            return Connection(ch1, ch2)
        }

        private val logger = LogManager.getLogger(StreamSimChannel::class.java)
    }

    class Connection(val ch1: StreamSimChannel, val ch2: StreamSimChannel) {
        fun disconnect(): CompletableFuture<Unit> {
            return CompletableFuture.allOf(
                ch1.close().toVoidCompletableFuture(),
                ch2.close().toVoidCompletableFuture()
            ).thenApply { Unit }
        }
    }
}

private class SimChannelId(val id: String) : ChannelId {
    override fun compareTo(other: ChannelId) = asLongText().compareTo(other.asLongText())
    override fun asShortText() = id
    override fun asLongText() = id
}

