package edu.uci.plrg.cfi.x86.graph.data.graph.transform;

import java.util.Comparator;

import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.x86.graph.data.graph.EdgeType;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.writer.ModuleDataWriter;

public class RawEdge implements ModuleDataWriter.Edge {

	static class EdgeIndexSorter implements Comparator<RawEdge> {
		static EdgeIndexSorter INSTANCE = new EdgeIndexSorter();

		@Override
		public int compare(RawEdge first, RawEdge second) {
			return first.edgeIndex - second.edgeIndex;
		}
	}

	private int edgeIndex;
	public final IndexedModuleNode fromNode;
	public final IndexedModuleNode toNode;
	public final EdgeType type;
	public final int ordinal;

	RawEdge(IndexedModuleNode fromNode, IndexedModuleNode toNode, EdgeType type, int ordinal) {
		this.fromNode = fromNode;
		this.toNode = toNode;
		this.type = type;
		this.ordinal = ordinal;
		
		if (type == EdgeType.UNEXPECTED_RETURN)
			Log.log("Loaded UR %s", this);
	}

	@Override
	public IndexedModuleNode getFromNode() {
		return fromNode;
	}

	@Override
	public IndexedModuleNode getToNode() {
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
	public boolean isModuleEntry() {
		return fromNode.module != toNode.module; // EDGE: verify
	}
	
	@Override
	public boolean isModuleExit() {
		return fromNode.module != toNode.module;
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
