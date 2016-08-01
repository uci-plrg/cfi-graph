package edu.uci.plrg.cfi.x86.graph.util;

import java.util.EnumMap;
import java.util.Map;

import edu.uci.plrg.cfi.common.util.MutableInteger;
import edu.uci.plrg.cfi.x86.graph.data.graph.Edge;
import edu.uci.plrg.cfi.x86.graph.data.graph.EdgeType;
import edu.uci.plrg.cfi.x86.graph.data.graph.Node;
import edu.uci.plrg.cfi.x86.graph.data.graph.OrdinalEdgeList;

public class EdgeCounter {
	private final Map<EdgeType, MutableInteger> counts = new EnumMap<EdgeType, MutableInteger>(
			EdgeType.class);

	public EdgeCounter() {
		for (EdgeType type : EdgeType.values()) {
			counts.put(type, new MutableInteger(0));
		}
	}
	
	public void reset() {
		for (MutableInteger counter : counts.values())
			counter.setVal(0);
	}
	
	public int getCount(EdgeType type) {
		return counts.get(type).getVal();
	}

	public void tally(EdgeType type) {
		counts.get(type).increment();
	}

	public void tally(EdgeType type, int addition) {
		counts.get(type).add(addition);
	}

	public void tallyOutgoingEdges(Node<?> node) {
		OrdinalEdgeList<? extends Node<?>> edgeList = node.getOutgoingEdges();
		try {
			for (Edge<? extends Node<?>> edge : edgeList) {
				tally(edge.getEdgeType());
			}
		} finally {
			edgeList.release();
		}
	}
	
	public void tallyEdge(EdgeType type) {
		counts.get(type).increment();
	}
}
