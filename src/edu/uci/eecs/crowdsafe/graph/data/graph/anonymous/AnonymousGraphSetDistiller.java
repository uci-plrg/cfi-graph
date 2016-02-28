package edu.uci.eecs.crowdsafe.graph.data.graph.anonymous;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModuleSet;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleBasicBlock;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleBoundaryNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;

public class AnonymousGraphSetDistiller {

	private enum MergeResult {
		DISTINCT,
		SUBSUMED,
		COMPATIBLE
	}

	private abstract class Frame<T> {
		abstract void activate(T left, T right);

		abstract void deactivate();
	}

	private interface FrameFactory<FrameType extends Frame<?>> {
		FrameType createFrame();
	}

	private static class MergeStack<FrameData, FrameType extends Frame<FrameData>> {
		private final List<FrameType> frames = new ArrayList<FrameType>();
		private int framePointer = -1;
		private FrameType top = null;
		private FrameFactory<FrameType> factory;

		public MergeStack(FrameFactory<FrameType> factory) {
			this.factory = factory;
		}

		private void expand(int limit) {
			for (int i = frames.size(); i <= limit; i++)
				frames.add(factory.createFrame());
		}

		void reset() {
			framePointer = -1;
			top = null;
		}

		void push(FrameData left, FrameData right) {
			expand(framePointer + 1);

			FrameType push = frames.get(++framePointer);
			push.activate(left, right);
			top = push;
		}

		void pop() {
			top.deactivate();
			top = frames.get(--framePointer);
		}

		boolean isBaseFrame() {
			return framePointer == 0;
		}

		int size() {
			return framePointer + 1;
		}
	}

	private class NodeFrameFactory implements FrameFactory<NodeFrame> {
		@Override
		public NodeFrame createFrame() {
			return new NodeFrame();
		}
	}

	private class NodeFrame extends Frame<ModuleNode<?>> {
		final List<Edge<ModuleNode<?>>> leftEdges = new ArrayList<Edge<ModuleNode<?>>>();
		final List<Edge<ModuleNode<?>>> rightEdges = new ArrayList<Edge<ModuleNode<?>>>();

		final MergeStack<Edge<ModuleNode<?>>, EdgeFrame> edgeStack = new MergeStack(new EdgeFrameFactory());

		ModuleNode<?> left, right;

		@Override
		void activate(ModuleNode<?> left, ModuleNode<?> right) {
			this.left = left;
			this.right = right;

			leftEdges.clear();
			rightEdges.clear();
			OrdinalEdgeList<ModuleNode<?>> edges = left.getOutgoingEdges();
			try {
				leftEdges.addAll(edges);
			} finally {
				edges.release();
			}
			edges = right.getOutgoingEdges();
			try {
				rightEdges.addAll(edges);
			} finally {
				edges.release();
			}

			edgeStack.reset();

			visitedLeftNodes.add(left);
			visitedRightNodes.add(right);
		}

		@Override
		void deactivate() {
			leftEdges.clear();
			rightEdges.clear();

			visitedLeftNodes.remove(left);
			visitedRightNodes.remove(right);
		}

		void pushEdgePair(int leftIndex, int rightIndex) {
			edgeStack.push(leftEdges.get(leftIndex), rightEdges.get(rightIndex));
			leftEdges.set(leftIndex, null);
			rightEdges.set(rightIndex, null);
			edgeStack.top.leftIndex = leftIndex;
			edgeStack.top.rightIndex = rightIndex;
		}

		void changeLeftEdge(int leftIndex) {
			leftEdges.set(edgeStack.top.leftIndex, edgeStack.top.left);
			edgeStack.top.changeLeftEdge(leftIndex, leftEdges.get(leftIndex));
			leftEdges.set(edgeStack.top.leftIndex, null);
		}

		void changeRightEdge(int rightIndex) {
			rightEdges.set(edgeStack.top.rightIndex, edgeStack.top.right);
			edgeStack.top.changeRightEdge(rightIndex, rightEdges.get(rightIndex));
			rightEdges.set(edgeStack.top.rightIndex, null);
		}
	}

	private class EdgeFrameFactory implements FrameFactory<EdgeFrame> {
		@Override
		public EdgeFrame createFrame() {
			return new EdgeFrame();
		}
	}

	private class EdgeFrame extends Frame<Edge<ModuleNode<?>>> {
		Edge<ModuleNode<?>> left, right;
		ModuleNode<?> leftNode, rightNode;
		int leftIndex, rightIndex;

		@Override
		void activate(Edge<ModuleNode<?>> left, Edge<ModuleNode<?>> right) {
			this.left = left;
			this.right = right;
			leftNode = left.getToNode();
			rightNode = right.getToNode();
		}

		@Override
		void deactivate() {
		}

		boolean isCompatible() {
			return left.getOrdinal() == right.getOrdinal() && leftNode.getType() == rightNode.getType()
					&& leftNode.getHash() == rightNode.getHash() && !visitedLeftNodes.contains(leftNode)
					&& !visitedRightNodes.contains(rightNode);
		}

		void changeLeftEdge(int index, Edge<ModuleNode<?>> edge) {
			leftIndex = index;
			left = edge;
			leftNode = edge.getToNode();
		}

		void changeRightEdge(int index, Edge<ModuleNode<?>> edge) {
			rightIndex = index;
			right = edge;
			rightNode = edge.getToNode();
		}
	}

	public static void distillGraphs(ApplicationAnonymousGraphs graphs) {
		AnonymousGraphSetDistiller distiller = new AnonymousGraphSetDistiller();

		for (ApplicationModule owner : graphs.getOwners())
			distiller.distillGraphs(graphs.getOwnerGraphs(owner).subgraphs);
	}

	public static void distillGraphs(ApplicationAnonymousGraphs newGraphs, ApplicationAnonymousGraphs destinationGraphs) {
		AnonymousGraphSetDistiller distiller = new AnonymousGraphSetDistiller();

		// TODO: always merge JIT graph by union
		
		for (ApplicationModule owner : newGraphs.getOwners()) {
			ModuleAnonymousGraphs existingGraphs = destinationGraphs.getOwnerGraphs(owner);
			if (existingGraphs != null)
				distiller.distillGraphs(newGraphs.getOwnerGraphs(owner).subgraphs, existingGraphs.subgraphs);
		}
	}

	private final MergeStack<ModuleNode<?>, NodeFrame> mergeStack = new MergeStack(new NodeFrameFactory());
	private final Set<ModuleNode<?>> visitedLeftNodes = new HashSet<ModuleNode<?>>();
	private final Set<ModuleNode<?>> visitedRightNodes = new HashSet<ModuleNode<?>>();

	public void distillGraphs(List<AnonymousGraph> graphs) {
		boolean changed = true;
		while (changed) {
			changed = false;
			for (int i = graphs.size() - 1; i > 0; i--) {
				AnonymousGraph left = graphs.get(i);
				for (int j = i - 1; j >= 0; j--) {
					AnonymousGraph right = graphs.get(j);
					MergeResult result = merge(left, right);

					Log.log("Merge graph #%d into #%d: %s", i, j, result);

					if (result != MergeResult.DISTINCT) {
						graphs.remove(i);
						changed = true;
						break;
					}
				}
			}
		}
	}

	public void distillGraphs(List<AnonymousGraph> newGraphs, List<AnonymousGraph> destinationGraphs) {
		for (int i = newGraphs.size() - 1; i >= 0; i--) {
			AnonymousGraph newGraph = newGraphs.get(i);
			for (int j = 0; j < destinationGraphs.size(); j++) {
				AnonymousGraph existingGraph = destinationGraphs.get(j);
				MergeResult result = merge(newGraph, existingGraph);

				if (result != MergeResult.DISTINCT) {
					newGraphs.remove(i);
					break;
				}
			}
		}

		List<AnonymousGraph> combinedGraphs = new ArrayList<AnonymousGraph>();
		int i = 1, j = 1;
		AnonymousGraph newGraph = newGraphs.get(0);
		AnonymousGraph existingGraph = destinationGraphs.get(0);
		while (true) {
			if (newGraph.getNodeCount() > existingGraph.getNodeCount()) {
				combinedGraphs.add(newGraph);
				if (i < newGraphs.size()) {
					newGraph = newGraphs.get(i++);
				} else {
					if (j < destinationGraphs.size())
						combinedGraphs.addAll(destinationGraphs.subList(j - 1, destinationGraphs.size()));
					else
						combinedGraphs.add(existingGraph);
					break;
				}
			} else {
				combinedGraphs.add(existingGraph);
				if (j < destinationGraphs.size()) {
					existingGraph = destinationGraphs.get(j++);
				} else {
					if (i < newGraphs.size())
						combinedGraphs.addAll(newGraphs.subList(i - 1, newGraphs.size()));
					else
						combinedGraphs.add(newGraph);
					break;
				}
			}
		}

		distillGraphs(combinedGraphs);
		destinationGraphs.clear();
		destinationGraphs.addAll(combinedGraphs);
	}

	private ModuleNode<?> createMetaEntryNode(AnonymousGraph graph) {
		ModuleNode<?> metaEntryNode = new ModuleBasicBlock(new ModuleBasicBlock.Key(ApplicationModule.ANONYMOUS_MODULE,
				0, 0), 0L, MetaNodeType.SINGLETON);
		for (ModuleNode<?> entryNode : graph.getEntryPoints()) {
			Edge<ModuleNode<?>> metaEntryEdge = new Edge<ModuleNode<?>>(metaEntryNode, entryNode, EdgeType.INDIRECT, 0);
			metaEntryNode.addOutgoingEdge(metaEntryEdge);
		}
		return metaEntryNode;
	}

	private MergeResult merge(AnonymousGraph left, AnonymousGraph right) {
		if (left.getEntryPoints().size() != right.getEntryPoints().size())
			return MergeResult.DISTINCT;

		for (Long leftEntryHash : left.getEntryHashes()) {
			if (right.getEntryPoint(leftEntryHash) == null)
				return MergeResult.DISTINCT;
		}

		// TODO: quick filter by node hashes also

		visitedLeftNodes.clear();
		visitedRightNodes.clear();
		mergeStack.reset();
		ModuleNode<?> leftMetaEntry = createMetaEntryNode(left);
		ModuleNode<?> rightMetaEntry = createMetaEntryNode(right);
		mergeStack.push(leftMetaEntry, rightMetaEntry);

		merge_stack: while (true) {
			mergeStack.top.pushEdgePair(0, 0);
			left_edges: for (int i = mergeStack.top.edgeStack.top.leftIndex + 1; i <= mergeStack.top.leftEdges.size(); i++) {
				for (int j = mergeStack.top.edgeStack.top.rightIndex + 1; j <= mergeStack.top.rightEdges.size(); j++) {
					if (mergeStack.top.edgeStack.top.isCompatible()) {
						switch (mergeStack.top.edgeStack.top.leftNode.getType()) {
							case MODULE_EXIT:
							case RETURN:
								while (true) {
									if (mergeStack.top.edgeStack.size() >= mergeStack.top.leftEdges.size()) {
										if (mergeStack.isBaseFrame())
											return MergeResult.SUBSUMED;
										mergeStack.pop();
										i = mergeStack.top.edgeStack.top.leftIndex;
									} else if (mergeStack.top.edgeStack.top.rightIndex >= mergeStack.top.rightEdges
											.size()) {
										if (mergeStack.isBaseFrame())
											return MergeResult.DISTINCT;
										mergeStack.pop();
										i = mergeStack.top.edgeStack.top.leftIndex;
									} else {
										j = 0;
										while (j < mergeStack.top.rightEdges.size()
												&& mergeStack.top.rightEdges.get(j) == null)
											j++;
										if (j == mergeStack.top.rightEdges.size()) {
											if (mergeStack.isBaseFrame())
												return MergeResult.DISTINCT;
											mergeStack.pop();
											i = mergeStack.top.edgeStack.top.leftIndex;
											continue;
										}
										break;
									}
								}
								mergeStack.top.edgeStack.top.rightIndex = j;
								mergeStack.top.pushEdgePair(i, j);
								continue left_edges; // continue merging left edges
							default:
								mergeStack.top.edgeStack.top.leftIndex = i;
								mergeStack.top.edgeStack.top.rightIndex = j;
								mergeStack.push(mergeStack.top.edgeStack.top.leftNode,
										mergeStack.top.edgeStack.top.rightNode);
								continue merge_stack;
						}
					} else {
						while (j < mergeStack.top.rightEdges.size() && mergeStack.top.rightEdges.get(j) == null)
							j++;
						if (j == mergeStack.top.rightEdges.size()) {
							if (mergeStack.isBaseFrame())
								return MergeResult.DISTINCT;
							mergeStack.pop(); // edge match failed, so contrinue trying right edges
							i = mergeStack.top.edgeStack.top.leftIndex;
							j = mergeStack.top.edgeStack.top.rightIndex;
						} else {
							mergeStack.top.changeRightEdge(j);
						}
					}
				}
				return MergeResult.DISTINCT;
			}
			throw new IllegalStateException("unreachable");
		}
	}

	private static class UnitTestGraph {
		private static int GRAPH_INDEX = 0;

		private final AnonymousGraph graph = new AnonymousGraph("Unit test graph #" + (GRAPH_INDEX++));
		private final List<ModuleNode<?>> nodesByTag = new ArrayList<ModuleNode<?>>();

		private void addNode(ModuleNode<?> node) {
			graph.addNode(node);
			nodesByTag.add(node);
		}

		void addNodes(MetaNodeType type, long... hashes) {
			if (type == MetaNodeType.MODULE_ENTRY || type == MetaNodeType.MODULE_EXIT) {
				for (long hash : hashes)
					addNode(new ModuleBoundaryNode(hash, type));
			} else {
				for (long hash : hashes)
					addNode(new ModuleBasicBlock(ApplicationModule.ANONYMOUS_MODULE, nodesByTag.size(), 0, hash, type));
			}
		}

		void addEdge(int fromTag, int toTag, EdgeType type, int ordinal) {
			ModuleNode<?> fromNode = nodesByTag.get(fromTag - 1);
			fromNode.addOutgoingEdge(new Edge<ModuleNode<?>>(fromNode, nodesByTag.get(toTag - 1), type, ordinal));
		}
	}

	private static class UnitTest {
		void test1() {
			UnitTestGraph graph1 = new UnitTestGraph();
			graph1.addNodes(MetaNodeType.MODULE_ENTRY, 10);
			graph1.addNodes(MetaNodeType.NORMAL, 20, 30, 40);
			graph1.addNodes(MetaNodeType.MODULE_EXIT, 50);
			graph1.addEdge(1, 2, EdgeType.INDIRECT, 0);
			graph1.addEdge(2, 3, EdgeType.DIRECT, 0);
			graph1.addEdge(2, 4, EdgeType.DIRECT, 1);
			graph1.addEdge(3, 5, EdgeType.DIRECT, 0);
			graph1.addEdge(4, 5, EdgeType.DIRECT, 0);

			UnitTestGraph graph2 = new UnitTestGraph();
			graph2.addNodes(MetaNodeType.MODULE_ENTRY, 10);
			graph2.addNodes(MetaNodeType.NORMAL, 20, 30, 40);
			graph2.addNodes(MetaNodeType.MODULE_EXIT, 50);
			graph2.addEdge(1, 2, EdgeType.INDIRECT, 0);
			graph2.addEdge(2, 3, EdgeType.DIRECT, 0);
			graph2.addEdge(2, 4, EdgeType.DIRECT, 1);
			graph2.addEdge(3, 5, EdgeType.DIRECT, 0);
			graph2.addEdge(4, 5, EdgeType.DIRECT, 0);

			UnitTestGraph graph3 = new UnitTestGraph();
			graph3.addNodes(MetaNodeType.MODULE_ENTRY, 10);
			graph3.addNodes(MetaNodeType.NORMAL, 20, 30);
			graph3.addNodes(MetaNodeType.MODULE_EXIT, 50);
			graph3.addEdge(1, 2, EdgeType.INDIRECT, 0);
			graph3.addEdge(2, 3, EdgeType.DIRECT, 0);
			graph3.addEdge(3, 4, EdgeType.DIRECT, 0);

			UnitTestGraph graph4 = new UnitTestGraph();
			graph4.addNodes(MetaNodeType.MODULE_ENTRY, 10);
			graph4.addNodes(MetaNodeType.NORMAL, 20, 30, 60);
			graph4.addNodes(MetaNodeType.MODULE_EXIT, 50);
			graph4.addEdge(1, 2, EdgeType.INDIRECT, 0);
			graph4.addEdge(2, 3, EdgeType.DIRECT, 0);
			graph4.addEdge(2, 4, EdgeType.DIRECT, 1);
			graph4.addEdge(3, 5, EdgeType.DIRECT, 0);
			graph4.addEdge(4, 5, EdgeType.DIRECT, 0);

			UnitTestGraph graph5 = new UnitTestGraph();
			graph5.addNodes(MetaNodeType.MODULE_ENTRY, 10);
			graph5.addNodes(MetaNodeType.NORMAL, 20, 60);
			graph5.addNodes(MetaNodeType.MODULE_EXIT, 50);
			graph5.addEdge(1, 2, EdgeType.INDIRECT, 0);
			graph5.addEdge(2, 3, EdgeType.DIRECT, 0);
			graph5.addEdge(3, 4, EdgeType.DIRECT, 0);

			UnitTestGraph graph6 = new UnitTestGraph();
			graph6.addNodes(MetaNodeType.MODULE_ENTRY, 10);
			graph6.addNodes(MetaNodeType.NORMAL, 20, 60);
			graph6.addNodes(MetaNodeType.MODULE_EXIT, 50);
			graph6.addEdge(1, 2, EdgeType.INDIRECT, 0);
			graph6.addEdge(2, 3, EdgeType.DIRECT, 1);
			graph6.addEdge(3, 4, EdgeType.DIRECT, 0);

			List<AnonymousGraph> graphs = new ArrayList<AnonymousGraph>();
			graphs.add(graph1.graph);
			graphs.add(graph2.graph);
			graphs.add(graph3.graph);
			graphs.add(graph4.graph);
			graphs.add(graph5.graph);
			graphs.add(graph6.graph);

			AnonymousGraphSetDistiller distiller = new AnonymousGraphSetDistiller();
			distiller.distillGraphs(graphs);

			Log.log("Unit test ended with %d graphs", graphs.size());
		}
	}

	/* unit test */
	public static void main(String[] args) {
		ApplicationModuleSet.initialize(null);
		Log.addOutput(System.out);

		UnitTest test = new UnitTest();
		test.test1();
	}
}
