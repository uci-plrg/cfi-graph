package edu.uci.plrg.cfi.x86.graph.util;

import java.util.EnumSet;
import java.util.Set;

import edu.uci.plrg.cfi.x86.graph.data.graph.Edge;
import edu.uci.plrg.cfi.x86.graph.data.graph.EdgeType;
import edu.uci.plrg.cfi.x86.graph.data.graph.Node;
import edu.uci.plrg.cfi.x86.graph.data.graph.OrdinalEdgeList;

public class EdgeTypes {
	static final StringBuilder buffer = new StringBuilder();

	public static EdgeTypes getIncoming(Node<?> node) {
		EdgeTypes edgeTypes = new EdgeTypes();
		OrdinalEdgeList<?> edges = node.getIncomingEdges();
		try {
			for (Edge<?> edge : edges) {
				edgeTypes.add(edge.getEdgeType());
			}
		} finally {
			edges.release();
		}
		return edgeTypes;
	}

	public static EdgeTypes getOutgoing(Node<?> node) {
		EdgeTypes edgeTypes = new EdgeTypes();
		OrdinalEdgeList<?> edges = node.getOutgoingEdges();
		try {
			for (Edge<?> edge : edges) {
				edgeTypes.add(edge.getEdgeType());
			}
		} finally {
			edges.release();
		}
		return edgeTypes;
	}

	private final Set<EdgeType> edgeTypes = EnumSet.noneOf(EdgeType.class);

	public void add(EdgeType type) {
		edgeTypes.add(type);
	}

	@Override
	public String toString() {
		synchronized (buffer) {
			buffer.setLength(0);
			buffer.append("{");
			for (EdgeType type : edgeTypes) {
				buffer.append(type.code);
				buffer.append(",");
			}
			if (!edgeTypes.isEmpty())
				buffer.setLength(buffer.length() - 1);
			buffer.append("}");
			return buffer.toString();
		}
	}
}
