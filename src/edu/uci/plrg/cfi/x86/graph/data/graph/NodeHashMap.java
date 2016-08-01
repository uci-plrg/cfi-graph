package edu.uci.plrg.cfi.x86.graph.data.graph;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class NodeHashMap<NodeType extends Node<NodeType>> {
	private final Map<Long, NodeList<NodeType>> map = new HashMap<Long, NodeList<NodeType>>();

	private int nodeCount = 0;

	@SuppressWarnings("unchecked")
	public void add(NodeType node) {
		NodeList<NodeType> existing = map.get(node.getHash());
		if (existing == null) {
			map.put(node.getHash(), node);
		} else {
			if (existing.equals(node))
				return;

			if (existing.isSingleton()) {
				NodeArrayList<NodeType> list = new NodeArrayList<NodeType>();
				if (list.contains(node))
					return;

				list.add((NodeType) existing);
				list.add(node);
				map.put(node.getHash(), list);
			} else {
				NodeArrayList<NodeType> list = (NodeArrayList<NodeType>) existing;
				list.add(node);
			}
		}

		nodeCount++;
	}

	@SuppressWarnings("unchecked")
	public NodeList<NodeType> get(long hash) {
		NodeList<NodeType> nodes = map.get(hash);
		if (nodes == null)
			return (NodeList<NodeType>) NodeList.EMPTY;
		else
			return nodes;
	}

	public Set<Long> keySet() {
		return map.keySet();
	}

	public int getHashCount() {
		return map.size();
	}

	public int getNodeCount() {
		return nodeCount;
	}

	public int getHashOverlapPerNode(NodeHashMap<?> other) {
		int overlap = 0;
		NodeList<?> otherList;
		for (Map.Entry<Long, NodeList<NodeType>> entry : map.entrySet()) {
			otherList = other.map.get(entry.getKey());
			if (otherList != null) {
				overlap += Math.min(entry.getValue().size(), otherList.size());
			}
		}
		return overlap;
	}
}
