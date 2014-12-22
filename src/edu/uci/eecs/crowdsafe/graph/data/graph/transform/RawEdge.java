package edu.uci.eecs.crowdsafe.graph.data.graph.transform;

import java.util.Comparator;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.writer.ClusterDataWriter;

public class RawEdge implements ClusterDataWriter.Edge<IndexedClusterNode> {

	static class EdgeIndexSorter implements Comparator<RawEdge> {
		static EdgeIndexSorter INSTANCE = new EdgeIndexSorter();

		@Override
		public int compare(RawEdge first, RawEdge second) {
			return first.edgeIndex - second.edgeIndex;
		}
	}

	private int edgeIndex;
	public final IndexedClusterNode fromNode;
	public final IndexedClusterNode toNode;
	public final EdgeType type;
	public final int ordinal;

	RawEdge(IndexedClusterNode fromNode, IndexedClusterNode toNode, EdgeType type, int ordinal) {
		this.fromNode = fromNode;
		this.toNode = toNode;
		this.type = type;
		this.ordinal = ordinal;
		
		if (type == EdgeType.UNEXPECTED_RETURN)
			Log.log("Loaded UR %s", this);
	}

	@Override
	public IndexedClusterNode getFromNode() {
		return fromNode;
	}

	@Override
	public IndexedClusterNode getToNode() {
		return toNode;
	}

	@Override
	public EdgeType getEdgeType() {
		return type;
	}

	@Override
	public int getOrdinal() {
		return ordinal;
	}
	
	@Override
	public boolean isClusterEntry() {
		return fromNode.cluster != toNode.cluster; // EDGE: verify
	}
	
	@Override
	public boolean isClusterExit() {
		return fromNode.cluster != toNode.cluster;
	}

	public int getEdgeIndex() {
		return edgeIndex;
	}

	public void setEdgeIndex(int edgeIndex) {
		this.edgeIndex = edgeIndex;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + fromNode.hashCode();
		result = prime * result + ordinal;
		result = prime * result + toNode.hashCode();
		result = prime * result + type.hashCode();
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RawEdge other = (RawEdge) obj;
		if (!fromNode.equals(other.fromNode))
			return false;
		if (ordinal != other.ordinal)
			return false;
		if (!toNode.equals(other.toNode))
			return false;
		if (type != other.type)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return String.format("%s--%s(%d)-->%s", fromNode, type.code, ordinal, toNode);
	}
}
