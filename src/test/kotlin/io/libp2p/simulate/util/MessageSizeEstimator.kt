package io.libp2p.simulate.util

import io.netty.buffer.ByteBuf

val GeneralSizeEstimator: (Any) -> Int =  { msg ->
    when(msg) {
        is ByteBuf -> msg.readableBytes()
        else -> 0
    }
}