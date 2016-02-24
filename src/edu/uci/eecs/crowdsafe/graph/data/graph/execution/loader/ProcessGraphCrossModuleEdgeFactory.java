package edu.uci.eecs.crowdsafe.graph.data.graph.execution.loader;

import java.io.IOException;

import edu.uci.eecs.crowdsafe.common.exception.InvalidTagException;
import edu.uci.eecs.crowdsafe.common.exception.MultipleEdgeException;
import edu.uci.eecs.crowdsafe.common.exception.TagNotFoundException;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.GraphLoadEventListener.LoadTarget;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.execution.ModuleInstance;
import edu.uci.eecs.crowdsafe.graph.io.execution.ExecutionTraceStreamType;
import edu.uci.eecs.crowdsafe.graph.util.CrowdSafeTraceUtil;

/**
 * Before calling this function, you should have all the normal nodes added to the corresponding graph and their indexes
 * fixed. The only thing this function should do is to add signature nodes when necessary and build the necessary edges
 * between them and real entry nodes.
 * 
 * @param crossModuleEdgeFile
 * @param hashLookupTable
 * @throws MultipleEdgeException
 * @throws InvalidTagException
 * @throws TagNotFoundException
 */
public class ProcessGraphCrossModuleEdgeFactory {

	private static final int ENTRY_BYTE_COUNT = 0x18;

	private final ProcessGraphLoadSession.GraphLoader loader;
	private final LittleEndianInputStream input;

	long edgeIndex = -1;

	public ProcessGraphCrossModuleEdgeFactory(ProcessGraphLoadSession.GraphLoader loader, LittleEndianInputStream input)
			throws IOException {
		this.loader = loader;
		this.input = input;
	}

	boolean ready() throws IOException {
		return input.ready(ENTRY_BYTE_COUNT);
	}

	void createEdge() throws IOException {
		long annotatedFromTag = input.readLong();
		long annotatedToTag = input.readLong();
		long signatureHash = input.readLong();
		signatureHash = Math.abs(signatureHash);
		edgeIndex++;

		long fromTag = CrowdSafeTraceUtil.getTag(annotatedFromTag);
		long toTag = CrowdSafeTraceUtil.getTag(annotatedToTag);
		int fromVersion = CrowdSafeTraceUtil.getTagVersion(annotatedFromTag);
		int toVersion = CrowdSafeTraceUtil.getTagVersion(annotatedToTag);

		ModuleInstance fromModule = loader.graph.getModules().getModule(fromTag, edgeIndex,
				ExecutionTraceStreamType.CROSS_MODULE_EDGE);
		ModuleInstance toModule = loader.graph.getModules().getModule(toTag, edgeIndex,
				ExecutionTraceStreamType.CROSS_MODULE_EDGE);

		EdgeType edgeType = CrowdSafeTraceUtil.getTagEdgeType(annotatedFromTag);
		int edgeOrdinal = CrowdSafeTraceUtil.getEdgeOrdinal(annotatedFromTag);

		ExecutionNode fromNode = loader.hashLookupTable.get(ExecutionNode.Key.create(fromTag, fromVersion, fromModule));
		ExecutionNode toNode = loader.hashLookupTable.get(ExecutionNode.Key.create(toTag, toVersion, toModule));

		// Double check if tag1 and tag2 exist in the lookup file
		if (fromNode == null) {
			Log.log("Problem at cross-module edge index %d: missing cross-module edge source block %s!", edgeIndex,
					ExecutionNode.Key.create(fromTag, fromVersion, fromModule));
			return;
			/**
			 * <pre>
			throw new TagNotFoundException("Failed to find cross-module edge source block %s!",
					ExecutionNode.Key.create(fromTag, fromVersion, fromModule));
			 */
		}
		if (toNode == null) {
			Log.log("Problem at cross-module edge index %d: missing cross-module edge destination block %s!",
					edgeIndex, ExecutionNode.Key.create(toTag, toVersion, toModule));
			return;
			/**
			 * <pre>
			throw new TagNotFoundException("Failed to find cross-module edge destination block %s!",
					ExecutionNode.Key.create(toTag, toVersion, toModule));
			 */
		}

		if (loader.listener != null) {
			loader.listener.nodeLoadReference(fromNode, LoadTarget.CROSS_MODULE_EDGE);
			loader.listener.nodeLoadReference(toNode, LoadTarget.CROSS_MODULE_EDGE);
		}

		Edge<ExecutionNode> existing = fromNode.getOutgoingEdge(toNode);
		if (existing == null) {
			// Be careful when dealing with the cross module nodes.
			// Cross-module edges are not added to any node, but the
			// edge from signature node to real entry node is preserved.
			// We only need to add the signature nodes to "nodes"
			ModuleGraph<ExecutionNode> fromGraph = loader.graph.getModuleGraph(fromModule);
			ModuleGraph<ExecutionNode> toGraph = loader.graph.getModuleGraph(toModule);

			if (fromGraph == toGraph) {
				Edge<ExecutionNode> e = new Edge<ExecutionNode>(fromNode, toNode, edgeType, edgeOrdinal);
				fromNode.addOutgoingEdge(e);
				toNode.addIncomingEdge(e);

				if (loader.listener != null)
					loader.listener.edgeCreation(e);
			} else {
				ExecutionNode exitNode = new ExecutionNode(fromModule, MetaNodeType.MODULE_EXIT, signatureHash, 0,
						signatureHash, fromNode.getTimestamp());
				fromGraph.addNode(exitNode);
				Edge<ExecutionNode> moduleExitEdge = new Edge<ExecutionNode>(fromNode, exitNode, edgeType, 0);
				fromNode.addOutgoingEdge(moduleExitEdge);
				exitNode.addIncomingEdge(moduleExitEdge);

				if (loader.listener != null)
					loader.listener.edgeCreation(moduleExitEdge);

				ExecutionNode entryNode = toGraph.getEntryPoint(signatureHash);
				if (entryNode == null) {
					entryNode = new ExecutionNode(toModule, MetaNodeType.MODULE_ENTRY, 0L, 0, signatureHash,
							toNode.getTimestamp());
					toGraph.addModuleEntryNode(entryNode);
				}
				Edge<ExecutionNode> moduleEntryEdge = new Edge<ExecutionNode>(entryNode, toNode, edgeType, 0);
				entryNode.addOutgoingEdge(moduleEntryEdge);
				toNode.addIncomingEdge(moduleEntryEdge);

				if (loader.listener != null)
					loader.listener.edgeCreation(moduleEntryEdge);
			}
		}
	}

	void close() throws IOException {
		if (input.ready())
			Log.log("Warning: input stream %s has %d bytes remaining.", input.description, input.available());

		input.close();
	}
}