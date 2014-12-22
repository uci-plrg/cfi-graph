package edu.uci.eecs.crowdsafe.graph.data.graph.execution.loader;

import java.io.IOException;

import edu.uci.eecs.crowdsafe.common.exception.InvalidGraphException;
import edu.uci.eecs.crowdsafe.common.exception.MultipleEdgeException;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.GraphLoadEventListener.LoadTarget;
import edu.uci.eecs.crowdsafe.graph.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.execution.ModuleInstance;
import edu.uci.eecs.crowdsafe.graph.io.execution.ExecutionTraceStreamType;
import edu.uci.eecs.crowdsafe.graph.util.CrowdSafeTraceUtil;

public class ProcessGraphEdgeFactory {

	private static final int ENTRY_BYTE_COUNT = 0x10;

	private final ProcessGraphLoadSession.GraphLoader loader;
	private final LittleEndianInputStream input;

	long edgeIndex = -1;

	public ProcessGraphEdgeFactory(ProcessGraphLoadSession.GraphLoader loader, LittleEndianInputStream input)
			throws IOException {
		this.loader = loader;
		this.input = input;
	}

	boolean ready() throws IOException {
		return input.ready(ENTRY_BYTE_COUNT);
	}

	// 25% hot during load!
	void createEdge() throws IOException {
		long annotatedFromTag = input.readLong();
		long annotatedToTag = input.readLong();
		edgeIndex++;

		long fromTag = CrowdSafeTraceUtil.getTag(annotatedFromTag);
		long toTag = CrowdSafeTraceUtil.getTag(annotatedToTag);
		int fromVersion = CrowdSafeTraceUtil.getTagVersion(annotatedFromTag);
		int toVersion = CrowdSafeTraceUtil.getTagVersion(annotatedToTag);

		ModuleInstance fromModule = loader.graph.getModules().getModule(fromTag, edgeIndex,
				ExecutionTraceStreamType.GRAPH_EDGE);
		ModuleInstance toModule = loader.graph.getModules().getModule(toTag, edgeIndex,
				ExecutionTraceStreamType.GRAPH_EDGE);

		EdgeType edgeType = CrowdSafeTraceUtil.getTagEdgeType(annotatedFromTag);
		int edgeOrdinal = CrowdSafeTraceUtil.getEdgeOrdinal(annotatedFromTag);

		ExecutionNode fromNode = loader.hashLookupTable.get(ExecutionNode.Key.create(fromTag, fromVersion, fromModule));
		ExecutionNode toNode = loader.hashLookupTable.get(ExecutionNode.Key.create(toTag, toVersion, toModule));

		if (edgeOrdinal == 255) {
			Log.log("Warning: skipping edge %s with ordinal 255", new Edge<ExecutionNode>(fromNode, toNode, edgeType,
					edgeOrdinal));
			return;
		}

		if (loader.listener != null) {
			loader.listener.nodeLoadReference(fromNode, LoadTarget.EDGE);
			loader.listener.nodeLoadReference(toNode, LoadTarget.EDGE);
		}

		// Double check if tag1 and tag2 exist in the lookup file
		if (fromNode == null) {
			Log.log("Problem at edge index %d: missing 'from' node for tag 0x%x-v%d(%s) in edge to 0x%x-v%d(%s) of type %s on ordinal %d",
					edgeIndex, fromTag, fromVersion, fromModule.unit.name, toTag, toVersion, toModule.unit.name,
					edgeType, edgeOrdinal);
			return;
		}
		if (toNode == null) {
			// if (edgeType == EdgeType.CALL_CONTINUATION)
			// return; // discard b/c we never reached the continuation point
			// else {
			// boolean fixed = false;
			Log.log("Problem at edge index %d: missing 'to' node for tag 0x%x-v%d(%s) in edge #%d from 0x%x-v%d(%s) of type %s on ordinal %d",
					edgeIndex, toTag, toVersion, toModule.unit.name, edgeIndex, fromTag, fromVersion,
					fromModule.unit.name, edgeType, edgeOrdinal);
			return;
			// }
		}

		if ((fromModule.unit != toModule.unit)
				&& (loader.graph.getModuleGraphCluster(fromModule.unit) != loader.graph
						.getModuleGraphCluster(toModule.unit))) {
			throw new InvalidGraphException(String.format(
					"Error: a normal edge\n\t[%s - %s]\ncrosses between module %s and %s", fromNode, toNode,
					loader.graph.getModuleGraphCluster(fromModule.unit).cluster.name,
					loader.graph.getModuleGraphCluster(toModule.unit).cluster.name));
		}

		Edge<ExecutionNode> existing = fromNode.getOutgoingEdge(toNode);
		Edge<ExecutionNode> e = new Edge<ExecutionNode>(fromNode, toNode, edgeType, edgeOrdinal);

		if ((existing != null)
				&& ((existing.isDirect() && e.isContinuation()) || (existing.isContinuation() && e.isDirect()))) {
			existing = null; // // allow a call to its own continuation
		}
		if (existing == null) {
			fromNode.addOutgoingEdge(e);
			toNode.addIncomingEdge(e);

			if (loader.listener != null)
				loader.listener.edgeCreation(e);
		} else {
			if (!existing.equals(e)) {
				String msg = "Multiple edges:\n" + "Edge1: " + existing + "\n" + "Edge2: " + e;
				throw new MultipleEdgeException(msg);
			}
		}
	}

	void close() throws IOException {
		if (input.ready())
			Log.log("Warning: input stream %s has %d bytes remaining.", input.description, input.available());

		input.close();
	}
}