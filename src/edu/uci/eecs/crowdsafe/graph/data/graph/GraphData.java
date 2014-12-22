package edu.uci.eecs.crowdsafe.graph.data.graph;

import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.exception.InvalidGraphException;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.graph.execution.ExecutionNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.execution.ModuleInstance;

public class GraphData<NodeType extends Node<NodeType>> {

	public final NodeHashMap<NodeType> nodesByHash = new NodeHashMap<NodeType>();

	// TODO: it would be ideal for this to become a read-only map after the graph is built
	final Map<Node.Key, NodeType> nodesByKey = new HashMap<Node.Key, NodeType>();

	public boolean HACK_containsEquivalent(Node<?> node) {
		if (node.getModule().unit.isAnonymous)
			return false;
		if (nodesByKey.containsKey(node.getKey()))
			return true;
		if ((node instanceof ExecutionNode) && (((ExecutionNode) node).getInstanceId() > 0))
			return nodesByKey.containsKey(ExecutionNode.Key.create(
					((ModuleInstance) node.getModule()).start + node.getRelativeTag(), 0,
					(ModuleInstance) node.getModule()));
		else
			return false;
	}

	/**
	 * To validate the correctness of the graph. Basically it checks if entry points have no incoming edges, exit points
	 * have no outgoing edges. It might include more validation stuff later...
	 * 
	 * @return true means this is a valid graph, otherwise it's invalid
	 */
	public void validate() {
		for (NodeType node : nodesByKey.values()) {
			switch (node.getType()) {
				case CLUSTER_ENTRY:
					if (node.hasIncomingEdges()) {
						throw new InvalidGraphException("Entry point has incoming edges!");
					}
					break;
				case CLUSTER_EXIT:
					if (node.hasOutgoingEdges()) {
						Log.log("");
						throw new InvalidGraphException("Exit point has outgoing edges!");
					}
					break;
				default:
					break;
			}
		}
	}
}
