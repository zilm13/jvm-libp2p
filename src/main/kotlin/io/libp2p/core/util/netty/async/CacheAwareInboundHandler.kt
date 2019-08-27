package io.libp2p.core.util.netty.async

import io.libp2p.core.InternalErrorException
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandler
import io.netty.channel.ChannelPipeline
import java.util.Collections.newSetFromMap
import java.util.Collections.synchronizedSet
import java.util.IdentityHashMap

open class CacheAwareInboundHandler(val orig: ChannelHandler) : ChannelHandler {

    final override fun handlerAdded(ctx: ChannelHandlerContext) {
        handleCachingHandler(ctx)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {}
    override fun handlerRemoved(ctx: ChannelHandlerContext?) {}

    open fun handlerAdded0(ctx: ChannelHandlerContext) {}

    fun handleCachingHandler(ctx: ChannelHandlerContext) {
        var h: CachingHandler? = null
        ctx.pipeline().toMap().values.reversed().drop(1).forEach {
            when(it) {
                is CachingHandler -> {
                    if (h != null) {
//                        throw InternalErrorException("Duplicate CachingHandler instance on pipeline: " + ctx.pipeline())
                    } else {
                        h = it
                    }
                }
//                is ChannelInboundHandler -> {
//                    if (h == null) {
//                        throw InternalErrorException("ChannelInboundHandler found after CachingHandler: $it, ${ctx.pipeline()}")
//                    }
//                }
            }
        }
        ctx.pipeline().replace(this, null, orig)
        h?.newInboundHandlerAdded() ?: throw InternalErrorException("No CachingHandler found on pipeline: ${ctx.pipeline()}")
    }

    companion object {
        val recursives = synchronizedSet(newSetFromMap(IdentityHashMap<ChannelPipeline, Boolean>()))

        fun addLast(pipeline: ChannelPipeline, name: String?, handler: ChannelHandler) {
//            if (!pipeline.channel().eventLoop().inEventLoop()) {
//                pipeline.channel().eventLoop().execute { addLastOnLoop(pipeline, name, handler) }
//            } else {
                addLastOnLoop(pipeline, name, handler)
//            }
        }
        fun addLastOnLoop(pipeline: ChannelPipeline, name: String?, handler: ChannelHandler) {
            val firstEntrance = recursives.add(pipeline)
            pipeline.addLast(name, handler)
            if (firstEntrance) {
                try {
                    var h: CachingHandler? = null
                    pipeline.toMap().values.reversed().drop(1).forEach {
                        when (it) {
                            is CachingHandler -> {
                                if (h != null) {
//                        throw InternalErrorException("Duplicate CachingHandler instance on pipeline: " + ctx.pipeline())
                                } else {
                                    h = it
                                }
                            }
                        }
                    }
                    h?.newInboundHandlerAdded()
                        ?: throw InternalErrorException("No CachingHandler found on pipeline: $pipeline")
                }finally {
                    recursives.remove(pipeline)
                }
            }
        }
    }
}

fun ChannelHandler.toCacheAware(): ChannelHandler =
    when(this) {
        is ChannelInboundHandler -> CacheAwareInboundHandler(this)
        else -> this
    }

//fun ChannelHandler.toCacheAware(): ChannelHandler =
//    when(this) {
//        is ChannelInboundHandler ->
//            when (this) {
//                is ChannelOutboundHandler -> CacheAwareInboundOutboundHandlerWrapper(this)
//                else -> CacheAwareInboundHandlerWrapper(this)
//            }
//        else -> this
//    }

//open class CacheAwareInboundHandlerWrapper(val delegate: ChannelInboundHandler): CacheAwareInboundHandler() {
//    override fun handlerAdded0(ctx: ChannelHandlerContext) {
//        delegate.handlerAdded(ctx)
//    }
//
//    override fun channelInactive(ctx: ChannelHandlerContext?) {
//        delegate.channelInactive(ctx)
//    }
//
//    override fun userEventTriggered(ctx: ChannelHandlerContext?, evt: Any?) {
//        delegate.userEventTriggered(ctx, evt)
//    }
//
//    override fun channelWritabilityChanged(ctx: ChannelHandlerContext?) {
//        delegate.channelWritabilityChanged(ctx)
//    }
//
//    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
//        delegate.channelRead(ctx, msg)
//    }
//
//    override fun channelUnregistered(ctx: ChannelHandlerContext?) {
//        delegate.channelUnregistered(ctx)
//    }
//
//    override fun channelActive(ctx: ChannelHandlerContext?) {
//        delegate.channelActive(ctx)
//    }
//
//    override fun channelRegistered(ctx: ChannelHandlerContext?) {
//        delegate.channelRegistered(ctx)
//    }
//
//    override fun channelReadComplete(ctx: ChannelHandlerContext?) {
//        delegate.channelReadComplete(ctx)
//    }
//
//    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
//        delegate.exceptionCaught(ctx, cause)
//    }
//
//    override fun handlerRemoved(ctx: ChannelHandlerContext?) {
//        delegate.handlerRemoved(ctx)
//    }
//}
//
//class CacheAwareInboundOutboundHandlerWrapper(delegate: ChannelInboundHandler) :
//    CacheAwareInboundHandlerWrapper(delegate), ChannelOutboundHandler {
//    val delegateOut: ChannelOutboundHandler = delegate as ChannelOutboundHandler
//
//    override fun deregister(ctx: ChannelHandlerContext?, promise: ChannelPromise?) {
//        delegateOut.deregister(ctx, promise)
//    }
//
//    override fun disconnect(ctx: ChannelHandlerContext?, promise: ChannelPromise?) {
//        delegateOut.disconnect(ctx, promise)
//    }
//
//    override fun write(ctx: ChannelHandlerContext?, msg: Any?, promise: ChannelPromise?) {
//        delegateOut.write(ctx, msg, promise)
//    }
//
//    override fun flush(ctx: ChannelHandlerContext?) {
//        delegateOut.flush(ctx)
//    }
//
//    override fun connect(
//        ctx: ChannelHandlerContext?,
//        remoteAddress: SocketAddress?,
//        localAddress: SocketAddress?,
//        promise: ChannelPromise?
//    ) {
//        delegateOut.connect(ctx, remoteAddress, localAddress, promise)
//    }
//
//    override fun bind(ctx: ChannelHandlerContext?, localAddress: SocketAddress?, promise: ChannelPromise?) {
//        delegateOut.bind(ctx, localAddress, promise)
//    }
//
//    override fun close(ctx: ChannelHandlerContext?, promise: ChannelPromise?) {
//        delegateOut.close(ctx, promise)
//    }
//
//    override fun read(ctx: ChannelHandlerContext?) {
//        delegateOut.read(ctx)
//    }
//}