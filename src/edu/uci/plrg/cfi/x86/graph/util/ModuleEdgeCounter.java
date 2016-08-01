package edu.uci.plrg.cfi.x86.graph.util;

import edu.uci.plrg.cfi.x86.graph.data.graph.Edge;
import edu.uci.plrg.cfi.x86.graph.data.graph.EdgeType;
import edu.uci.plrg.cfi.x86.graph.data.graph.Node;
import edu.uci.plrg.cfi.x86.graph.data.graph.OrdinalEdgeList;

public class ModuleEdgeCounter {
	private final EdgeCounter intraCounts = new EdgeCounter();
	private final EdgeCounter interCounts = new EdgeCounter();

	public void reset() {
		intraCounts.reset();
		interCounts.reset();
	}

	public int getInterCount(EdgeType type) {
		return interCounts.getCount(type);
	}

	public int getIntraCount(EdgeType type) {
		return intraCounts.getCount(type);
	}

	public void tally(Edge<? extends Node<?>> edge) {
		if (edge.isModuleEntry() || edge.isModuleExit()) {
			interCounts.tally(edge.getEdgeType());
		} else {
			intraCounts.tally(edge.getEdgeType());
		}
	}

	public void tallyOutgoingEdges(Node<?> node) {
		OrdinalEdgeList<? extends Node<?>> edgeList = node.getOutgoingEdges();
		try {
			for (Edge<? extends Node<?>> edge : edgeList) {
				tally(edge);
			}
		} finally {
			edgeList.release();
		}
	}

	public void tallyIntraEdge(EdgeType type) {
		intraCounts.tally(type);
	}

	public void tallyInterEdge(EdgeType type) {
		interCounts.tally(type);
	}
}
