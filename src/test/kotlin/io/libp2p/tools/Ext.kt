package io.libp2p.tools

fun msec(info: String = "Starting...", f: () -> Unit) {
    val s = System.nanoTime()
    try {
        print("$info ")
        System.out.flush()
        f()
    } finally {
        val d = System.nanoTime() - s

        val durS = when (d) {
            in 0..10 * 1_000L -> "" + d + " ns"
            in 10 * 1000..10 * 1_000_000L -> "" + (d / 1_000) + " us"
            in 10 * 1_000_000L .. 1 * 1_000_000_000L -> "" + (d / 1_000_000) + " ms"
            else -> "%.3f".format((d / 1_000_000) / 1000.0) + " s"
        }
        println("done in $durS")
    }
}

operator fun <T> List<T>.get(subIndexes: IntRange) =  subList(subIndexes.first, subIndexes.last + 1)
