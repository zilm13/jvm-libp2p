package io.libp2p.simulate

interface RandomValue {

    fun next(): Double

    companion object {
        fun const(constVal: Double) = object : RandomValue {
            override fun next() = constVal
        }
    }
}