package edu.uci.eecs.crowdsafe.graph.data.graph.anonymous;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleBoundaryNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;

public class AnonymousSubgraphFlowAnalysis {

	private static class FlowRecord {
		final int id = ID_INDEX++;
		final ModuleBoundaryNode entryPoint;
		final ModuleNode<?> entryNode;
		final Set<ModuleNode<?>> coverage = new HashSet<ModuleNode<?>>();
		final Set<ModuleNode<?>> exits = new HashSet<ModuleNode<?>>();
		int backEdgeCount = 0;

		public FlowRecord(ModuleBoundaryNode entryPoint, ModuleNode<?> entryNode) {
			this.entryPoint = entryPoint;
			this.entryNode = entryNode;
		}
	}

	private static int ID_INDEX = 0;

	private FlowRecord flowRecord;
	private final Map<ModuleNode<?>, FlowRecord> flowPerEntryNode = new LinkedHashMap<ModuleNode<?>, FlowRecord>();

	private final LinkedList<ModuleNode<?>> queue = new LinkedList<ModuleNode<?>>();

	void clear() {
		ID_INDEX = 0;
		flowRecord = null;
		flowPerEntryNode.clear();
	}

	void analyzeFlow(ModuleAnonymousGraphs module) {
		int returnOnlyCount = 0;
		int singletonExitCount = 0;
		int totalBackEdgeCount = 0;
		int maxBackEdgeCount = 0;
		for (ModuleGraph<ModuleNode<?>> graph : module.subgraphs) {
			for (long entryHash : graph.getEntryHashes()) {
				ModuleBoundaryNode entryPoint = (ModuleBoundaryNode) graph.getEntryPoint(entryHash);

				OrdinalEdgeList<ModuleNode<?>> edges = entryPoint.getOutgoingEdges();
				try {
					for (Edge<ModuleNode<?>> edge : edges) {
						ModuleNode<?> entryNode = edge.getToNode();
						flowRecord = new FlowRecord(entryPoint, entryNode);
						flowPerEntryNode.put(entryNode, flowRecord);

						queue.clear();
						queue.push(entryNode);
						flowRecord.coverage.add(entryNode);
						followFlow();

						if (flowRecord.exits.isEmpty())
							returnOnlyCount++;
						else if (flowRecord.exits.size() == 1)
							singletonExitCount++;

						totalBackEdgeCount += flowRecord.backEdgeCount;
						if (flowRecord.backEdgeCount > maxBackEdgeCount)
							maxBackEdgeCount = flowRecord.backEdgeCount;
					}
				} finally {
					edges.release();
				}
			}
		}

		float averageBackEdgeCount = (totalBackEdgeCount / (float) flowPerEntryNode.size());
		Log.log("\tReturn only: %d; singleton exit: %d", returnOnlyCount, singletonExitCount);
		Log.log("\tAverage back edge count: %.3f; max: %d", averageBackEdgeCount, maxBackEdgeCount);

		if (flowPerEntryNode.size() < 5) {
			for (FlowRecord flowRecord : flowPerEntryNode.values()) {
				if (flowRecord.exits.size() > 1) {
					int coveragePercent = Math.round((flowRecord.coverage.size() / (float) module
							.getExecutableNodeCount()) * 100f);
					if (coveragePercent > 100)
						throw new IllegalStateException("Coverage must not exceed 100% from any entry point!");
					Log.log("\tEntry #%d to %d exits covering %d%% of the subgraph", flowRecord.id,
							flowRecord.exits.size(), coveragePercent);
				}
			}
		} else {
			int totalExits = 0;
			int maxExitCount = 0;
			for (FlowRecord flowRecord : flowPerEntryNode.values()) {
				totalExits += flowRecord.exits.size();
				if (flowRecord.exits.size() > maxExitCount)
					maxExitCount = flowRecord.exits.size();
			}
			float averageExitCount = (totalExits / (float) flowPerEntryNode.size());
			Log.log("\t%d entry points flow on average to %.2f exits each; max %d", flowPerEntryNode.size(),
					averageExitCount, maxExitCount);
		}
	}

	private void followFlow() {
		while (!queue.isEmpty()) {
			ModuleNode<?> node = queue.pop();

			OrdinalEdgeList<ModuleNode<?>> edges = node.getOutgoingEdges();
			try {
				for (Edge<ModuleNode<?>> edge : edges) {
					ModuleNode<?> toNode = edge.getToNode();

					if (toNode.getType() == MetaNodeType.MODULE_EXIT) {
						flowRecord.exits.add(node);
						continue;
					}

					if (flowRecord.coverage.contains(toNode)) {
						flowRecord.backEdgeCount++;
					} else {
						flowRecord.coverage.add(toNode);
						queue.push(toNode);
					}
				}
			} finally {
				edges.release();
			}
		}
	}
}
