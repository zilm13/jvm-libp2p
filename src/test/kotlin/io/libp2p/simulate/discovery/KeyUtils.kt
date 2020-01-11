package io.libp2p.simulate.discovery

import io.libp2p.core.crypto.KEY_TYPE
import io.libp2p.core.crypto.PrivKey
import io.libp2p.core.crypto.generateKeyPair

class KeyUtils {
    companion object {
        val pubkeySize = 32

        fun genPrivKey(): PrivKey {
            return generateKeyPair(KEY_TYPE.SECP256K1).first
        }

        fun privToPubCompressed(privKey: PrivKey): ByteArray {
            val pubKey = privKey.publicKey().raw()
            return when (pubKey.size) {
                pubkeySize -> pubKey
                in 0 until pubkeySize -> {
                    val res = ByteArray(pubkeySize)
                    System.arraycopy(pubKey, 0, res, pubkeySize - pubKey.size, pubKey.size)
                    res
                }
                in (pubkeySize + 1)..Int.MAX_VALUE -> pubKey.takeLast(pubkeySize).toByteArray()
                else -> throw RuntimeException("Not expected")
            }
        }
    }
}