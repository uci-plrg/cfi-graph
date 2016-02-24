package edu.uci.eecs.crowdsafe.graph.data.graph.modular.writer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ApplicationGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleMetadataExecution;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleMetadataSequence;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleUIB;
import edu.uci.eecs.crowdsafe.graph.io.modular.ModularTraceDataSink;

public class ModuleGraphWriter implements ModuleDataWriter.ModularData<ModuleNode<?>> {

	private final ApplicationGraph graph;

	private final Map<ModuleNode<?>, Integer> nodeIndexMap = new HashMap<ModuleNode<?>, Integer>();
	private final List<Edge<ModuleNode<?>>> allEdges = new ArrayList<Edge<ModuleNode<?>>>();

	private final ModuleDataWriter<ModuleNode<?>> dataWriter;

	public ModuleGraphWriter(ApplicationGraph graph, ModularTraceDataSink dataSink) throws IOException {
		this.graph = graph;

		dataWriter = new ModuleDataWriter<ModuleNode<?>>(this, dataSink);
	}

	public void writeGraph() throws IOException {
		for (ModuleNode<?> node : graph.graph.getAllNodes()) {
			nodeIndexMap.put(node, nodeIndexMap.size());
			dataWriter.writeNode(node);

			for (Edge<ModuleNode<?>> edge : node.getOutgoingEdges()) {
				allEdges.add(edge);
			}
		}

		int edgeIndex = 0;
		Map<Edge<ModuleNode<?>>, Integer> edgeIndexMap = new HashMap<Edge<ModuleNode<?>>, Integer>();
		for (Edge<ModuleNode<?>> edge : allEdges) {
			dataWriter.writeEdge(edge);
			edgeIndexMap.put(edge, edgeIndex++);
		}
		for (ModuleMetadataSequence sequence : graph.graph.metadata.sequences.values()) {
			for (ModuleMetadataExecution execution : sequence.executions) {
				for (int i = execution.uibs.size() - 1; i >= 0; i--) {
					ModuleUIB uib = execution.uibs.get(i);
					if (edgeIndexMap.get(uib.edge) == null) {
						Log.log("Error! Found a UIB for missing edge %s.", uib.edge);
						execution.uibs.remove(i);
					}
				}
			}
		}

		dataWriter.writeMetadataHistory(graph.graph.metadata, edgeIndexMap);
		dataWriter.flush();
	}

	@Override
	public ApplicationModule getModule() {
		return graph.graph.module;
	}

	@Override
	public int getNodeIndex(ModuleNode<?> node) {
		return nodeIndexMap.get(node);
	}
}