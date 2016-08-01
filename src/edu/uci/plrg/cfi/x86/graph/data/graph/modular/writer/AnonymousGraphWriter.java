package edu.uci.plrg.cfi.x86.graph.data.graph.modular.writer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.x86.graph.data.application.ApplicationModule;
import edu.uci.plrg.cfi.x86.graph.data.graph.Edge;
import edu.uci.plrg.cfi.x86.graph.data.graph.MetaNodeType;
import edu.uci.plrg.cfi.x86.graph.data.graph.NodeIdentifier;
import edu.uci.plrg.cfi.x86.graph.data.graph.OrdinalEdgeList;
import edu.uci.plrg.cfi.x86.graph.data.graph.anonymous.AnonymousGraph;
import edu.uci.plrg.cfi.x86.graph.data.graph.anonymous.ApplicationAnonymousGraphs;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ModuleBoundaryNode;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ModuleBoundaryNode.HashLabel;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ModuleNode;
import edu.uci.plrg.cfi.x86.graph.io.modular.ModularTraceDataSink;

public class AnonymousGraphWriter implements ModuleDataWriter.ModularData {

	private final ApplicationAnonymousGraphs graphs;

	private final Map<NodeIdentifier, Integer> nodeIndexMap = new IdentityHashMap<NodeIdentifier, Integer>();
	private final transient List<Edge<ModuleNode<?>>> graphEdges = new ArrayList<Edge<ModuleNode<?>>>();

	private ModuleDataWriter dataWriter;

	public AnonymousGraphWriter(ApplicationAnonymousGraphs graphs) {
		this.graphs = graphs;
	}

	public void initialize(ModularTraceDataSink dataSink) throws IOException {
		dataWriter = new ModuleDataWriter(this, dataSink);
	}

	public void writeGraph() throws IOException {
		for (AnonymousGraph graph : graphs.getAllGraphs()) {
			for (ModuleNode<?> entry : graph.getEntryPoints())
				writeNode(entry);
			for (ModuleNode<?> exit : graph.getExitPoints()) 
				writeNode(exit);
			for (ModuleNode<?> node : graph.getAllNodes()) {
				if (!node.isModuleBoundaryNode())
					writeNode(node);
			}

			for (Edge<ModuleNode<?>> edge : graphEdges)
				dataWriter.writeEdge(edge);
			graphEdges.clear();
		}
		dataWriter.flush();
	}

	private void writeNode(ModuleNode<?> node) throws IOException {
		dataWriter.writeNode(node);
		nodeIndexMap.put(node, nodeIndexMap.size());

		if (node.getType() != MetaNodeType.MODULE_EXIT) {
			OrdinalEdgeList<ModuleNode<?>> edges = node.getOutgoingEdges();
			try {
				for (Edge<ModuleNode<?>> edge : edges) {
					graphEdges.add(edge);
				}
			} finally {
				edges.release();
			}
		}
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
