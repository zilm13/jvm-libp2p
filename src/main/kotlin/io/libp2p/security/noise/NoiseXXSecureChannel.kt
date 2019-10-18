package io.libp2p.security.noise

import com.google.protobuf.ByteString
import com.southernstorm.noise.protocol.DHState
import com.southernstorm.noise.protocol.HandshakeState
import com.southernstorm.noise.protocol.Noise
import io.libp2p.core.P2PAbstractChannel
import io.libp2p.core.PeerId
import io.libp2p.core.crypto.PrivKey
import io.libp2p.core.crypto.marshalPublicKey
import io.libp2p.core.crypto.unmarshalPublicKey
import io.libp2p.core.multistream.Mode
import io.libp2p.core.multistream.ProtocolMatcher
import io.libp2p.core.security.SecureChannel
import io.libp2p.etc.SECURE_SESSION
import io.libp2p.etc.events.SecureChannelFailed
import io.libp2p.etc.events.SecureChannelInitialized
import io.libp2p.etc.types.toByteArray
import io.libp2p.etc.types.toByteBuf
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.SimpleChannelInboundHandler
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.config.Configurator
import spipe.pb.Spipe
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

class NoiseXXSecureChannel(private val localKey: PrivKey) :
        SecureChannel {

    companion object {
        const val protocolName = "Noise_XX_25519_ChaChaPoly_SHA256"
        const val announce = "/noise/$protocolName/0.1.0"

        @JvmStatic
        var localStaticPrivateKey25519: ByteArray = ByteArray(32)

        init {
            // initialize static Noise key
            Noise.random(localStaticPrivateKey25519)
        }
    }

    private var logger: Logger
    private var loggerNameParent: String = NoiseXXSecureChannel::class.java.name + this.hashCode()

    private val handshakeHandlerName = "NoiseHandshake"

    override val announce = Companion.announce
    override val matcher = ProtocolMatcher(Mode.PREFIX, name = "/noise/$protocolName/0.1.0")

    init {
        logger = LogManager.getLogger(loggerNameParent)
        Configurator.setLevel(loggerNameParent, Level.DEBUG)
    }

    // simplified constructor
    fun initChannel(ch: P2PAbstractChannel): CompletableFuture<SecureChannel.Session> {
        return initChannel(ch, "")
    }

    override fun initChannel(
            ch: P2PAbstractChannel,
            selectedProtocol: String
    ): CompletableFuture<SecureChannel.Session> {
        val role = if (ch.isInitiator) AtomicInteger(HandshakeState.INITIATOR) else AtomicInteger(HandshakeState.RESPONDER)

        val chid = "ch=" + ch.nettyChannel.id().asShortText() + "-" + ch.nettyChannel.localAddress() + "-" + ch.nettyChannel.remoteAddress()
        logger.debug(chid)

        val ret = CompletableFuture<SecureChannel.Session>()
        val resultHandler = object : ChannelInboundHandlerAdapter() {
            override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
                when (evt) {
                    is SecureChannelInitialized -> {
                        val session = evt.session as NoiseSecureChannelSession
                        ctx.channel().attr(SECURE_SESSION).set(session)

                        ret.complete(session)
                        ctx.pipeline().remove(this)
                        ctx.pipeline().addLast(NoiseXXCodec(session.aliceCipher, session.bobCipher))

                        logger.debug("Reporting secure channel initialized")
                    }
                    is SecureChannelFailed -> {
                        logger.debug("Failed connection exception:" + evt.exception)
                        ret.completeExceptionally(evt.exception)

//                        ctx.pipeline().remove(handshakeHandlerName + chid)
                        ctx.pipeline().remove(this)

                        logger.debug("Reporting secure channel failed")
                    }
                }
                ctx.fireUserEventTriggered(evt)
//                ctx.fireChannelActive()
            }
        }
        ch.nettyChannel.pipeline().addLast(handshakeHandlerName + chid, NoiseIoHandshake(localKey, role.get(), "$chid|$loggerNameParent"))
        ch.nettyChannel.pipeline().addLast(handshakeHandlerName + chid + "ResultHandler", resultHandler)
        return ret
    }

    class NoiseIoHandshake(private val localKey: PrivKey, private val role: Int, loggerString: String) : SimpleChannelInboundHandler<ByteBuf>() {

        private var loggerName: String
        private var logger2: Logger

        private val handshakestate: HandshakeState = HandshakeState(protocolName, role)
        private val localNoiseState: DHState = Noise.createDH("25519")

        private var sentNoiseKeyPayload = false

        private val instancePayload = ByteArray(65535)
        private var instancePayloadLength = 0

        init {
            val roleString = if (role == HandshakeState.INITIATOR) "INIT" else "RESP"
            loggerName = "$roleString|$loggerString"
            loggerName = loggerName.replace(".", "_")

            logger2 = LogManager.getLogger(loggerName)
            Configurator.setLevel(loggerName, Level.DEBUG)

            logger2.debug("Starting handshake")

            // configure the localDHState with the private
            // which will automatically generate the corresponding public key
            localNoiseState.setPrivateKey(localStaticPrivateKey25519, 0)

            handshakestate.localKeyPair.copyFrom(localNoiseState)
            handshakestate.start()
        }

        override fun channelRead0(ctx: ChannelHandlerContext, msg1: ByteBuf) {
            val msg = msg1.toByteArray()

            channelRegistered(ctx)

            if (flagRemoteVerified && !flagRemoteVerifiedPassed) {
                logger2.error("Responder verification of Remote peer id has failed")
                ctx.fireUserEventTriggered(SecureChannelFailed(Exception("Responder verification of Remote peer id has failed")))
                return
            }

            // we always read from the wire when it's the next action to take
            // capture any payloads
            if (handshakestate.action == HandshakeState.READ_MESSAGE) {
                val payload = ByteArray(65535)
                var payloadLength : Int
                try {
                    logger2.debug("Noise handshake READ_MESSAGE")
                    val length = msg1.readShort().toInt()
                    payloadLength = handshakestate.readMessage(msg, 2, length, payload, 0)
                } catch (e: Exception) {
                    logger2.debug(loggerName + "Exception e:" + e.toString())
                    e.printStackTrace(System.err)
                    ctx.fireUserEventTriggered(SecureChannelFailed(e))
                    return
                }
                if (payloadLength > 0 && instancePayloadLength == 0) {
                    logger2.debug("Read payload")
                    // currently, only allow storing a single payload for verification (this should maybe be changed to a queue)
                    payload.copyInto(instancePayload, 0, 0, payloadLength)
                    instancePayloadLength = payloadLength
                }

                val remotePublicKeyState: DHState = handshakestate.remotePublicKey
                val remotePublicKey = ByteArray(remotePublicKeyState.publicKeyLength)
                remotePublicKeyState.getPublicKey(remotePublicKey, 0)

                // verify the signature of the remote's noise static public key once the remote public key has been provided by the XX protocol
                if (!flagRemoteVerified && !Arrays.equals(remotePublicKey, ByteArray(remotePublicKeyState.publicKeyLength))) {
                    var check = verifyPayload(ctx, instancePayload, instancePayloadLength, remotePublicKey)
                    if (!check) return
                }
            }

            // after reading messages and setting up state, write next message onto the wire
            if (role == HandshakeState.RESPONDER && handshakestate.action == HandshakeState.WRITE_MESSAGE) {
                sendNoiseStaticKeyAsPayload(ctx)
            } else if (handshakestate.action == HandshakeState.WRITE_MESSAGE) {
                val sndmessage = ByteArray(65535)
                logger2.debug("Noise handshake WRITE_MESSAGE")
                val sndmessageLength = handshakestate.writeMessage(sndmessage, 0, null, 0, 0)
                val compositeBuf = Unpooled.wrappedBuffer(
                        Unpooled.buffer().writeShort(sndmessageLength),
                    Unpooled.wrappedBuffer(sndmessage.copyOfRange(0, sndmessageLength).toByteBuf()))
                ctx.writeAndFlush(compositeBuf)
            }

            if (handshakestate.action == HandshakeState.SPLIT && flagRemoteVerifiedPassed) {
                var cipherStatePair = handshakestate.split()
                var aliceSplit = cipherStatePair.sender
                var bobSplit = cipherStatePair.receiver
                logger2.debug("Split complete")

                // put alice and bob security sessions into the context and trigger the next action
                val secureChannelInitialized = SecureChannelInitialized(
                        NoiseSecureChannelSession(
                                PeerId.fromPubKey(localKey.publicKey()),
                                PeerId.random(),
                                localKey.publicKey(),
                                aliceSplit,
                                bobSplit
                        ) as SecureChannel.Session
                )
                ctx.fireUserEventTriggered(secureChannelInitialized)
                ctx.fireChannelActive()
                ctx.channel().pipeline().remove(this)
            }
        }

        override fun channelRegistered(ctx: ChannelHandlerContext) {
            if (activated) return
            activated = true

            // even though both the alice and bob parties can have the payload ready
            // the Noise protocol only permits alice to send a packet first
            if (role == HandshakeState.INITIATOR) {
                sendNoiseStaticKeyAsPayload(ctx)
            }
        }

        /**
         * Sends the next Noise message with a payload of the identities and signatures
         * Currently does not include other data in the payload.
         */
        private fun sendNoiseStaticKeyAsPayload(ctx: ChannelHandlerContext) {
            // only send the Noise static key once
            if (sentNoiseKeyPayload) return
            sentNoiseKeyPayload = true

            // the payload consists of the identity public key, and the signature of the noise static public key
            // the actual noise static public key is sent later as part of the XX handshake

            // get identity public key
            val identityPublicKey: ByteArray = marshalPublicKey(localKey.publicKey())

            // get noise static public key signature
            val localNoisePubKey = ByteArray(localNoiseState.publicKeyLength)
            localNoiseState.getPublicKey(localNoisePubKey, 0)
            val localNoiseStaticKeySignature = localKey.sign("noise-libp2p-static-key:".toByteArray() + localNoisePubKey)

            // generate an appropriate protobuf element
            val noiseHandshakePayload =
                    Spipe.NoiseHandshakePayload.newBuilder()
                            .setLibp2PKey(ByteString.copyFrom(identityPublicKey))
                            .setNoiseStaticKeySignature(ByteString.copyFrom(localNoiseStaticKeySignature))
                            .setLibp2PData(ByteString.EMPTY)
                            .setLibp2PDataSignature(ByteString.EMPTY)
                            .build()

            // create the message with the signed payload - verification happens once the noise static key is shared
            val msgBuffer = ByteArray(65535)
            val msgLength = handshakestate.writeMessage(
                    msgBuffer,
                    0,
                    noiseHandshakePayload.toByteArray(),
                    0,
                    noiseHandshakePayload.toByteArray().size
            )

            logger2.debug("Noise handshake w payload WRITE_MESSAGE")
            // put the message frame which also contains the payload onto the wire

            val compositeBuf = Unpooled.wrappedBuffer(
                    Unpooled.buffer().writeShort(msgLength),
                    Unpooled.wrappedBuffer(msgBuffer.copyOfRange(0, msgLength).toByteBuf()))
            ctx.writeAndFlush(compositeBuf)

//            ctx.writeAndFlush(msgBuffer.copyOfRange(0, msgLength).toByteBuf())
        }

        private fun verifyPayload(
                ctx: ChannelHandlerContext,
                payload: ByteArray,
                payloadLength: Int,
                remotePublicKey: ByteArray
        ): Boolean {
            logger2.debug("Verifying noise static key payload")
            flagRemoteVerified = true

            // the self-signed remote pubkey and signature would be retrieved from the first Noise payload
            val inp = Spipe.NoiseHandshakePayload.parseFrom(payload.copyOfRange(0, payloadLength))
            // validate the signature
            val data: ByteArray = inp.libp2PKey.toByteArray()
            val remotePubKeyFromMessage = unmarshalPublicKey(data)
            val remoteSignatureFromMessage = inp.noiseStaticKeySignature.toByteArray()

            flagRemoteVerifiedPassed = remotePubKeyFromMessage.verify("noise-libp2p-static-key:".toByteArray() + remotePublicKey, remoteSignatureFromMessage)

            if (flagRemoteVerifiedPassed) {
                logger2.debug("Remote verification passed")
                return true
            } else {
                logger2.error("Remote verification failed")
                ctx.fireUserEventTriggered(SecureChannelFailed(Exception("Responder verification of Remote peer id has failed")))
                ctx.channel().pipeline().remove(this)
                return false;
            }
        }

        override fun channelActive(ctx: ChannelHandlerContext) {
            channelRegistered(ctx)
        }

        private var activated = false
        private var flagRemoteVerified = false
        private var flagRemoteVerifiedPassed = false
    }
}
