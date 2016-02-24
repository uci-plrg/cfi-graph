package edu.uci.eecs.crowdsafe.graph.data.graph;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.CrowdSafeDebug;
import edu.uci.eecs.crowdsafe.common.util.MutableInteger;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.EvaluationType;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleMetadata;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleMetadataExecution;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleSGE;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleUIB;
import edu.uci.eecs.crowdsafe.graph.data.results.Graph;
import edu.uci.eecs.crowdsafe.graph.data.results.NodeResultsFactory;
import edu.uci.eecs.crowdsafe.graph.util.ModuleEdgeCounter;

public class ModuleGraph<EdgeEndpointType extends Node<EdgeEndpointType>> {

	public final String name;
	public final ApplicationModule module;

	// Maps from the signature hash of the cross-module edge to entry/exit points
	private final Map<Long, EdgeEndpointType> entryNodes = new HashMap<Long, EdgeEndpointType>();
	private final Map<Long, EdgeEndpointType> exitNodes = new HashMap<Long, EdgeEndpointType>();

	public final ModuleMetadata metadata = new ModuleMetadata();

	protected final GraphData<EdgeEndpointType> graphData;

	private final Set<EdgeEndpointType> unreachableNodes = new HashSet<EdgeEndpointType>();
	private final ModuleEdgeCounter edgeCounter = new ModuleEdgeCounter();

	private int executableNodeCount = 0;

	private boolean analyzed = false;

	public ModuleGraph(String name, ApplicationModule module) {
		this.name = name;
		this.module = module;
		this.graphData = new GraphData<EdgeEndpointType>();
	}

	public GraphData<EdgeEndpointType> getGraphData() {
		return graphData;
	}

	public Set<EdgeEndpointType> getUnreachableNodes() {
		return unreachableNodes;
	}

	public Collection<Long> getEntryHashes() {
		return entryNodes.keySet();
	}

	public Collection<EdgeEndpointType> getEntryPoints() {
		return entryNodes.values();
	}

	public EdgeEndpointType getEntryPoint(long hash) {
		return entryNodes.get(hash);
	}

	public void addModuleEntryNode(EdgeEndpointType entryNode) {
		if (entryNodes.containsKey(entryNode.getHash()))
			return;

		entryNodes.put(entryNode.getHash(), entryNode);
	}

	public Collection<Long> getExitHashes() {
		return exitNodes.keySet();
	}

	public Collection<EdgeEndpointType> getExitPoints() {
		return exitNodes.values();
	}

	public EdgeEndpointType getExitPoint(long hash) {
		return exitNodes.get(hash);
	}

	public void addModuleExitNode(EdgeEndpointType exitNode) {
		if (exitNodes.containsKey(exitNode.getHash()))
			return;

		exitNodes.put(exitNode.getHash(), exitNode);
	}

	public int getExecutableNodeCount() {
		return executableNodeCount;
	}

	public boolean hasNode(Node.Key key) {
		return graphData.nodesByKey.containsKey(key);
	}

	public boolean hasNodes() {
		return !graphData.nodesByKey.isEmpty();
	}

	public Collection<Node.Key> getAllKeys() {
		return graphData.nodesByKey.keySet();
	}

	public Collection<EdgeEndpointType> getAllNodes() {
		return graphData.nodesByKey.values();
	}

	public int getNodeCount() {
		return graphData.nodesByKey.size();
	}

	public EdgeEndpointType getNode(Node.Key key) {
		return graphData.nodesByKey.get(key);
	}

	public void addNode(EdgeEndpointType node) {
		switch (node.getType()) {
			case MODULE_ENTRY:
				addModuleEntryNode(node);
				break;
			case MODULE_EXIT:
				addModuleExitNode(node);
				break;
			default:
				executableNodeCount++;
				//$FALL-THROUGH$
			case SINGLETON:
				graphData.nodesByHash.add(node);
		}
		graphData.nodesByKey.put(node.getKey(), node);
	}

	public void resetAnalysis() {
		analyzed = false;
	}

	public void analyzeGraph(boolean analyzeReachability) {
		if (analyzed)
			return;

		analyzed = true;

		edgeCounter.reset();
		unreachableNodes.clear();

		if (analyzeReachability)
			analyzeReachability();
		else
			edgeStudy(graphData.nodesByKey.values());
	}

	private void edgeStudy(Iterable<EdgeEndpointType> nodes) {
		for (EdgeEndpointType node : nodes) {
			edgeCounter.tallyOutgoingEdges(node);
		}
	}

	private void analyzeReachability() {
		unreachableNodes.addAll(graphData.nodesByKey.values());

		Set<EdgeEndpointType> visitedNodes = new HashSet<EdgeEndpointType>();
		Queue<EdgeEndpointType> bfsQueue = new LinkedList<EdgeEndpointType>();
		bfsQueue.addAll(entryNodes.values());

		NodeList<EdgeEndpointType> systemNodes = graphData.nodesByHash.get(1L);
		if (systemNodes.size() == 1)
			bfsQueue.add(systemNodes.get(0));
		systemNodes = graphData.nodesByHash.get(3L);
		if (systemNodes.size() == 1)
			bfsQueue.add(systemNodes.get(0));

		while (bfsQueue.size() > 0) {
			EdgeEndpointType node = bfsQueue.remove();
			unreachableNodes.remove(node);
			visitedNodes.add(node);

			OrdinalEdgeList<EdgeEndpointType> edgeList = node.getOutgoingEdges();
			try {
				for (Edge<EdgeEndpointType> edge : edgeList) {
					EdgeEndpointType neighbor = edge.getToNode();
					if (!visitedNodes.contains(neighbor)) {
						bfsQueue.add(neighbor);
						visitedNodes.add(neighbor);
					}
					if (edge.isModuleExit())
						edgeCounter.tallyInterEdge(edge.getEdgeType());
					else
						edgeCounter.tallyIntraEdge(edge.getEdgeType());
				}
			} finally {
				edgeList.release();
			}

			edgeList = node.getIncomingEdges();
			try {
				for (Edge<EdgeEndpointType> edge : edgeList) {
					if (edge.isModuleEntry()) {
						edgeCounter.tallyInterEdge(edge.getEdgeType());
					}
				}
			} finally {
				edgeList.release();
			}
		}

		if (!CrowdSafeDebug.LOG_UNREACHABLE_ENTRY_POINTS)
			return;

		if (!unreachableNodes.isEmpty()) {
			edgeStudy(unreachableNodes);

			if (!CrowdSafeDebug.LOG_UNREACHABLE_ENTRY_POINTS) {
				Log.log("%d unreachable nodes for %s", unreachableNodes.size(), name);
				return;
			}

			Set<EdgeEndpointType> missedEntries = new HashSet<EdgeEndpointType>();
			for (EdgeEndpointType node : unreachableNodes) {
				boolean reachableFromUnreachables = false;
				for (Edge<EdgeEndpointType> edge : node.getIncomingEdges()) {
					if (unreachableNodes.contains(edge.getFromNode())) {
						reachableFromUnreachables = true;
						break;
					}
				}
				if (!reachableFromUnreachables)
					missedEntries.add(node);
			}

			Log.log("%d unreachable entry points for %s (%d total nodes)", missedEntries.size(), name,
					unreachableNodes.size());

			int limitCounter = 0;
			for (EdgeEndpointType node : missedEntries) {
				if (CrowdSafeDebug.MAX_UNREACHABLE_NODE_REPORT > 0
						&& ++limitCounter == CrowdSafeDebug.MAX_UNREACHABLE_NODE_REPORT) {
					Log.log("\t...");
					break;
				}

				if (node.hasIncomingEdges()) {
					for (Edge<EdgeEndpointType> edge : node.getIncomingEdges()) {
						Log.log("\tMissed incoming edge %s", edge);
					}
				} else {
					Log.log("\tNo entry points into %s", node);
				}
			}
		}
	}

	public void logGraph() {
		logGraph(Integer.MAX_VALUE);
	}

	public void logGraph(int limit) {
		if (entryNodes.isEmpty()) {
			logUnreachableGraph();
			return;
		}

		Log.log("\nGraph traversal for module %s (0x%x)", module, hashCode());

		Set<EdgeEndpointType> visitedNodes = new HashSet<EdgeEndpointType>();
		Queue<EdgeEndpointType> bfsQueue = new LinkedList<EdgeEndpointType>();
		bfsQueue.addAll(entryNodes.values());

		int count = 0;
		queue: while (bfsQueue.size() > 0) {
			EdgeEndpointType node = bfsQueue.remove();
			visitedNodes.add(node);

			OrdinalEdgeList<EdgeEndpointType> edgeList = node.getOutgoingEdges();
			try {
				for (Edge<EdgeEndpointType> edge : edgeList) {
					EdgeEndpointType neighbor = edge.getToNode();
					if (!visitedNodes.contains(neighbor)) {
						bfsQueue.add(neighbor);
						visitedNodes.add(neighbor);
					}
					Log.log(edge);

					count++;
					if (count > limit)
						break queue;
				}
			} finally {
				edgeList.release();
			}
		}

		Log.log();
	}

	public void logUnreachableGraph() {
		Log.log("\nGraph traversal for unreachable graph %s (0x%x)", module, hashCode());

		for (EdgeEndpointType node : getAllNodes()) {
			Log.log("Node: %s", node);

			OrdinalEdgeList<EdgeEndpointType> edgeList = node.getOutgoingEdges();
			try {
				for (Edge<EdgeEndpointType> edge : edgeList) {
					Log.log("\tOutgoing: %s", edge);
				}
			} finally {
				edgeList.release();
			}
			edgeList = node.getIncomingEdges();
			try {
				for (Edge<EdgeEndpointType> edge : edgeList) {
					Log.log("\tIncoming: %s", edge);
				}
			} finally {
				edgeList.release();
			}
		}
	}

	public void writeDotFile(File file, String label) throws IOException {
		PrintWriter out = new PrintWriter(file);
		out.println("digraph main{");
		out.println("node [shape=box fontsize=10];");

		int nodeIndex = 0;
		Map<EdgeEndpointType, Integer> nodeIndexMap = new HashMap<EdgeEndpointType, Integer>();
		for (EdgeEndpointType node : getAllNodes()) {
			nodeIndexMap.put(node, nodeIndex);
			out.println(String.format("Node%d [ label=\"0x%x\"", nodeIndex, node.getHash()));

			OrdinalEdgeList<EdgeEndpointType> edgeList = node.getOutgoingEdges();
			try {
				for (Edge<EdgeEndpointType> edge : edgeList)
					out.println(String.format("Node%d->Node%d [ label=\"%s%d\" ];",
							nodeIndexMap.get(edge.getFromNode()), nodeIndexMap.get(edge.getToNode()),
							edge.getEdgeType().code, edge.getOrdinal()));
			} finally {
				edgeList.release();
			}
			edgeList = node.getIncomingEdges();
			try {
				for (Edge<EdgeEndpointType> edge : edgeList) {
					if (edge.isModuleEntry())
						out.println(String.format("Node%d->Node%d [ label=\"%s%d\" ];",
								nodeIndexMap.get(edge.getFromNode()), nodeIndexMap.get(edge.getToNode()),
								edge.getEdgeType().code, edge.getOrdinal()));
				}
			} finally {
				edgeList.release();
			}

			nodeIndex++;
		}
		out.println("color=blue");
		out.println(String.format("label=\"%s\"", label));
		out.println("}");
	}

	public void logUnknownSuspiciousUIB() {
		if (metadata.isSingletonExecution()) {
			for (ModuleUIB uib : metadata.getSingletonExecution().uibs) {
				if (!uib.isAdmitted)
					Log.log("<UIB: U->U %dI %dT of %s", uib.instanceCount, uib.traversalCount, uib.edge);
			}
		}
	}

	public Graph.Module summarize(boolean reportUnreachableSubgraphs) {
		if (!analyzed)
			throw new IllegalStateException("Cannot summarize a graph that has not been analyzed!");

		Graph.Module.Builder moduleBuilder = Graph.Module.newBuilder();
		Graph.ModuleVersion.Builder moduleVersionBuilder = Graph.ModuleVersion.newBuilder();
		Graph.ModuleInstance.Builder moduleInstanceBuilder = Graph.ModuleInstance.newBuilder();
		Graph.UnreachableNode.Builder unreachableBuilder = Graph.UnreachableNode.newBuilder();
		Graph.Node.Builder nodeBuilder = Graph.Node.newBuilder();
		Graph.Edge.Builder edgeBuilder = Graph.Edge.newBuilder();
		Graph.EdgeTypeCount.Builder edgeTypeCountBuilder = Graph.EdgeTypeCount.newBuilder();
		NodeResultsFactory nodeFactory = new NodeResultsFactory(moduleVersionBuilder, nodeBuilder);

		Graph.ModuleMetadata.Builder metadataBuilder = Graph.ModuleMetadata.newBuilder();
		Graph.UIBObservation.Builder uibBuilder = Graph.UIBObservation.newBuilder();
		Graph.SuspiciousGencodeEntry.Builder sgeBuilder = Graph.SuspiciousGencodeEntry.newBuilder();

		moduleBuilder.setDistributionName(module.name);
		moduleBuilder.setNodeCount(getNodeCount());
		moduleBuilder.setExecutableNodeCount(getExecutableNodeCount());
		moduleBuilder.setEntryPointCount(getEntryHashes().size());

		moduleVersionBuilder.clear().setName(module.filename);
		moduleVersionBuilder.setVersion(module.version);
		moduleInstanceBuilder.setVersion(moduleVersionBuilder.build());
		moduleInstanceBuilder.setNodeCount(executableNodeCount);
		moduleBuilder.addInstance(moduleInstanceBuilder.build());

		if (reportUnreachableSubgraphs) {
			Set<EdgeEndpointType> unreachableNodes = getUnreachableNodes();
			if (!unreachableNodes.isEmpty()) {
				for (Node<?> unreachableNode : unreachableNodes) {
					unreachableBuilder.clear().setNode(nodeFactory.buildNode(unreachableNode));
					unreachableBuilder.setIsEntryPoint(true);

					OrdinalEdgeList<?> edgeList = unreachableNode.getIncomingEdges();
					try {
						if (!edgeList.isEmpty()) {
							for (Edge<?> incoming : edgeList) {
								if (unreachableNodes.contains(incoming.getFromNode())) {
									unreachableBuilder.setIsEntryPoint(false);
								} else {
									moduleVersionBuilder.setName(incoming.getFromNode().getModule().filename);
									moduleVersionBuilder.setVersion(incoming.getFromNode().getModule().version);
									nodeBuilder.setVersion(moduleVersionBuilder.build());
									edgeBuilder.clear().setFromNode(nodeBuilder.build());
									edgeBuilder.setToNode(unreachableBuilder.getNode());
									edgeBuilder.setType(incoming.getEdgeType().mapToResultType());
									unreachableBuilder.addMissedIncomingEdge(edgeBuilder.build());
								}
							}
						}
					} finally {
						edgeList.release();
					}
					moduleBuilder.addUnreachable(unreachableBuilder.build());
				}
			}
		}

		for (EdgeType type : EdgeType.values()) {
			edgeTypeCountBuilder.clear().setType(type.mapToResultType());
			edgeTypeCountBuilder.setCount(edgeCounter.getInterCount(type));
			moduleBuilder.addInterModuleEdgeCount(edgeTypeCountBuilder.build());
		}
		for (EdgeType type : EdgeType.values()) {
			edgeTypeCountBuilder.clear().setType(type.mapToResultType());
			edgeTypeCountBuilder.setCount(edgeCounter.getIntraCount(type));
			moduleBuilder.addIntraModuleEdgeCount(edgeTypeCountBuilder.build());
		}

		Map<EvaluationType, MutableInteger> totalInstanceCounts = new EnumMap<EvaluationType, MutableInteger>(
				EvaluationType.class);
		Map<EvaluationType, MutableInteger> totalTraversalCounts = new EnumMap<EvaluationType, MutableInteger>(
				EvaluationType.class);
		Map<EvaluationType, MutableInteger> crossModuleInstanceCounts = new EnumMap<EvaluationType, MutableInteger>(
				EvaluationType.class);
		Map<EvaluationType, MutableInteger> crossModuleTraversalCounts = new EnumMap<EvaluationType, MutableInteger>(
				EvaluationType.class);
		Map<EvaluationType, MutableInteger> intraModuleInstanceCounts = new EnumMap<EvaluationType, MutableInteger>(
				EvaluationType.class);
		Map<EvaluationType, MutableInteger> intraModuleTraversalCounts = new EnumMap<EvaluationType, MutableInteger>(
				EvaluationType.class);
		for (EvaluationType type : EvaluationType.values()) {
			totalInstanceCounts.put(type, new MutableInteger(0));
			totalTraversalCounts.put(type, new MutableInteger(0));
			crossModuleInstanceCounts.put(type, new MutableInteger(0));
			crossModuleTraversalCounts.put(type, new MutableInteger(0));
			intraModuleInstanceCounts.put(type, new MutableInteger(0));
			intraModuleTraversalCounts.put(type, new MutableInteger(0));
		}

		if (metadata.getRootSequence() == null) {
			metadataBuilder.setSequenceIdHigh(0L);
			metadataBuilder.setSequenceIdLow(0L);
			metadataBuilder.setExecutionIdHigh(0L);
			metadataBuilder.setExecutionIdLow(0L);
			metadataBuilder.setExecutionIndex(-1);
		} else {
			ModuleMetadataExecution execution = metadata.getRootSequence().getHeadExecution();
			metadataBuilder.setSequenceIdHigh(metadata.getRootSequence().id.getMostSignificantBits());
			metadataBuilder.setSequenceIdLow(metadata.getRootSequence().id.getLeastSignificantBits());
			metadataBuilder.setExecutionIdHigh(execution.id.getMostSignificantBits());
			metadataBuilder.setExecutionIdLow(execution.id.getLeastSignificantBits());

			if (metadata.isMain()) {
				Log.log("Setting main execution index to %d for summary of <%s>",
						metadata.getRootSequence().executions.size(), name);
			}
			metadataBuilder.setExecutionIndex(metadata.getRootSequence().executions.size());

			for (ModuleUIB uib : execution.uibs) {
				if (uib.edge.isModuleEntry())
					continue;

				totalInstanceCounts.get(EvaluationType.TOTAL).add(uib.instanceCount);
				totalTraversalCounts.get(EvaluationType.TOTAL).add(uib.traversalCount);
				if (uib.edge.isCrossModule()) {
					crossModuleInstanceCounts.get(EvaluationType.TOTAL).add(uib.instanceCount);
					crossModuleTraversalCounts.get(EvaluationType.TOTAL).add(uib.traversalCount);
				} else {
					intraModuleInstanceCounts.get(EvaluationType.TOTAL).add(uib.instanceCount);
					intraModuleTraversalCounts.get(EvaluationType.TOTAL).add(uib.traversalCount);
				}

				if (uib.isAdmitted) {
					totalInstanceCounts.get(EvaluationType.ADMITTED).add(uib.instanceCount);
					totalTraversalCounts.get(EvaluationType.ADMITTED).add(uib.traversalCount);
					if (uib.edge.isCrossModule()) {
						crossModuleInstanceCounts.get(EvaluationType.ADMITTED).add(uib.instanceCount);
						crossModuleTraversalCounts.get(EvaluationType.ADMITTED).add(uib.traversalCount);
					} else {
						intraModuleInstanceCounts.get(EvaluationType.ADMITTED).add(uib.instanceCount);
						intraModuleTraversalCounts.get(EvaluationType.ADMITTED).add(uib.traversalCount);
					}
				} else {
					totalInstanceCounts.get(EvaluationType.SUSPICIOUS).add(uib.instanceCount);
					totalTraversalCounts.get(EvaluationType.SUSPICIOUS).add(uib.traversalCount);
					if (uib.edge.isCrossModule()) {
						crossModuleInstanceCounts.get(EvaluationType.SUSPICIOUS).add(uib.instanceCount);
						crossModuleTraversalCounts.get(EvaluationType.SUSPICIOUS).add(uib.traversalCount);
					} else {
						intraModuleInstanceCounts.get(EvaluationType.SUSPICIOUS).add(uib.instanceCount);
						intraModuleTraversalCounts.get(EvaluationType.SUSPICIOUS).add(uib.traversalCount);
					}
				}
			}

			for (ModuleSGE sge : execution.sges) {
				sgeBuilder.setUibCount(sge.uibCount);
				sgeBuilder.setSuibCount(sge.suibCount);
				metadataBuilder.addGencodeEntries(sgeBuilder.build());
				sgeBuilder.clear();
			}
		}

		for (EvaluationType type : EvaluationType.values()) {
			uibBuilder.setType(type.getResultType());
			uibBuilder.setInstanceCount(totalInstanceCounts.get(type).getVal());
			uibBuilder.setTraversalCount(totalTraversalCounts.get(type).getVal());
			metadataBuilder.addTotalObserved(uibBuilder.build());
			uibBuilder.clear();

			uibBuilder.setType(type.getResultType());
			uibBuilder.setInstanceCount(crossModuleInstanceCounts.get(type).getVal());
			uibBuilder.setTraversalCount(crossModuleTraversalCounts.get(type).getVal());
			metadataBuilder.addInterModuleObserved(uibBuilder.build());
			uibBuilder.clear();

			uibBuilder.setType(type.getResultType());
			uibBuilder.setInstanceCount(intraModuleInstanceCounts.get(type).getVal());
			uibBuilder.setTraversalCount(intraModuleTraversalCounts.get(type).getVal());
			metadataBuilder.addIntraModuleObserved(uibBuilder.build());
			uibBuilder.clear();
		}

		moduleBuilder.setMetadata(metadataBuilder.build());
		return moduleBuilder.build();
	}

	@Override
	public String toString() {
		return "graph " + module.name;
	}
}
