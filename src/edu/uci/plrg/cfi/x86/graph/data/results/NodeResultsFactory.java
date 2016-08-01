package edu.uci.plrg.cfi.x86.graph.data.results;

import edu.uci.plrg.cfi.x86.graph.data.graph.Node;
import edu.uci.plrg.cfi.x86.graph.data.results.Graph.Module.Builder;

public class NodeResultsFactory {

	private final Graph.ModuleVersion.Builder moduleBuilder;
	private final Graph.Node.Builder nodeBuilder;

	public NodeResultsFactory() {
		moduleBuilder = Graph.ModuleVersion.newBuilder();
		nodeBuilder = Graph.Node.newBuilder();
	}

	public NodeResultsFactory(Graph.ModuleVersion.Builder moduleBuilder, Graph.Node.Builder nodeBuilder) {
		this.moduleBuilder = moduleBuilder;
		this.nodeBuilder = nodeBuilder;
	}

	public Graph.Node buildNode(Node<?> node) {
		moduleBuilder.clear().setName(node.getModule().filename);
		moduleBuilder.setVersion(node.getModule().version);
		nodeBuilder.clear().setVersion(moduleBuilder.build());
		nodeBuilder.setRelativeTag((int) node.getRelativeTag());
		nodeBuilder.setTagVersion(node.getInstanceId());
		nodeBuilder.setHashcode(node.getHash());
		return nodeBuilder.build();
	}
}
