package io.libp2p.simulate

interface Topology {

    fun connect(peers: List<SimPeer>): List<SimConnection>
}