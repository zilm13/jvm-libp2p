package io.libp2p.core.util.netty.async

import io.netty.channel.AbstractChannel
import io.netty.channel.ChannelFactory

class MyChannelFactory<T : AbstractChannel>(val delegate: ChannelFactory<T>) : ChannelFactory<MyChannel> {
    override fun newChannel(): MyChannel {
        return MyChannel(delegate.newChannel())
    }
}