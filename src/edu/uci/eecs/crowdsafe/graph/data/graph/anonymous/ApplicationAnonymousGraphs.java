package edu.uci.eecs.crowdsafe.graph.data.graph.anonymous;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModuleSet;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleBoundaryNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;

public class ApplicationAnonymousGraphs {

	private Map<ApplicationModule, ModuleAnonymousGraphs> graphsByOwner = new HashMap<ApplicationModule, ModuleAnonymousGraphs>();

	public void inflate(ModuleGraph<ModuleNode<?>> graph) {
		Set<AnonymousGraph> subgraphs = MaximalSubgraphs.getMaximalSubgraphs(graph);

		List<Edge<ModuleNode<?>>> gencodeEntries = new ArrayList<Edge<ModuleNode<?>>>();
		List<Edge<ModuleNode<?>>> executionEntries = new ArrayList<Edge<ModuleNode<?>>>();
		List<Edge<ModuleNode<?>>> executionExits = new ArrayList<Edge<ModuleNode<?>>>();
		List<ModuleNode<?>> returnNodes = new ArrayList<ModuleNode<?>>();

		Set<ApplicationModule> entryModules = new HashSet<ApplicationModule>();
		Set<ApplicationModule> owners = new HashSet<ApplicationModule>();

		for (AnonymousGraph subgraph : subgraphs) {
			Log.log("Reporting graph 0x%x", subgraph.hashCode());

			gencodeEntries.clear();
			executionEntries.clear();
			executionExits.clear();
			returnNodes.clear();
			owners.clear();
			entryModules.clear();
			ApplicationModule fromModule, owner = null;
			
			for (ModuleNode<?> entryPoint : subgraph.getEntryPoints()) {
				ModuleBoundaryNode.HashLabel label = ApplicationModuleSet.getInstance().crossModuleLabels
						.get(entryPoint.getHash());
				OrdinalEdgeList<ModuleNode<?>> edges = entryPoint.getOutgoingEdges();
				try {
					fromModule = ApplicationModuleSet.getInstance().modulesByName.get(label.fromModuleName);
					if (label.isGencode()) {
						gencodeEntries.addAll(edges);
						owners.add(fromModule);
					} else {
						executionEntries.addAll(edges);
						entryModules.add(fromModule);
					}
				} finally {
					edges.release();
				}
			}

			owners.retainAll(entryModules);

			for (ModuleNode<?> exitPoint : subgraph.getExitPoints()) {
				ModuleBoundaryNode.HashLabel label = ApplicationModuleSet.getInstance().crossModuleLabels.get(exitPoint
						.getHash());
				if (!label.isGencode()) {
					OrdinalEdgeList<ModuleNode<?>> edges = exitPoint.getIncomingEdges();
					try {
						executionExits.addAll(edges);
					} finally {
						edges.release();
					}
				}
			}

			for (ModuleNode<?> node : subgraph.getAllNodes()) {
				if (node.getType() == MetaNodeType.RETURN)
					returnNodes.add(node);
			}

			if (owners.size() == 1) {
				owner = owners.iterator().next();
			} else {
				if (owners.isEmpty()) {
					Log.error(" ### Cannot find the owner for an anonymous subgraph of %d nodes with entry points %s",
							subgraph.getExecutableNodeCount(), subgraph.getEntryPoints());
				} else {
					Log.error(
							" ### Multiple potential owners for an anonymous subgraph of %d nodes with entry points %s",
							subgraph.getExecutableNodeCount(), subgraph.getEntryPoints());
				}
				subgraph.logGraph(true);
				Log.warn(" ### ownership\n");
				continue;
			}

			if (executionExits.isEmpty() && returnNodes.isEmpty()) {
				Log.error(" ### Missing exit from anonymous subgraph of %d nodes with entry points %s",
						subgraph.getExecutableNodeCount(), subgraph.getEntryPoints());
				subgraph.logGraph(true);
				Log.warn(" ### exit\n");
				continue;
			}

			Log.log(" === SDR of %d executable nodes with %d entries owned by %s:", subgraph.getExecutableNodeCount(),
					executionEntries.size(), owner.filename);
			Log.log("\t--- Gencode entries:");
			for (Edge<ModuleNode<?>> gencodeEdge : gencodeEntries)
				Log.log("\t%s", gencodeEdge);
			Log.log("\t--- Execution entries:");
			for (Edge<ModuleNode<?>> executionEdge : executionEntries)
				Log.log("\t%s", executionEdge);
			Log.log("\t--- Execution exits:");
			for (Edge<ModuleNode<?>> executionEdge : executionExits)
				Log.log("\t%s", executionEdge);
			for (ModuleNode<?> returnNode : returnNodes)
				Log.log("\t%s", returnNode);
			Log.log(" === SDR end\n");
			
			ModuleAnonymousGraphs moduleGraphs = graphsByOwner.get(owner);
			if (moduleGraphs == null) {
				moduleGraphs = new ModuleAnonymousGraphs(owner);
				graphsByOwner.put(owner, moduleGraphs);
			}
			moduleGraphs.addSubgraph(subgraph);
		}
	}
}
