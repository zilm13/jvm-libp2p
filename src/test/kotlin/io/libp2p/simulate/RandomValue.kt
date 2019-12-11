package io.libp2p.simulate

import java.util.Random

interface RandomValue {

    fun next(): Double

    companion object {
        fun const(constVal: Double) = object : RandomValue {
            override fun next() = constVal
        }
        fun uniform(from: Double, to: Double, rnd: Random)= object : RandomValue {
            override fun next() = from + rnd.nextDouble() * (to - from)
        }
    }
}

interface RandomDistribution {
    fun newValue(rnd: Random): RandomValue

    companion object {
        fun const(constVal: Double) = object : RandomDistribution {
            override fun newValue(rnd: Random) = RandomValue.const(constVal)
        }
        fun uniform(from: Double, to: Double) = object : RandomDistribution {
            override fun newValue(rnd: Random) = RandomValue.uniform(from, to, rnd)
        }
    }
}