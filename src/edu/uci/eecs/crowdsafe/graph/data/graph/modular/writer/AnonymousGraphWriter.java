package edu.uci.eecs.crowdsafe.graph.data.graph.modular.writer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.graph.NodeIdentifier;
import edu.uci.eecs.crowdsafe.graph.data.graph.anonymous.ApplicationAnonymousGraphs;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;
import edu.uci.eecs.crowdsafe.graph.io.modular.ModularTraceDataSink;

public class AnonymousGraphWriter implements ModuleDataWriter.ModularData {

	private final ApplicationAnonymousGraphs graphs;

	private final Map<NodeIdentifier, Integer> nodeIndexMap = new HashMap<NodeIdentifier, Integer>();

	private final ModuleDataWriter dataWriter;

	public AnonymousGraphWriter(ApplicationAnonymousGraphs graphs, ModularTraceDataSink dataSink) throws IOException {
		this.graphs = graphs;

		dataWriter = new ModuleDataWriter(this, dataSink);
	}
	
	public void writeGraph() {
		
	}
	
	@Override
	public ApplicationModule getModule() {
		return ApplicationModule.ANONYMOUS_MODULE;
	}
	
	@Override
	public int getNodeIndex(NodeIdentifier node) {
		return nodeIndexMap.get(node);
	}

}
