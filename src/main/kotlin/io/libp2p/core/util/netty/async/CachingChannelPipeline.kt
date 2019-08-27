package io.libp2p.core.util.netty.async

import io.libp2p.core.types.copy
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelInboundHandler
import io.netty.channel.ChannelPipeline
import io.netty.channel.DefaultChannelPipeline
import io.netty.util.concurrent.EventExecutorGroup

class CachingChannelPipeline(channel: Channel, val delegate: RecPipeline = RecPipeline(
    channel
)
) :
    ChannelPipeline by delegate {

    override fun addLast(group: EventExecutorGroup?, name: String?, handler: ChannelHandler?): ChannelPipeline {
        delegate.addLast(group, name, handler)
        if (handler is ChannelInboundHandler) {
            channel().eventLoop().execute { replayEvents(handler) }
        }
        return this
    }

    fun replayEvents(handler: ChannelInboundHandler) {
        val ctx = context(handler)
        var anyReads = false
        val copy = delegate.unhandledEvents.copy()
        delegate.unhandledEvents.clear()

        copy.forEach {
            try {
                when (it.type) {
                    RecPipeline.EventType.Message -> {
                        handler.channelRead(ctx, it.data)
                        anyReads = true
                    }
                    RecPipeline.EventType.UserEvent -> handler.userEventTriggered(ctx, it.data)
                    RecPipeline.EventType.Exception -> handler.exceptionCaught(ctx, it.data as Throwable)
                    RecPipeline.EventType.Active -> handler.channelActive(ctx)
                    RecPipeline.EventType.Inactive -> handler.channelInactive(ctx)
                }
            } catch (e: Exception) {
                try {
                    handler.exceptionCaught(ctx, e)
                } catch (e: Exception) {
                }
            }
        }
        if (anyReads) {
            try {
                handler.channelReadComplete(ctx)
            } catch (e: Exception) {
                try {
                    handler.exceptionCaught(ctx, e)
                } catch (e: Exception) {
                }
            }
        }
    }

    override fun addLast(vararg handlers: ChannelHandler?): ChannelPipeline {
        return addLast(null as EventExecutorGroup, *handlers)
    }

    override fun addLast(name: String?, handler: ChannelHandler?): ChannelPipeline {
        return addLast(null, name, handler)
    }

    override fun addLast(group: EventExecutorGroup?, vararg handlers: ChannelHandler?): ChannelPipeline {
        handlers.forEach { addLast(group, null as String, it) }
        return this
    }
}

class RecPipeline(channel: Channel) : DefaultChannelPipeline(channel) {
    enum class EventType { Message, Exception, UserEvent, Active, Inactive }
    class Event(val type: EventType, val data: Any? = null)

    val unhandledEvents = mutableListOf<Event>()

    override fun onUnhandledInboundMessage(msg: Any?) {
        unhandledEvents += Event(
            EventType.Message,
            msg
        )
    }

    override fun onUnhandledInboundChannelReadComplete() {
    }

    override fun onUnhandledInboundUserEventTriggered(evt: Any?) {
        unhandledEvents += Event(
            EventType.UserEvent,
            evt
        )
    }

    override fun onUnhandledInboundException(cause: Throwable?) {
        unhandledEvents += Event(
            EventType.Exception,
            cause
        )
    }

    override fun onUnhandledChannelWritabilityChanged() {
    }

    override fun onUnhandledInboundChannelActive() {
        unhandledEvents += Event(EventType.Active)
    }

    override fun onUnhandledInboundChannelInactive() {
        unhandledEvents += Event(EventType.Inactive)
    }
}

