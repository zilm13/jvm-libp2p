package io.libp2p.simulate.discovery

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.UnknownFieldSet
import java.io.ByteArrayOutputStream

/**
 * Discovery message type
 */
interface Message {
    fun getBytes(): ByteArray

    companion object {
        fun fromBytes(bytes: ByteArray): Message {
            val inputStream: CodedInputStream = CodedInputStream.newInstance(bytes)
            val fields: UnknownFieldSet = UnknownFieldSet.parseFrom(inputStream)
            val type = fields.getField(1).varintList[0].toInt()
            return when (type) {
                FindNodeMessage.getType() -> FindNodeMessage(bytes)
                NodesMessage.getType() -> NodesMessage(bytes)
                else -> throw RuntimeException("Message type $this is not supported")
            }
        }
    }
}

data class FindNodeMessage(val distance: Int) : Message {
    constructor(bytes: ByteArray) : this(parseBytes(bytes))

    override fun getBytes(): ByteArray {
        val baos = ByteArrayOutputStream()
        val out: CodedOutputStream = CodedOutputStream.newInstance(baos)
        out.writeInt32(1, getType())
        out.writeInt32(2, distance)
        out.flush()
        return baos.toByteArray()
    }

    override fun toString(): String {
        return "FindNodeMessage(distance=$distance)"
    }

    companion object {
        fun getType(): Int = 1
        private fun parseBytes(bytes: ByteArray): Int {
            val inputStream: CodedInputStream = CodedInputStream.newInstance(bytes)
            val fields: UnknownFieldSet = UnknownFieldSet.parseFrom(inputStream)
            val type = fields.getField(1).varintList[0].toInt()
            assert(getType() == type)
            val distance = fields.getField(2).varintList[0].toInt()
            return distance
        }
    }
}

data class NodesMessage(val nodes: Collection<Enr>) : Message {
    constructor(bytes: ByteArray) : this(parseBytes(bytes))

    override fun getBytes(): ByteArray {
        val baos = ByteArrayOutputStream()
        val out: CodedOutputStream = CodedOutputStream.newInstance(baos)
        out.writeInt32(1, getType())
        var index = 0
        nodes.forEach {
            out.writeByteArray(index + 2, it.getBytes())
            index++
        }
        out.flush()
        return baos.toByteArray()
    }

    override fun toString(): String {
        return "NodesMessage(nodes=$nodes)"
    }


    companion object {
        fun getType(): Int = 2
        private fun parseBytes(bytes: ByteArray): List<Enr> {
            val inputStream: CodedInputStream = CodedInputStream.newInstance(bytes)
            val fields: UnknownFieldSet = UnknownFieldSet.parseFrom(inputStream)
            val type = fields.getField(1).varintList[0].toInt()
            assert(getType() == type)
            val nodes: MutableList<Enr> = ArrayList()
            var i = 2
            while (fields.hasField(i)) {
                val nodesBytes = fields.getField(i).lengthDelimitedList[0].toByteArray()
                nodes.add(Enr(nodesBytes))
                i++
            }
            return nodes
        }
    }
}