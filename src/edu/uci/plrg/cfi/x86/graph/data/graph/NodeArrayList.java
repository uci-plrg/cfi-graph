package edu.uci.plrg.cfi.x86.graph.data.graph;

import java.util.ArrayList;

public class NodeArrayList<NodeType extends Node<NodeType>> extends ArrayList<NodeType> implements NodeList<NodeType> {

	@Override
	public boolean isSingleton() {
		return false;
	}
	
	@Override
	public boolean contains(Object o) {
		int size = size();
		for (int i = 0; i < size; i++) {
			if (get(i).equals(o))
				return true;
		}
		return false;
	}
}
