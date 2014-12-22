package edu.uci.eecs.crowdsafe.graph.data.results;

import edu.uci.eecs.crowdsafe.graph.data.graph.Node;
import edu.uci.eecs.crowdsafe.graph.data.results.Graph.Module.Builder;

public class NodeResultsFactory {

	private final Graph.Module.Builder moduleBuilder;
	private final Graph.Node.Builder nodeBuilder;

	public NodeResultsFactory() {
		moduleBuilder = Graph.Module.newBuilder();
		nodeBuilder = Graph.Node.newBuilder();
	}

	public NodeResultsFactory(Builder moduleBuilder, Graph.Node.Builder nodeBuilder) {
		this.moduleBuilder = moduleBuilder;
		this.nodeBuilder = nodeBuilder;
	}

	public Graph.Node buildNode(Node<?> node) {
		moduleBuilder.clear().setName(node.getModule().unit.filename);
		moduleBuilder.setVersion(node.getModule().unit.version);
		nodeBuilder.clear().setModule(moduleBuilder.build());
		nodeBuilder.setRelativeTag((int) node.getRelativeTag());
		nodeBuilder.setTagVersion(node.getInstanceId());
		nodeBuilder.setHashcode(node.getHash());
		return nodeBuilder.build();
	}
}
