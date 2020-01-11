package io.libp2p.simulate.discovery

import java.lang.Integer.min
import java.util.LinkedList

class Bucket(private val maxSize: Int,
             private val payload: LinkedList<Enr> = LinkedList()):List<Enr> by payload {
    fun put(enr: Enr) {
        if (contains(enr)) {
            payload.remove(enr)
        }
        if (size == maxSize) {
            payload.removeLast()
        }
        payload.add(enr)
    }
}

class KademliaTable(
    private val home: Enr,
    private val bucketSize: Int,
    private val numberBuckets: Int,
    private val distanceDivisor: Int,
    private val bootNodes: Collection<Enr> = ArrayList()
) {
    private val buckets: MutableMap<Int, Bucket> = HashMap()

    init {
        bootNodes.forEach { put(it) }
    }

    fun put(enr: Enr) {
        if (home == enr) {
            return
        }
        val distance = home.simTo(enr, distanceDivisor)
        buckets.computeIfAbsent(distance) { Bucket(bucketSize) }.put(enr)
    }

    fun find(startBucket: Int, limit: Int = bucketSize): List<Enr> {
        var total = 0
        var currentBucket = startBucket
        val result: MutableList<Enr> = ArrayList()
        while (total < limit && currentBucket <= numberBuckets) {
            buckets[currentBucket]?.let {
                val needed = min(limit - result.size, it.size)
                for (i in 0 until needed) {
                    result.add(it[i])
                    total++
                }
            }
            currentBucket++
        }
        return result
    }

    fun findStrict(bucket: Int): List<Enr> {
        return ArrayList<Enr>().apply {
            buckets[bucket]?.let {
                this.addAll(it)
            }
        }
    }
}