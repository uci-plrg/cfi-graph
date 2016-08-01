package edu.uci.plrg.cfi.x86.graph.data.graph.anonymous;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.uci.plrg.cfi.common.exception.InvalidGraphException;
import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.x86.graph.data.graph.Edge;
import edu.uci.plrg.cfi.x86.graph.data.graph.ModuleGraph;
import edu.uci.plrg.cfi.x86.graph.data.graph.OrdinalEdgeList;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ModuleBoundaryNode;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ModuleNode;

public class MaximalSubgraphs {

	private static class Subgraph {
		final AnonymousGraph graph;
		final Map<Long, ModuleBoundaryNode> boundaryNodes = new HashMap<Long, ModuleBoundaryNode>();

		public Subgraph(ModuleGraph<ModuleNode<?>> originalGraph) {
			graph = new AnonymousGraph("Anonymous maximal subgraph");
		}

		void addClusterBoundaryEdge(ModuleBoundaryNode node, Edge<ModuleNode<?>> edge) {
			ModuleBoundaryNode subgraphBoundaryNode = boundaryNodes.get(node.getHash());
			if (subgraphBoundaryNode == null) {
				subgraphBoundaryNode = new ModuleBoundaryNode(node.getHash(), node.getType());
				graph.addNode(subgraphBoundaryNode);
				boundaryNodes.put(subgraphBoundaryNode.getHash(), subgraphBoundaryNode);
			}

			switch (node.getType()) {
				case MODULE_ENTRY: {
					Edge<ModuleNode<?>> patchEdge = new Edge<ModuleNode<?>>(subgraphBoundaryNode, edge.getToNode(),
							edge.getEdgeType(), edge.getOrdinal());
					subgraphBoundaryNode.addOutgoingEdge(patchEdge);
					edge.getToNode().replaceEdge(edge, patchEdge);
					break;
				}
				case MODULE_EXIT: {
					Edge<ModuleNode<?>> patchEdge = new Edge<ModuleNode<?>>(edge.getFromNode(), subgraphBoundaryNode,
							edge.getEdgeType(), edge.getOrdinal());
					subgraphBoundaryNode.addIncomingEdge(patchEdge);
					edge.getFromNode().replaceEdge(edge, patchEdge);
					break;
				}
				default:
					; /* nothing */
			}
		}

		void mergeClusterBoundaryNode(ModuleBoundaryNode mergingSubgraphBoundaryNode) {
			ModuleBoundaryNode subgraphBoundaryNode = boundaryNodes.get(mergingSubgraphBoundaryNode.getHash());
			if (subgraphBoundaryNode == null) {
				graph.addNode(mergingSubgraphBoundaryNode);
				boundaryNodes.put(mergingSubgraphBoundaryNode.getHash(), mergingSubgraphBoundaryNode);
			} else {
				switch (mergingSubgraphBoundaryNode.getType()) {
					case MODULE_ENTRY: {
						OrdinalEdgeList<ModuleNode<?>> mergingEdgeList = mergingSubgraphBoundaryNode.getOutgoingEdges();
						try {
							for (Edge<ModuleNode<?>> mergingEntryEdge : mergingEdgeList) {
								Edge<ModuleNode<?>> replacement = new Edge<ModuleNode<?>>(subgraphBoundaryNode,
										mergingEntryEdge.getToNode(), mergingEntryEdge.getEdgeType(),
										mergingEntryEdge.getOrdinal());
								mergingEntryEdge.getToNode().replaceEdge(mergingEntryEdge, replacement);
								subgraphBoundaryNode.addOutgoingEdge(replacement);
							}
						} finally {
							mergingEdgeList.release();
						}
						break;
					}
					case MODULE_EXIT: {
						OrdinalEdgeList<ModuleNode<?>> mergingEdgeList = mergingSubgraphBoundaryNode.getIncomingEdges();
						try {
							for (Edge<ModuleNode<?>> mergingExitEdge : mergingEdgeList) {
								Edge<ModuleNode<?>> replacement = new Edge<ModuleNode<?>>(
										mergingExitEdge.getFromNode(), subgraphBoundaryNode,
										mergingExitEdge.getEdgeType(), mergingExitEdge.getOrdinal());
								mergingExitEdge.getFromNode().replaceEdge(mergingExitEdge, replacement);
								subgraphBoundaryNode.addIncomingEdge(replacement);
							}
						} finally {
							mergingEdgeList.release();
						}
						break;
					}
					default:
						; /* nothing */
				}
			}
		}

		void addFrontierEntryEdge(Edge<ModuleNode<?>> edge) {
			ModuleBoundaryNode globalEntryNode = (ModuleBoundaryNode) edge.getFromNode();
			ModuleBoundaryNode subgraphBoundaryNode = boundaryNodes.get(globalEntryNode.getHash());
			if (subgraphBoundaryNode == null) {
				subgraphBoundaryNode = new ModuleBoundaryNode(globalEntryNode.getHash(), globalEntryNode.getType());
				graph.addNode(subgraphBoundaryNode);
				boundaryNodes.put(globalEntryNode.getHash(), subgraphBoundaryNode);
			}

			Edge<ModuleNode<?>> replacement = new Edge<ModuleNode<?>>(subgraphBoundaryNode, edge.getToNode(),
					edge.getEdgeType(), edge.getOrdinal());
			edge.getToNode().replaceEdge(edge, replacement);
			subgraphBoundaryNode.addOutgoingEdge(replacement);
		}

		void addFrontierExitEdge(Edge<ModuleNode<?>> edge) {
			ModuleBoundaryNode globalExitNode = (ModuleBoundaryNode) edge.getToNode();
			ModuleBoundaryNode subgraphBoundaryNode = boundaryNodes.get(globalExitNode.getHash());
			if (subgraphBoundaryNode == null) {
				subgraphBoundaryNode = new ModuleBoundaryNode(globalExitNode.getHash(), globalExitNode.getType());
				graph.addNode(subgraphBoundaryNode);
				boundaryNodes.put(globalExitNode.getHash(), subgraphBoundaryNode);
			}

			Edge<ModuleNode<?>> replacement = new Edge<ModuleNode<?>>(edge.getFromNode(), subgraphBoundaryNode,
					edge.getEdgeType(), edge.getOrdinal());
			edge.getFromNode().replaceEdge(edge, replacement);
			subgraphBoundaryNode.addIncomingEdge(replacement);
		}
	}

	// this method modifies `graph`
	public static Set<AnonymousGraph> getMaximalSubgraphs(ModuleGraph<ModuleNode<?>> graph) {
		MaximalSubgraphs processor = new MaximalSubgraphs(graph);

		for (ModuleNode<?> node : graph.getAllNodes()) {
			if (node.getType().isApplicationNode)
				processor.atoms.add(node);
		}

		for (ModuleNode<?> node : graph.getAllNodes()) {
			OrdinalEdgeList<ModuleNode<?>> edgeList = node.getOutgoingEdges();
			try {
				for (Edge<ModuleNode<?>> edge : edgeList) {
					processor.addEdge(edge);
				}
			} finally {
				edgeList.release();
			}
		}

		return processor.distinctSubgraphs;
	}

	private final ModuleGraph<ModuleNode<?>> originalGraph;

	private final Set<ModuleNode<?>> atoms = new HashSet<ModuleNode<?>>();
	private final Map<ModuleNode<?>, Subgraph> subgraphs = new HashMap<ModuleNode<?>, Subgraph>();
	private final Set<AnonymousGraph> distinctSubgraphs = new HashSet<AnonymousGraph>();

	private MaximalSubgraphs(ModuleGraph<ModuleNode<?>> originalGraph) {
		this.originalGraph = originalGraph;
	}

	private void addEdge(Edge<ModuleNode<?>> edge) {
		ModuleNode<?> fromAtom = consumeFromAtom(edge);
		ModuleNode<?> toAtom = consumeToAtom(edge);

		Log.log("Process edge: %s | from: %s | to: %s", edge, fromAtom, toAtom);

		if (edge.getFromNode().getType().isApplicationNode) {
			if (edge.getToNode().getType().isApplicationNode) {

				Log.log("\tBoth sides executable");

				if (fromAtom == null) {
					if (toAtom == null) {
						addConsumedEdge(edge);

						Log.log("\tAdd consumed edge");
					} else {
						Subgraph subgraph = subgraphs.get(edge.getFromNode());
						subgraph.graph.addNode(toAtom);
						subgraphs.put(toAtom, subgraph);

						Log.log("\tAttach <to> to <from>'s subgraph (0x%x)", subgraph.graph.hashCode());
					}
				} else {
					if (edge.getFromNode() == edge.getToNode())
						toAtom = fromAtom;
					if (toAtom == null) {
						Subgraph subgraph = subgraphs.get(edge.getToNode());
						subgraph.graph.addNode(fromAtom);
						subgraphs.put(fromAtom, subgraph);

						Log.log("\tAttach <from> to <to>'s subgraph (0x%x)", subgraph.graph.hashCode());
						subgraph.graph.logGraph();
					} else {
						addIsolatedEdge(edge);

						Log.log("\tAdd isolated edge");
					}
				}
			} else { /* cluster exit */
				Log.log("\tCluster exit");

				if (fromAtom == null) { /* already in a subgraph */
					Subgraph subgraph = subgraphs.get(edge.getFromNode());
					subgraph.addFrontierExitEdge(edge);

					Log.log("\tAdd frontier exit edge to 0x%x", subgraph.graph.hashCode());
				} else {
					addIsolatedEdge(edge);

					Log.log("\tAdd isolated edge");
				}
			}
		} else { /* cluster entry */
			Log.log("\tCluster entry");

			if (!edge.getToNode().getType().isApplicationNode)
				throw new InvalidGraphException("Cluster entry links directly to cluster exit:\n%s", edge);

			if (toAtom == null) { /* already in a subgraph */
				Subgraph subgraph = subgraphs.get(edge.getToNode());
				subgraph.addFrontierEntryEdge(edge);

				Log.log("\tAdd frontier entry edge to 0x%x", subgraph.graph.hashCode());
			} else {
				addIsolatedEdge(edge);

				Log.log("\tAdd isolated edge");
			}
		}
	}

	private void addIsolatedEdge(Edge<ModuleNode<?>> edge) {
		Subgraph subgraph = addSubgraph();
		ModuleNode<?> fromNode = edge.getFromNode();
		if (fromNode.getType().isApplicationNode) {
			subgraphs.put(fromNode, subgraph);
			subgraph.graph.addNode(fromNode);
		} else {
			subgraph.addClusterBoundaryEdge((ModuleBoundaryNode) fromNode, edge);
		}

		ModuleNode<?> toNode = edge.getToNode();
		if (toNode.getType().isApplicationNode) {
			subgraphs.put(toNode, subgraph);
			subgraph.graph.addNode(toNode);
		} else {
			subgraph.addClusterBoundaryEdge((ModuleBoundaryNode) toNode, edge);
		}
	}

	private void addConsumedEdge(Edge<ModuleNode<?>> edge) {
		Subgraph fromSubgraph = subgraphs.get(edge.getFromNode());
		Subgraph toSubgraph = subgraphs.get(edge.getToNode());
		if ((fromSubgraph != null) && (toSubgraph != null) && (fromSubgraph != toSubgraph)) {
			Subgraph smallSubgraph, largeSubgraph;
			if (fromSubgraph.graph.getNodeCount() < toSubgraph.graph.getNodeCount()) {
				smallSubgraph = fromSubgraph;
				largeSubgraph = toSubgraph;
			} else {
				smallSubgraph = toSubgraph;
				largeSubgraph = fromSubgraph;
			}
			for (ModuleNode<?> node : smallSubgraph.graph.getAllNodes()) {
				switch (node.getType()) {
					case MODULE_ENTRY:
					case MODULE_EXIT:
						largeSubgraph.mergeClusterBoundaryNode((ModuleBoundaryNode) node);
						break;
					default:
						largeSubgraph.graph.addNode(node);
						subgraphs.put(node, largeSubgraph);
				}
			}
			Log.log("Merged subgraph 0x%x into 0x%x", smallSubgraph.graph.hashCode(), largeSubgraph.graph.hashCode());
			Log.log("\tConsumed: ");
			smallSubgraph.graph.logGraph();
			Log.log("\tInto: ");
			largeSubgraph.graph.logGraph();
			distinctSubgraphs.remove(smallSubgraph.graph);
		}
	}

	private ModuleNode<?> consumeFromAtom(Edge<ModuleNode<?>> edge) {
		ModuleNode<?> fromNode = edge.getFromNode();
		if (!atoms.remove(fromNode))
			return null;
		return fromNode;
	}

	private ModuleNode<?> consumeToAtom(Edge<ModuleNode<?>> edge) {
		ModuleNode<?> toNode = edge.getToNode();
		if (!atoms.remove(toNode))
			return null;
		return toNode;
	}

	private Subgraph addSubgraph() {
		Subgraph subgraph = new Subgraph(originalGraph);
		Log.log("Adding subgraph 0x%x", subgraph.graph.hashCode());
		distinctSubgraphs.add(subgraph.graph);
		return subgraph;
	}
}
