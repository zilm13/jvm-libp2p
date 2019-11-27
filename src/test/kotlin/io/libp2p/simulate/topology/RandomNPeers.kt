package io.libp2p.simulate.topology

import io.libp2p.simulate.SimConnection
import io.libp2p.simulate.SimPeer
import io.libp2p.simulate.Topology
import org.jgrapht.alg.connectivity.ConnectivityInspector
import org.jgrapht.generate.RandomRegularGraphGenerator
import org.jgrapht.graph.DefaultUndirectedGraph
import java.util.Random

class RandomNPeers(val peersCount: Int = 10) : Topology {
    var random = Random()

    override fun connect(peers: List<SimPeer>): List<SimConnection> {
        val peersVertex = peers.iterator()
        val graph = DefaultUndirectedGraph(peersVertex::next, { Any() }, false)
        RandomRegularGraphGenerator<SimPeer, Any>(peers.size, peersCount, random).generateGraph(graph)
        val connectedSets = ConnectivityInspector(graph).connectedSets()
        return peers
            .flatMap { graph.incomingEdgesOf(it) }
            .distinct()
            .map { graph.getEdgeSource(it).connect(graph.getEdgeTarget(it)) }
            .map { it.get() }
    }
}