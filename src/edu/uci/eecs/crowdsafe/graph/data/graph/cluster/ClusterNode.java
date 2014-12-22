package edu.uci.eecs.crowdsafe.graph.data.graph.cluster;

import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeSet;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.Node;

public abstract class ClusterNode<KeyType extends Node.Key> extends Node<ClusterNode<?>> {

	public static final int SYSTEM_SINGLETON = 1;
	public static final int PROCESS_ENTRY_SINGLETON = 3;
	public static final int CHILD_PROCESS_SINGLETON = 4;
	public static final int BLACK_BOX_SINGLETON_START = 0x100;
	public static final int BLACK_BOX_SINGLETON_END = BLACK_BOX_SINGLETON_START + 10;
	public static final int SYSCALL_COUNT = 0x4000;
	public static final int SYSCALL_SINGLETON_START = 0x1000;
	public static final int SYSCALL_SINGLETON_END = SYSCALL_SINGLETON_START + SYSCALL_COUNT;
	public static final int FAKE_ANONYMOUS_TAG_START = SYSCALL_SINGLETON_END + 1;

	final KeyType key;

	protected ClusterNode(KeyType key) {
		this.key = key;
	}

	public abstract int getInstanceId();

	public abstract ClusterModule getModule();

	@Override
	public KeyType getKey() {
		return key;
	}

	public void addIncomingEdge(Edge<ClusterNode<?>> e) {
		edges.addEdge(EdgeSet.Direction.INCOMING, e);
	}

	public void addOutgoingEdge(Edge<ClusterNode<?>> e) {
		edges.addEdge(EdgeSet.Direction.OUTGOING, e);
	}

	public void removeIncomingEdge(Edge<ClusterNode<?>> e) {
		edges.removeEdge(EdgeSet.Direction.INCOMING, e);
	}

	public boolean replaceEdge(Edge<ClusterNode<?>> original, Edge<ClusterNode<?>> replacement) {
		return edges.replaceEdge(original, replacement);
	}

	public boolean isBlackBoxSingleton() {
		return ((getType() == MetaNodeType.SINGLETON) && (getRelativeTag() >= BLACK_BOX_SINGLETON_START) && (getRelativeTag() < BLACK_BOX_SINGLETON_END));
	}

	@Override
	public int hashCode() {
		return key.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof ClusterNode) {
			return key.equals(((ClusterNode<?>) o).key);
		}
		return false;
	}
}
