package io.libp2p.core.util.netty.async

import io.libp2p.core.InternalErrorException
import io.libp2p.core.util.netty.async.CachingHandler.EventType.Active
import io.libp2p.core.util.netty.async.CachingHandler.EventType.Exception
import io.libp2p.core.util.netty.async.CachingHandler.EventType.Inactive
import io.libp2p.core.util.netty.async.CachingHandler.EventType.Message
import io.libp2p.core.util.netty.async.CachingHandler.EventType.Registered
import io.libp2p.core.util.netty.async.CachingHandler.EventType.Unregistered
import io.libp2p.core.util.netty.async.CachingHandler.EventType.UserEvent
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandler
import java.util.concurrent.atomic.AtomicInteger

val counter = AtomicInteger()

class CachingHandler : ChannelInboundHandler {
    enum class EventType { Message, Exception, UserEvent, Active, Inactive, Registered, Unregistered }
    class Event(val type: EventType, val data: Any? = null)

    lateinit var ctx: ChannelHandlerContext
    val unhandledEvents = mutableListOf<Event>()
    var replaying = false
    val number = counter.getAndIncrement()

    fun newInboundHandlerAdded() {
        val handler = CachingHandler()
        ctx.pipeline().addLast("Caching#${handler.number}", handler)
        replayEvents()
    }

    fun replayEventsAsync() {
        ctx.channel().eventLoop().execute { replayEvents() }
    }

    fun replayEvents() {
        if (replaying) {
            throw InternalErrorException("Already replaying")
        }
        replaying = true
        var anyReads = false

        unhandledEvents.forEach {
            when (it.type) {
                Message -> {
                    ctx.fireChannelRead(it.data)
                    anyReads = true
                }
                UserEvent -> ctx.fireUserEventTriggered(it.data)
                Exception -> ctx.fireExceptionCaught(it.data as Throwable)
                Active -> ctx.fireChannelActive()
                Inactive -> ctx.fireChannelInactive()
                Registered -> ctx.fireChannelRegistered()
                Unregistered -> ctx.fireChannelUnregistered()
            }
        }
        if (anyReads) {
            ctx.fireChannelReadComplete()
        }

        ctx.pipeline().remove(this)
    }

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        this.ctx = ctx
    }
    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any?) {
        if (replaying) {
            ctx.fireUserEventTriggered(evt)
        } else {
            unhandledEvents += Event(UserEvent, evt)
        }
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (replaying) {
            ctx.fireChannelRead(msg)
        } else {
            unhandledEvents += Event(Message, msg)
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable?) {
        if (replaying) {
            ctx.fireExceptionCaught(cause)
        } else {
            unhandledEvents += Event(Exception, cause)
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        if (replaying) {
            ctx.fireChannelActive()
        } else {
            unhandledEvents += Event(Active)
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        if (replaying) {
            ctx.fireChannelInactive()
        } else {
            unhandledEvents += Event(Inactive)
        }
    }

    override fun channelRegistered(ctx: ChannelHandlerContext) {
        if (replaying) {
            ctx.fireChannelRegistered()
        } else {
            unhandledEvents += Event(Registered)
        }
    }
    override fun channelUnregistered(ctx: ChannelHandlerContext) {
        if (replaying) {
            ctx.fireChannelUnregistered()
        } else {
            unhandledEvents += Event(Unregistered)
        }
    }
    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        ctx.fireChannelReadComplete()
    }
    override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
        ctx.fireChannelWritabilityChanged()
    }
    override fun handlerRemoved(ctx: ChannelHandlerContext?) {}
}