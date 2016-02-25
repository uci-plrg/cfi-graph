package edu.uci.eecs.crowdsafe.graph.data.graph.modular.loader;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.exception.InvalidGraphException;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.MutableInteger;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;

public class ModuleGraphEdgeFactory {

	private enum LookupType {
		NORMAL,
		CLUSTER_ENTRY,
		CLUSTER_EXIT;
	}

	private static final int ENTRY_BYTE_COUNT = 0x8;

	private final List<ModuleNode<?>> nodeList;
	private MutableInteger nodeListOffset = null;
	private final LittleEndianInputStream input;

	private final Map<Long, Edge<ModuleNode<?>>> existingEdges = new HashMap<Long, Edge<ModuleNode<?>>>();

	private Long previousValue = null;

	ModuleGraphEdgeFactory(List<ModuleNode<?>> nodeList, LittleEndianInputStream input) {
		this.nodeList = nodeList;
		this.input = input;
	}

	void activateSegmentedLoading(MutableInteger nodeListOffset) {
		this.nodeListOffset = nodeListOffset;
	}

	boolean ready() throws IOException {
		return input.ready(ENTRY_BYTE_COUNT);
	}

	Edge<ModuleNode<?>> createEdge() throws IOException {
		long value;
		if (previousValue == null) {
			value = input.readLong();
			Log.log("Loaded edge value 0x%x", value);
		} else {
			value = previousValue;
			previousValue = null;
		}

		Edge<ModuleNode<?>> edge = existingEdges.get(value);
		if (edge != null) {
			Log.log("Error: duplicate edge 0x%x", value);
			return edge;
		}

		int fromNodeIndex = (int) (value & 0xfffffffL);
		int toNodeIndex = (int) ((value >> 0x1cL) & 0xfffffffL);
		EdgeType type = EdgeType.values()[(int) ((value >> 0x38L) & 0xfL)];
		int ordinal = (int) ((value >> 0x3cL) & 0xfL);

		if ((type == EdgeType.GENCODE_PERM) || (type == EdgeType.GENCODE_WRITE))
			toString();

		int offset = nodeListOffset == null ? 0 : nodeListOffset.getVal();
		if ((fromNodeIndex - offset) >= nodeList.size()) {
			if ((toNodeIndex - offset) < nodeList.size()) {
				throw new InvalidGraphException("Edge (#%d)->(#%d) crosses anonymous subgraph partitions!",
						fromNodeIndex, toNodeIndex);
			}
			previousValue = value;
			return null;
		}
		ModuleNode<?> fromNode = nodeList.get(fromNodeIndex - offset);
		ModuleNode<?> toNode = nodeList.get(toNodeIndex - offset);

		// if ((fromNode.getModule().unit.isAnonymous) && (type == EdgeType.CALL_CONTINUATION))
		// throw new IllegalStateException("Anonymous edges may not be call continuations!");

		edge = new Edge<ModuleNode<?>>(fromNode, toNode, type, ordinal);
		existingEdges.put(value, edge);

		fromNode.addOutgoingEdge(edge);
		toNode.addIncomingEdge(edge);

		return edge;
	}

	void close() throws IOException {
		if (input.ready())
			Log.log("Warning: input stream %s has %d bytes remaining.", input.description, input.available());

		input.close();
	}
}
