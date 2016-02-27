package edu.uci.eecs.crowdsafe.graph.data.graph.anonymous;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleBasicBlock;
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

	private class MergeStack<FrameData, FrameType extends Frame<FrameData>> {
		private final List<FrameType> frames = new ArrayList<FrameType>();
		private int framePointer = 0;
		private FrameType top = null;
		private Class<FrameType> frameType;

		public MergeStack(Class<FrameType> frameType) {
			this.frameType = frameType;
		}

		private void expand() {
			try {
				for (int i = frames.size(); i <= framePointer; i++)
					frames.add(frameType.newInstance());
			} catch (InstantiationException e) {
				throw new IllegalStateException(e);
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}

		void reset() {
			framePointer = 0;
			top = null;
		}

		void push(FrameData left, FrameData right) {
			expand();

			FrameType push = frames.get(framePointer++);
			push.activate(left, right);
			top = push;
		}

		void pop() {
			top.deactivate();
			top = frames.get(--framePointer);
		}
	}

	private class NodeFrame extends Frame<ModuleNode<?>> {
		final List<Edge<ModuleNode<?>>> leftEdges = new ArrayList<Edge<ModuleNode<?>>>();
		final List<Edge<ModuleNode<?>>> rightEdges = new ArrayList<Edge<ModuleNode<?>>>();

		final MergeStack<Edge<ModuleNode<?>>, EdgeFrame> edgeStack = new MergeStack(EdgeFrame.class);

		ModuleNode<?> left, right;

		@Override
		void activate(ModuleNode<?> left, ModuleNode<?> right) {
			this.left = left;
			this.right = right;

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
		}

		void changeRightEdge(int rightIndex) {
			rightEdges.set(edgeStack.top.rightIndex, edgeStack.top.right);
			edgeStack.top.changeRightEdge(rightIndex, rightEdges.get(rightIndex));
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

	private final MergeStack<ModuleNode<?>, NodeFrame> mergeStack = new MergeStack(NodeFrame.class);
	private final Set<ModuleNode<?>> visitedLeftNodes = new HashSet<ModuleNode<?>>();
	private final Set<ModuleNode<?>> visitedRightNodes = new HashSet<ModuleNode<?>>();

	private List<AnonymousGraph> graphs;

	public void distillGraphs(List<AnonymousGraph> graphs) {
		this.graphs = graphs;

		boolean changed = true;
		while (changed) {
			changed = false;
			for (int i = graphs.size() - 1; i > 1; i--) {
				AnonymousGraph left = graphs.get(i);
				for (int j = i - 1; j > 0; j--) {
					AnonymousGraph right = graphs.get(j);
					MergeResult result = merge(left, right);

					if (result != MergeResult.DISTINCT) {
						graphs.remove(i);
						changed = true;
						break;
					}
				}
			}
		}
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

	/*
	 * For each edge on the left, find a subsuming edge on the right. Any node may have multiple neighbors with the same
	 * hash, so we have to check every permutation of edge pairings. There should never be a large number of targets, so
	 * we could just try every permutation of edges. Would it help to sort them by ordinal and target hash? Seems like I
	 * still need a stack of them.
	 */

	private MergeResult merge(AnonymousGraph left, AnonymousGraph right) {
		if (left.getEntryPoints().size() != right.getEntryPoints().size())
			return MergeResult.DISTINCT;

		for (Long leftEntryHash : left.getEntryHashes()) {
			if (right.getEntryPoint(leftEntryHash) == null)
				return MergeResult.DISTINCT;
		}

		visitedLeftNodes.clear();
		visitedRightNodes.clear();
		mergeStack.reset();
		ModuleNode<?> leftMetaEntry = createMetaEntryNode(left);
		ModuleNode<?> rightMetaEntry = createMetaEntryNode(right);
		mergeStack.push(leftMetaEntry, rightMetaEntry);

		merge_stack: while (true) {
			mergeStack.top.pushEdgePair(0, 0);
			for (int i = mergeStack.top.edgeStack.top.leftIndex + 1; i < mergeStack.top.leftEdges.size(); i++) {
				for (int j = mergeStack.top.edgeStack.top.rightIndex + 1; j < mergeStack.top.rightEdges.size(); j++) {
					if (mergeStack.top.edgeStack.top.isCompatible()) {
						mergeStack.push(mergeStack.top.edgeStack.top.leftNode, mergeStack.top.edgeStack.top.rightNode);
						continue merge_stack;
					} else {
						while (j < mergeStack.top.rightEdges.size() && mergeStack.top.rightEdges.get(j) == null)
							j++;
						mergeStack.top.changeRightEdge(j);
					}
				}
				mergeStack.top.edgeStack.top.rightIndex = 0;
				while (i < mergeStack.top.leftEdges.size() && mergeStack.top.leftEdges.get(i) == null)
					i++;
				mergeStack.top.changeLeftEdge(i);
			}
			// cannot merge this node frame, so pop and continue the previous frame
			break;
		}

		return MergeResult.SUBSUMED;
	}
}
