package edu.uci.plrg.cfi.x86.graph.data.graph.anonymous;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.x86.graph.data.application.ApplicationModule;
import edu.uci.plrg.cfi.x86.graph.data.application.ApplicationModuleSet;
import edu.uci.plrg.cfi.x86.graph.data.graph.Edge;
import edu.uci.plrg.cfi.x86.graph.data.graph.MetaNodeType;
import edu.uci.plrg.cfi.x86.graph.data.graph.ModuleGraph;
import edu.uci.plrg.cfi.x86.graph.data.graph.OrdinalEdgeList;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ModuleBoundaryNode;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ModuleNode;

public class ApplicationAnonymousGraphs {

	private Map<ApplicationModule, ModuleAnonymousGraphs> graphsByOwner = new HashMap<ApplicationModule, ModuleAnonymousGraphs>();

	public List<AnonymousGraph> getAllGraphs() {
		List<AnonymousGraph> graphs = new ArrayList<AnonymousGraph>();
		for (ModuleAnonymousGraphs moduleGraphs : graphsByOwner.values())
			graphs.addAll(moduleGraphs.subgraphs);
		return Collections.unmodifiableList(graphs);
	}
	
	public Set<ApplicationModule> getOwners() {
		return graphsByOwner.keySet();
	}
	
	public ModuleAnonymousGraphs getOwnerGraphs(ApplicationModule owner) {
		return graphsByOwner.get(owner);
	}

	public void addGraph(AnonymousGraph graph, ApplicationModule owner) {
		ModuleAnonymousGraphs moduleGraphs = graphsByOwner.get(owner);
		if (moduleGraphs == null) {
			moduleGraphs = new ModuleAnonymousGraphs(owner);
			graphsByOwner.put(owner, moduleGraphs);
		}
		moduleGraphs.addSubgraph(graph);
	}

	public void inflate(ModuleGraph<ModuleNode<?>> graph) {
		Set<AnonymousGraph> subgraphs = MaximalSubgraphs.getMaximalSubgraphs(graph);

		List<Edge<ModuleNode<?>>> gencodeEntries = new ArrayList<Edge<ModuleNode<?>>>();
		List<Edge<ModuleNode<?>>> executionEntries = new ArrayList<Edge<ModuleNode<?>>>();
		List<Edge<ModuleNode<?>>> executionExits = new ArrayList<Edge<ModuleNode<?>>>();
		List<ModuleNode<?>> returnNodes = new ArrayList<ModuleNode<?>>();

		Set<ApplicationModule> entryModules = new HashSet<ApplicationModule>();
		Set<ApplicationModule> owners = new HashSet<ApplicationModule>();

		if (!graphsByOwner.isEmpty()) {
			Log.warn("Warning: inflating an anonymous graph into an %s that is already populated with %d graphs!",
					getClass().getSimpleName(), getAllGraphs().size());
		}

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
					fromModule = ApplicationModuleSet.getInstance().modulesByFilename.get(label.fromModuleFilename);
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
				if (label == null) {
					Log.log("Warning: no label for exit point %s", exitPoint);
					continue;
				}
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
					Log.error(
							" ### Error: cannot find the owner for an anonymous subgraph of %d nodes with entry points %s",
							subgraph.getExecutableNodeCount(), subgraph.getEntryPoints());
				} else {
					Log.error(
							" ### Error: multiple potential owners for an anonymous subgraph of %d nodes with entry points %s",
							subgraph.getExecutableNodeCount(), subgraph.getEntryPoints());
				}
				subgraph.logGraph(true);
				Log.warn(" ### ownership\n");
				continue;
			}

			if (executionExits.isEmpty() && returnNodes.isEmpty()) {
				Log.error(" ### Error: missing exit from anonymous subgraph of %d nodes with entry points %s",
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

			addGraph(subgraph, owner);
		}
	}
}
