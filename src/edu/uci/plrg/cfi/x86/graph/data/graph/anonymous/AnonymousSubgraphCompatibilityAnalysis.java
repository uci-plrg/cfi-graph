package edu.uci.plrg.cfi.x86.graph.data.graph.anonymous;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.common.util.MutableInteger;
import edu.uci.plrg.cfi.x86.graph.data.graph.Edge;
import edu.uci.plrg.cfi.x86.graph.data.graph.EdgeType;
import edu.uci.plrg.cfi.x86.graph.data.graph.MetaNodeType;
import edu.uci.plrg.cfi.x86.graph.data.graph.ModuleGraph;
import edu.uci.plrg.cfi.x86.graph.data.graph.NodeList;
import edu.uci.plrg.cfi.x86.graph.data.graph.OrdinalEdgeList;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ModuleNode;

class AnonymousSubgraphCompatibilityAnalysis {
	final ModuleGraph<ModuleNode<?>> leftGraph;
	final ModuleGraph<ModuleNode<?>> rightGraph;

	private final Set<ModuleNode<?>> visitedLeftNodes = new HashSet<ModuleNode<?>>();

	private Set<ModuleNode<?>> leftCoverageSet = null;
	private Set<ModuleNode<?>> rightCoverageSet = null;

	private MutableInteger maxExploredDepth = null;
	private MutableInteger maxCompatibleDepth = null;
	// private MutableInteger failedIndirectsHaving

	private boolean followIndirectBranches = false;

	AnonymousSubgraphCompatibilityAnalysis(ModuleGraph<ModuleNode<?>> leftGraph,
			ModuleGraph<ModuleNode<?>> rightGraph) {
		// put the larger graph on the left
		if (rightGraph.getNodeCount() > leftGraph.getNodeCount()) {
			ModuleGraph<ModuleNode<?>> swap = rightGraph;
			rightGraph = leftGraph;
			leftGraph = swap;
		}

		this.leftGraph = leftGraph;
		this.rightGraph = rightGraph;
	}

	void localCompatibilityPerNode(int depth) {
		Log.log("\nExploring localized compatibility of two graphs (left: %d nodes, right: %d nodes, depth: %d)",
				leftGraph.getNodeCount(), rightGraph.getNodeCount(), depth);

		maxExploredDepth = null;
		maxCompatibleDepth = null;
		followIndirectBranches = false;

		Set<ModuleNode<?>> unmatchedRightNodes = new HashSet<ModuleNode<?>>(rightGraph.getAllNodes());

		int compatibleCount = 0;
		int incompatibleCount = 0;
		boolean compatible;
		for (ModuleNode<?> leftNode : leftGraph.getAllNodes()) {
			compatible = false;
			NodeList<ModuleNode<?>> hashMatches = rightGraph.getGraphData().nodesByHash.get(leftNode.getHash());
			if (hashMatches != null) {
				for (int i = 0; i < hashMatches.size(); i++) {
					ModuleNode<?> rightNode = hashMatches.get(i);
					if (isCompatible(leftNode, rightNode, depth)) {
						unmatchedRightNodes.remove(rightNode);
						compatible = true;
						break;
					}
				}
			}
			if (compatible)
				compatibleCount++;
			else
				incompatibleCount++;
		}
		int rightGraphCoverage = (rightGraph.getNodeCount() - unmatchedRightNodes.size());
		Log.log("\tPer left node: %d compatible and %d incompatible (%d right coverage)", compatibleCount,
				incompatibleCount, rightGraphCoverage);

		compatibleCount = rightGraphCoverage;
		incompatibleCount = 0;
		for (ModuleNode<?> rightNode : unmatchedRightNodes) {
			compatible = false;
			NodeList<ModuleNode<?>> hashMatches = leftGraph.getGraphData().nodesByHash.get(rightNode.getHash());
			if (hashMatches != null) {
				for (int i = 0; i < hashMatches.size(); i++) {
					ModuleNode<?> leftNode = hashMatches.get(i);
					if (isCompatible(leftNode, rightNode, depth)) {
						compatible = true;
						break;
					}
				}
			}
			if (compatible)
				compatibleCount++;
			else
				incompatibleCount++;
		}

		Log.log("\tPer right node: %d compatible and %d incompatible", compatibleCount, incompatibleCount);
	}

	void fullCompatibilityPerEntry() {
		Log.log("\nExploring entry point compatibility of two graphs (left: %d nodes, right: %d nodes)",
				leftGraph.getNodeCount(), rightGraph.getNodeCount());

		maxExploredDepth = new MutableInteger(0);
		maxCompatibleDepth = new MutableInteger(0);
		followIndirectBranches = true;

		leftCoverageSet = new HashSet<ModuleNode<?>>();
		rightCoverageSet = new HashSet<ModuleNode<?>>();
		Set<ModuleNode<?>> leftCompatibleCoverageSet = new HashSet<ModuleNode<?>>();
		Set<ModuleNode<?>> rightCompatibleCoverageSet = new HashSet<ModuleNode<?>>();
		List<Integer> compatibleSubgraphDepths = new ArrayList<Integer>();

		int compatibleCount = 0;
		int incompatibleCount = 0;
		int totalRightNodes = 0;
		int compatibleSubgraphMaxDepth = 0;
		int compatibleSubgraphDepth;
		boolean compatible;
		Set<ModuleNode<?>> unmatchedRightNodes = new HashSet<ModuleNode<?>>();
		Set<Long> entryHashes = new HashSet<Long>(leftGraph.getEntryHashes());
		entryHashes.addAll(rightGraph.getEntryHashes());
		for (Long entryHash : entryHashes) {
			ModuleNode<?> leftEntry = leftGraph.getEntryPoint(entryHash);
			if (leftEntry == null) {
				Log.log("Entry point 0x%x does not occur on the left side!", entryHash);
				continue;
			}
			ModuleNode<?> rightEntry = rightGraph.getEntryPoint(entryHash);
			if (rightEntry == null) {
				Log.log("Entry point 0x%x does not occur on the right side!", entryHash);
				continue;
			}

			OrdinalEdgeList<ModuleNode<?>> leftEdges = leftEntry.getOutgoingEdges();
			OrdinalEdgeList<ModuleNode<?>> rightEdges = rightEntry.getOutgoingEdges();
			try {
				for (Edge<ModuleNode<?>> rightEdge : rightEdges) {
					unmatchedRightNodes.add(rightEdge.getToNode());
					totalRightNodes++;
				}

				for (Edge<ModuleNode<?>> leftEdge : leftEdges) {
					ModuleNode<?> leftToNode = leftEdge.getToNode();
					compatible = false;
					for (Edge<ModuleNode<?>> rightEdge : rightEdges) {
						ModuleNode<?> rightToNode = rightEdge.getToNode();
						if (leftToNode.getHash() == rightToNode.getHash()) {
							leftCoverageSet.clear();
							rightCoverageSet.clear();
							maxExploredDepth.setVal(Integer.MAX_VALUE);
							maxCompatibleDepth.setVal(Integer.MAX_VALUE);
							visitedLeftNodes.clear();
							if (isCompatible(leftToNode, rightToNode, Integer.MAX_VALUE)) {
								unmatchedRightNodes.remove(rightToNode);
								leftCompatibleCoverageSet.addAll(leftCoverageSet);
								rightCompatibleCoverageSet.addAll(rightCoverageSet);
								compatibleSubgraphDepth = Integer.MAX_VALUE - maxCompatibleDepth.getVal();
								compatibleSubgraphDepths.add(compatibleSubgraphDepth);
								if (compatibleSubgraphDepth > compatibleSubgraphMaxDepth)
									compatibleSubgraphMaxDepth = compatibleSubgraphDepth;
								compatible = true;
								break;
							}
						}
					}

					if (compatible)
						compatibleCount++;
					else
						incompatibleCount++;
				}

				int rightGraphCoverage = (totalRightNodes - unmatchedRightNodes.size());
				Log.log("\tEntry point 0x%x per left node: %d compatible and %d incompatible (%d right coverage)",
						entryHash, compatibleCount, incompatibleCount, rightGraphCoverage);

				compatibleCount = rightGraphCoverage;
				incompatibleCount = 0;
				for (ModuleNode<?> rightToNode : unmatchedRightNodes) {
					compatible = false;
					for (Edge<ModuleNode<?>> leftEdge : leftEdges) {
						ModuleNode<?> leftToNode = leftEdge.getToNode();
						if (leftToNode.getHash() == rightToNode.getHash()) {
							leftCoverageSet.clear();
							rightCoverageSet.clear();
							maxExploredDepth.setVal(Integer.MAX_VALUE);
							maxCompatibleDepth.setVal(Integer.MAX_VALUE);
							visitedLeftNodes.clear();
							if (isCompatible(leftToNode, rightToNode, Integer.MAX_VALUE)) {
								leftCompatibleCoverageSet.addAll(leftCoverageSet);
								rightCompatibleCoverageSet.addAll(rightCoverageSet);
								compatibleSubgraphDepth = Integer.MAX_VALUE - maxCompatibleDepth.getVal();
								compatibleSubgraphDepths.add(compatibleSubgraphDepth);
								if (compatibleSubgraphDepth > compatibleSubgraphMaxDepth)
									compatibleSubgraphMaxDepth = compatibleSubgraphDepth;
								compatible = true;
								break;
							}
						}
					}

					if (compatible)
						compatibleCount++;
					else
						incompatibleCount++;
				}
				Log.log("\tEntry point 0x%x per right node: %d compatible and %d incompatible", entryHash,
						compatibleCount, incompatibleCount);
				Log.log("\tCoverage of compatible entry subgraphs: %d left, %d right",
						leftCompatibleCoverageSet.size(), rightCompatibleCoverageSet.size());

				int totalCompatibleSubgraphDepth = 0;
				for (int next : compatibleSubgraphDepths) {
					totalCompatibleSubgraphDepth += next;
				}
				float averageCompatibleSubgraphDepth = (compatibleSubgraphDepths.size() == 0) ? 0f
						: (totalCompatibleSubgraphDepth / (float) compatibleSubgraphDepths.size());
				Log.log("\tAverage compatible subgraph depth: %.2f; max: %d", averageCompatibleSubgraphDepth,
						compatibleSubgraphMaxDepth);
			} finally {
				leftEdges.release();
				rightEdges.release();
			}
		}
	}

	private boolean isCompatible(ModuleNode<?> leftNode, ModuleNode<?> rightNode, int depth) {
		if (visitedLeftNodes.contains(leftNode))
			return true;
		visitedLeftNodes.add(leftNode);

		if ((maxExploredDepth != null) && (depth < maxExploredDepth.getVal()))
			maxExploredDepth.setVal(depth);

		int ordinalCount = leftNode.getOutgoingOrdinalCount();
		if ((depth > 0) && (leftNode.getType() != MetaNodeType.MODULE_ENTRY) && (ordinalCount != 0)) {
			ordinals: for (int ordinal = 0; ordinal < ordinalCount; ordinal++) {
				OrdinalEdgeList<ModuleNode<?>> leftEdges = leftNode.getOutgoingEdges(ordinal);
				OrdinalEdgeList<ModuleNode<?>> rightEdges = rightNode.getOutgoingEdges(ordinal);
				try {
					if (leftEdges.isEmpty() || rightEdges.isEmpty())
						continue;

					EdgeType leftEdgeType = leftEdges.get(0).getEdgeType();
					EdgeType rightEdgeType = rightEdges.get(0).getEdgeType();

					if (leftEdgeType != rightEdgeType) {
						Log.log("Hash collision: edge types differ for hash 0x%x at ordinal %d!", leftNode.getHash(),
								ordinal);
						visitedLeftNodes.remove(leftNode);
						return false;
					}

					switch (leftEdgeType) {
						case DIRECT:
						case CALL_CONTINUATION:
						case EXCEPTION_CONTINUATION: {
							for (Edge<ModuleNode<?>> leftEdge : leftEdges) {
								ModuleNode<?> leftToNode = leftEdge.getToNode();
								for (Edge<ModuleNode<?>> rightEdge : rightEdges) {
									ModuleNode<?> rightToNode = rightEdge.getToNode();
									if (leftToNode.getHash() == rightToNode.getHash()) {
										if (isCompatible(leftToNode, rightToNode, depth - 1))
											continue ordinals;
									}
								}
							}
							visitedLeftNodes.remove(leftNode);
							return false;
						}
						case INDIRECT:
						case UNEXPECTED_RETURN:
						case GENCODE_PERM:
						case GENCODE_WRITE:
							for (Edge<ModuleNode<?>> leftEdge : leftEdges) {
								ModuleNode<?> leftToNode = leftEdge.getToNode();
								for (Edge<ModuleNode<?>> rightEdge : rightEdges) {
									ModuleNode<?> rightToNode = rightEdge.getToNode();
									if (leftToNode.getHash() == rightToNode.getHash()) {
										if (followIndirectBranches)
											if (!isCompatible(leftToNode, rightToNode, depth - 1))
												continue;
										continue ordinals;
									}
								}
							}
							visitedLeftNodes.remove(leftNode);
							return false;
					}
					break;
				} finally {
					leftEdges.release();
					rightEdges.release();
				}
			}
		}

		if ((maxCompatibleDepth != null) && (depth < maxCompatibleDepth.getVal()))
			maxCompatibleDepth.setVal(depth);
		if (leftCoverageSet != null)
			leftCoverageSet.add(leftNode);
		if (rightCoverageSet != null)
			rightCoverageSet.add(rightNode);

		return true;
	}
}
