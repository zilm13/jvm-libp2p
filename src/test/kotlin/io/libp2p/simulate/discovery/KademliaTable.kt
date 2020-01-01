package io.libp2p.simulate.discovery

import java.lang.Integer.min
import java.util.LinkedList

class Bucket(private val maxSize: Int) {
    private val payload: LinkedList<Enr> = LinkedList()
    fun size(): Int = payload.size
    fun contains(enr: Enr): Boolean = payload.find { it == enr } != null
    fun get(index: Int) = payload[index]

    fun put(enr: Enr) {
        if (contains(enr)) {
            payload.remove(enr)
        }
        if (size() == maxSize) {
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
        for (node in bootNodes) {
            put(node)
        }
    }

    fun put(enr: Enr) {
        if (home == enr) {
            return
        }
        val distance = home.simTo(enr, distanceDivisor)
        if (!buckets.contains(distance)) {
            buckets[distance] = Bucket(bucketSize)
        }
        buckets[distance]!!.put(enr)
    }

    fun find(startBucket: Int, limit: Int = bucketSize): List<Enr> {
        var total = 0
        var currentBucket = startBucket
        val result: MutableList<Enr> = ArrayList()
        while (total < limit && currentBucket <= numberBuckets) {
            if (buckets.contains(currentBucket)) {
                val needed = min(limit - result.size, buckets[currentBucket]!!.size())
                for (i in 0 until needed) {
                    result.add(buckets[currentBucket]!!.get(i))
                    total++
                }
            }
            currentBucket++
        }
        return result
    }

    fun findStrict(bucket: Int): List<Enr> {
        val result: MutableList<Enr> = ArrayList()
        if (buckets.contains(bucket)) {
            for (i in 0 until buckets[bucket]!!.size()) {
                result.add(buckets[bucket]!!.get(i))
            }
        }
        return result
    }
}