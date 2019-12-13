package io.libp2p.simulate.topology

import io.libp2p.simulate.ImmutableNetworkImpl
import io.libp2p.simulate.Network
import io.libp2p.simulate.SimPeer
import io.libp2p.simulate.Topology
import org.jgrapht.generate.RandomRegularGraphGenerator
import org.jgrapht.graph.DefaultUndirectedGraph
import java.util.Random

data class RandomNPeers(val peersCount: Int = 10) : Topology {
    override var random = Random()

    override fun connect(peers: List<SimPeer>): Network {
        val peersVertex = peers.iterator()
        val graph = DefaultUndirectedGraph(peersVertex::next, { Any() }, false)
        RandomRegularGraphGenerator<SimPeer, Any>(peers.size, peersCount, random).generateGraph(graph)
        val conns = peers
            .flatMap { graph.incomingEdgesOf(it) }
            .distinct()
            .map { graph.getEdgeSource(it).connect(graph.getEdgeTarget(it)) }
            .map { it.get() }
        return ImmutableNetworkImpl(conns)
    }
}