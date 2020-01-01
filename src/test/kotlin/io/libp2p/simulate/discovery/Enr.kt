package io.libp2p.simulate.discovery

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.UnknownFieldSet
import io.libp2p.core.PeerId
import io.libp2p.core.multiformats.Multiaddr
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import kotlin.experimental.xor

/**
 * Ethereum Node Record
 */
data class Enr(val addr: Multiaddr, val id: PeerId) {
    private constructor(pair: Pair<Multiaddr, PeerId>) : this(pair.first, pair.second)
    constructor(bytes: ByteArray) : this(fromBytes(bytes))

    fun getBytes(): ByteArray {
        val baos = ByteArrayOutputStream()
        val out: CodedOutputStream = CodedOutputStream.newInstance(baos)
        out.writeByteArray(1, addr.getBytes())
        out.writeByteArray(2, id.bytes)
        out.flush()
        return baos.toByteArray()
    }

    companion object {
        private fun fromBytes(bytes: ByteArray): Pair<Multiaddr, PeerId> {
            val inputStream: CodedInputStream = CodedInputStream.newInstance(bytes)
            val fields: UnknownFieldSet = UnknownFieldSet.parseFrom(inputStream)
            val addrBytes = fields.getField(1).lengthDelimitedList[0].toByteArray()
            val idBytes = fields.getField(2).lengthDelimitedList[0].toByteArray()
            return Pair(Multiaddr(addrBytes), PeerId(idBytes))
        }
    }
}

/**
 * The 'distance' between this node and other is the bitwise XOR of the IDs, taken as the number.
 *
 * <p>distance(n₁, n₂) = n₁ XOR n₂
 *
 * <p>LogDistance is reverse of length of common prefix in bits (length - number of leftmost zeros
 * in XOR)
 */
fun Enr.to(other: Enr): Int {
    assert(this.id.bytes.size == other.id.bytes.size)
    val size = this.id.bytes.size
    val xorResult = ByteArray(size)
    for (i in 0 until size) {
        xorResult[i] = (this.id.bytes[i] xor other.id.bytes[i])
    }
    var logDistance: Int = Byte.SIZE_BITS * xorResult.size // 256
    for (i in xorResult.indices) {
        logDistance -= Byte.SIZE_BITS
        when (xorResult[i].toInt() and 0xff) {
            1 -> logDistance += 1
            in 2..3 -> logDistance += 2
            in 4..7 -> logDistance += 3
            in 8..15 -> logDistance += 4
            in 16..31 -> logDistance += 5
            in 32..63 -> logDistance += 6
            in 64..127 -> logDistance += 7
            in 128..255 -> logDistance += 8
        }
        if (xorResult[i] != 0.toByte()) {
            break
        }
    }
    return logDistance
}

/**
 * Same as `to` but simplified to reduce number of possible distances for simulation
 */
fun Enr.simTo(other: Enr, distanceDivisor: Int) = kotlin.math.ceil(this.to(other) / distanceDivisor.toDouble()).toInt()

class EnrTests {
    @Test
    fun testEnrTo() {
        val nodeId0 = PeerId.fromHex("0000000000000000000000000000000000000000000000000000000000000000")
        val nodeId1a = PeerId.fromHex("0000000000000000000000000000000000000000000000000000000000000001")
        val nodeId1b = PeerId.fromHex("1000000000000000000000000000000000000000000000000000000000000000")
        val nodeId1s = PeerId.fromHex("1111111111111111111111111111111111111111111111111111111111111111")
        val nodeId9s = PeerId.fromHex("9999999999999999999999999999999999999999999999999999999999999999")
        val nodeIdfs = PeerId.fromHex("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
        val some = Multiaddr("/ip4/127.0.0.1/tcp/1234")
        assertEquals(0, Enr(some, nodeId1a).to(Enr(some, nodeId1a)))
        assertEquals(1, Enr(some, nodeId0).to(Enr(some, nodeId1a)))
        assertEquals(253, Enr(some, nodeId0).to(Enr(some, nodeId1b)))
        assertEquals(253, Enr(some, nodeId0).to(Enr(some, nodeId1s)))
        assertEquals(256, Enr(some, nodeId0).to(Enr(some, nodeId9s)))
        assertEquals(256, Enr(some, nodeId0).to(Enr(some, nodeIdfs)))
        assertEquals(255, Enr(some, nodeId9s).to(Enr(some, nodeIdfs)))
    }

    @Test
    fun testEnrSimTo() {
        val nodeId0 = PeerId.fromHex("0000000000000000000000000000000000000000000000000000000000000000")
        val nodeId1a = PeerId.fromHex("0000000000000000000000000000000000000000000000000000000000000001")
        val nodeId1b = PeerId.fromHex("1000000000000000000000000000000000000000000000000000000000000000")
        val nodeId1s = PeerId.fromHex("1111111111111111111111111111111111111111111111111111111111111111")
        val nodeId9s = PeerId.fromHex("9999999999999999999999999999999999999999999999999999999999999999")
        val nodeIdfs = PeerId.fromHex("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
        val some = Multiaddr("/ip4/127.0.0.1/tcp/1234")
        val divisor: Int = 8
        assertEquals(0, Enr(some, nodeId1a).simTo(Enr(some, nodeId1a), divisor))
        assertEquals(1, Enr(some, nodeId0).simTo(Enr(some, nodeId1a), divisor))
        assertEquals(32, Enr(some, nodeId0).simTo(Enr(some, nodeId1b), divisor))
        assertEquals(32, Enr(some, nodeId0).simTo(Enr(some, nodeId1s), divisor))
        assertEquals(32, Enr(some, nodeId0).simTo(Enr(some, nodeId9s), divisor))
        assertEquals(32, Enr(some, nodeId0).simTo(Enr(some, nodeIdfs), divisor))
        assertEquals(32, Enr(some, nodeId9s).simTo(Enr(some, nodeIdfs), divisor))
    }
}