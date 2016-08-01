package edu.uci.plrg.cfi.x86.graph.data.graph.anonymous;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import edu.uci.plrg.cfi.common.exception.InvalidGraphException;
import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.x86.graph.data.application.ApplicationModule;
import edu.uci.plrg.cfi.x86.graph.data.application.ApplicationModuleSet;
import edu.uci.plrg.cfi.x86.graph.data.graph.Edge;
import edu.uci.plrg.cfi.x86.graph.data.graph.MetaNodeType;
import edu.uci.plrg.cfi.x86.graph.data.graph.ModuleGraph;
import edu.uci.plrg.cfi.x86.graph.data.graph.OrdinalEdgeList;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ModuleBoundaryNode;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ModuleNode;

public class AnonymousGraph extends ModuleGraph<ModuleNode<?>> {

	private class Analysis {

		final Set<ModuleNode<?>> visitedNodes = new HashSet<ModuleNode<?>>();
		final Set<ModuleNode<?>> unvisitedNodes = new HashSet<ModuleNode<?>>();
		final Queue<ModuleNode<?>> bfsQueue = new LinkedList<ModuleNode<?>>();
		final List<ModuleNode<?>> orphanedEntries = new ArrayList<ModuleNode<?>>();

		void reset() {
			visitedNodes.clear();
			unvisitedNodes.clear();
			bfsQueue.clear();
			orphanedEntries.clear();
		}

		void log(boolean isFinal) {
			Log.log("\nGraph traversal for module %s (0x%x)", module, hashCode());

			for (ModuleNode<?> node : getAllNodes()) {
				if (node.getType().isApplicationNode) {
					if (node.getIncomingEdges().size() == 0)
						orphanedEntries.add(node);
					else
						unvisitedNodes.add(node);
				} else if (node.getType() == MetaNodeType.MODULE_ENTRY) {
					bfsQueue.add(node);
				}
			}

			traverseFromEntries();

			if (!isFinal)
				return;

			if (!orphanedEntries.isEmpty()) {
				for (ModuleNode<?> orphanedEntry : orphanedEntries) {
					Log.log(" ## Subgraph reachable only from orphaned entry point %s:", orphanedEntry);
					bfsQueue.add(orphanedEntry);
					traverseFromEntries();
				}
			}

			if (!unvisitedNodes.isEmpty()) {
				for (ModuleNode<?> node : unvisitedNodes) {
					Log.error("Node %s is only reachable from nodes outside the subgraph!", node);
					OrdinalEdgeList<ModuleNode<?>> edgeList = node.getIncomingEdges();
					try {
						for (Edge<ModuleNode<?>> edge : edgeList)
							Log.log("\t%s", edge);
					} finally {
						edgeList.release();
					}
				}
				throw new InvalidGraphException("Subgraph partition failed!", unvisitedNodes);
			}
		}

		void traverseFromEntries() {
			while (bfsQueue.size() > 0) {
				ModuleNode<?> node = bfsQueue.remove();
				visitedNodes.add(node);
				unvisitedNodes.remove(node);

				OrdinalEdgeList<ModuleNode<?>> edgeList = node.getOutgoingEdges();
				try {
					for (Edge<ModuleNode<?>> edge : edgeList) {
						ModuleNode<?> neighbor = edge.getToNode();
						if (!visitedNodes.contains(neighbor)) {
							bfsQueue.add(neighbor);
							visitedNodes.add(neighbor);
							unvisitedNodes.remove(node);
						}
						Log.log(edge);
					}
				} finally {
					edgeList.release();
				}
			}
		}
	}

	public static ApplicationModule identifyOwner(AnonymousGraph graph) {
		Set<ApplicationModule> entryModules = new HashSet<ApplicationModule>();
		Set<ApplicationModule> owners = new HashSet<ApplicationModule>();
		ApplicationModule fromModule, owner = null;

		for (ModuleNode<?> entryPoint : graph.getEntryPoints()) {
			ModuleBoundaryNode.HashLabel label = ApplicationModuleSet.getInstance().crossModuleLabels.get(entryPoint
					.getHash());
			OrdinalEdgeList<ModuleNode<?>> edges = entryPoint.getOutgoingEdges();
			try {
				fromModule = ApplicationModuleSet.getInstance().modulesByFilename.get(label.fromModuleFilename);
				if (label.isGencode()) {
					owners.add(fromModule);
				} else {
					entryModules.add(fromModule);
				}
			} finally {
				edges.release();
			}
		}

		owners.retainAll(entryModules);

		if (owners.size() == 1) {
			owner = owners.iterator().next();
		} else {
			if (owners.isEmpty()) {
				Log.error(" ### Cannot find the owner for an anonymous subgraph of %d nodes with entry points %s",
						graph.getExecutableNodeCount(), graph.getEntryPoints());
			} else {
				Log.error(" ### Multiple potential owners for an anonymous subgraph of %d nodes with entry points %s",
						graph.getExecutableNodeCount(), graph.getEntryPoints());
			}
			graph.logGraph(true);
			Log.warn(" ### ownership\n");
		}

		return owner;
	}

	private static int ID_INDEX = 0;

	public final int id = ID_INDEX++;
	private ModuleNode<?> jitSingleton = null;

	private Analysis analysis = null;

	public AnonymousGraph(String name) {
		super(name, ApplicationModule.ANONYMOUS_MODULE);
	}

	public boolean isJIT() {
		return jitSingleton != null;
	}

	public ModuleNode<?> getJITSingleton() {
		return jitSingleton;
	}

	public void addNode(ModuleNode<?> node) {
		super.addNode(node);

		if (node.isJITSingleton()) {
			if (getExecutableNodeCount() > 0) {
				logGraph();
				throw new IllegalArgumentException(
						String.format("Cannot add a JIT singleton to a graph having executable nodes (%d).",
								getExecutableNodeCount()));
			}
			jitSingleton = node;
		} else if (node.getType().isExecutable) {
			if (jitSingleton != null) {
				throw new IllegalArgumentException(
						String.format("Cannot add executable nodes to a JIT singleton graph."));
			}
		}
	}

	@Override
	public void logGraph() {
		this.logGraph(false);
	}

	public void logGraph(boolean isFinal) {
		if (analysis == null)
			analysis = new Analysis();
		else
			analysis.reset();
		analysis.log(isFinal);
	}
}
