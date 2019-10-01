package io.libp2p.core

import io.netty.channel.ChannelHandler

interface P2PChannel {
    val isInitiator: Boolean

    /**
     * Inserts [ChannelHandler]s at the last position of this pipeline.
     */
    fun pushHandler(vararg handlers: ChannelHandler)

    /**
     * Appends a [ChannelHandler] at the last position of this pipeline.
     */
    fun pushHandler(name: String, handler: ChannelHandler)

    /**
     * Inserts a [ChannelHandler] before an existing handler of this
     * pipeline.
     */
    fun addHandlerBefore(baseName: String, name: String, handler: ChannelHandler)
}