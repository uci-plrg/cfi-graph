package edu.uci.eecs.crowdsafe.graph.data.graph.modular;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.graph.data.results.Graph;
import edu.uci.eecs.crowdsafe.graph.io.modular.ModularTraceStreamType;

public class ApplicationGraph {

	public static EnumSet<ModularTraceStreamType> MODULAR_GRAPH_STREAM_TYPES = EnumSet
			.allOf(ModularTraceStreamType.class);

	public final ModuleGraph<ModuleNode<?>> graph;

	public ApplicationGraph(ModuleGraph<ModuleNode<?>> graph) {
		this.graph = graph;
	}

	public ApplicationGraph(String name, ApplicationModule module) {
		graph = new ModuleGraph<ModuleNode<?>>(name, module);
	}

	public ModuleNode<?> addNode(long hash, ApplicationModule module, int relativeTag, MetaNodeType type) {
		switch (type) {
			case MODULE_ENTRY:
				ModuleBoundaryNode.Key entryKey = new ModuleBoundaryNode.Key(hash, type);
				ModuleNode<?> entry = graph.getNode(entryKey);
				if (entry == null) {
					entry = new ModuleBoundaryNode(hash, type);
					graph.addModuleEntryNode(entry);
					graph.addNode(entry);
				}
				return entry;
			case MODULE_EXIT:
				ModuleBoundaryNode.Key exitKey = new ModuleBoundaryNode.Key(hash, type);
				ModuleNode<?> exit = graph.getNode(exitKey);
				if (exit == null) {
					exit = new ModuleBoundaryNode(hash, type);
					graph.addNode(exit);
				}
				return exit;
		}

		ModuleBasicBlock.Key key = new ModuleBasicBlock.Key(graph.module, relativeTag, 0);
		while (graph.hasNode(key))
			key = new ModuleBasicBlock.Key(graph.module, relativeTag, key.instanceId + 1);

		ModuleBasicBlock node = new ModuleBasicBlock(key, hash, type);
		graph.addNode(node);
		return node;
	}

	public Graph.Node summarizeProcess() {
		return null;
	}
}
