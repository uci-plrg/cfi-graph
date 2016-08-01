package edu.uci.plrg.cfi.x86.graph.data.graph;

public interface NodeList<NodeType extends Node<NodeType>> {

	@SuppressWarnings("rawtypes")
	public static final NodeList EMPTY = new EmptyNodeList();

	int size();

	boolean isSingleton();

	NodeType get(int index);

	@SuppressWarnings("rawtypes")
	public static class EmptyNodeList implements NodeList {
		@Override
		public int size() {
			return 0;
		}

		@Override
		public Node get(int index) {
			return null;
		}

		@Override
		public boolean isSingleton() {
			return false;
		}
	}
}
