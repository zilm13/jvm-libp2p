package io.libp2p.core.util.netty.async

import io.netty.channel.AbstractChannel
import io.netty.channel.Channel
import io.netty.channel.ChannelPipeline

class MyChannelPipeline(val delegate: ChannelPipeline): ChannelPipeline by delegate {
    override fun fireChannelRegistered(): ChannelPipeline {
        println("################ Hi!")
        delegate.fireChannelRegistered()
        return this
    }
}

class MyChannel(val delegate: AbstractChannel): Channel by delegate {
    val pipeline = MyChannelPipeline(delegate.pipeline())

    override fun pipeline() = pipeline
}