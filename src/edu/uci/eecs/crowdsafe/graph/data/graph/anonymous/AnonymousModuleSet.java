package edu.uci.eecs.crowdsafe.graph.data.graph.anonymous;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModuleSet;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.Node;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleBoundaryNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;
import edu.uci.eecs.crowdsafe.graph.util.EdgeTypes;

public class AnonymousModuleSet {

	private static class SizeOrder implements Comparator<ModuleGraph<ModuleNode<?>>> {
		@Override
		public int compare(ModuleGraph<ModuleNode<?>> first, ModuleGraph<ModuleNode<?>> second) {
			int comparison = second.getExecutableNodeCount() - first.getExecutableNodeCount();
			if (comparison != 0)
				return comparison;

			return (int) second.hashCode() - (int) first.hashCode();
		}
	}

	private static class GraphCache {
		final GraphMergeCandidate mergeCandidate;

		final Map<ApplicationModule, ModuleGraph<?>> graphs = new HashMap<ApplicationModule, ModuleGraph<?>>();

		public GraphCache(GraphMergeCandidate mergeCandidate) {
			this.mergeCandidate = mergeCandidate;
		}

		ModuleGraph<?> getGraph(ApplicationModule module) throws IOException {
			ModuleGraph<?> graph = graphs.get(module);
			if (graph == null) {
				graph = mergeCandidate.getModuleGraph(module);
				graphs.put(module, graph);
			}
			return graph;
		}
	}

	int totalSize = 0;
	int minSize = Integer.MAX_VALUE;
	int maxSize = 0;
	int averageSize;
	int twiceAverage;
	int thriceAverage;
	int halfAverage;
	int subgraphsOverTwiceAverage;
	int subgraphsOverThriceAverage;
	int subgraphsUnderHalfAverage;

	final String name;
	final GraphCache graphCache;
	final AnonymousSubgraphFlowAnalysis flowAnalsis = new AnonymousSubgraphFlowAnalysis();

	List<AnonymousGraph> maximalSubgraphs = new ArrayList<AnonymousGraph>();
	private final Map<AnonymousGraphCollection.OwnerKey, AnonymousGraphCollection> modulesByOwner = new HashMap<AnonymousGraphCollection.OwnerKey, AnonymousGraphCollection>();

	public AnonymousModuleSet(String name) {
		this(name, null);
	}

	public AnonymousModuleSet(String name, GraphMergeCandidate mergeCandidate) {
		graphCache = new GraphCache(mergeCandidate);
		this.name = name;
	}

	public Set<AnonymousGraphCollection.OwnerKey> getModuleOwners() {
		return modulesByOwner.keySet();
	}

	public AnonymousGraphCollection getModule(AnonymousGraphCollection.OwnerKey owner) {
		return modulesByOwner.get(owner);
	}

	public void installSubgraphs(List<? extends ModuleGraph<ModuleNode<?>>> anonymousGraphs)
			throws IOException {
		if (anonymousGraphs.isEmpty())
			return;

		for (ModuleGraph<ModuleNode<?>> dynamicGraph : anonymousGraphs) {
			for (AnonymousGraph maximalSubgraph : MaximalSubgraphs.getMaximalSubgraphs(dynamicGraph)) {
				int size = maximalSubgraph.getNodeCount();
				totalSize += size;
				if (size < minSize)
					minSize = size;
				if (size > maxSize)
					maxSize = size;

				maximalSubgraphs.add(maximalSubgraph);
			}
		}
		reportStatistics();
	}

	void installModules(List<AnonymousGraphCollection> modules) throws IOException {
		if (modules.isEmpty())
			return;

		for (AnonymousGraphCollection module : modules) {
			for (AnonymousGraph subgraph : module.subgraphs) {
				int size = subgraph.getNodeCount();
				totalSize += size;
				if (size < minSize)
					minSize = size;
				if (size > maxSize)
					maxSize = size;

				maximalSubgraphs.add(subgraph);
			}
		}
		reportStatistics();
	}

	private void reportStatistics() {
		Collections.sort(maximalSubgraphs, new SizeOrder());

		averageSize = maximalSubgraphs.isEmpty() ? 0 : (totalSize / maximalSubgraphs.size());
		twiceAverage = averageSize * 2;
		thriceAverage = averageSize * 3;
		halfAverage = averageSize / 2;
		subgraphsOverTwiceAverage = 0;
		subgraphsOverThriceAverage = 0;
		subgraphsUnderHalfAverage = 0;
		for (ModuleGraph<ModuleNode<?>> maximalSubgraph : maximalSubgraphs) {
			int size = maximalSubgraph.getNodeCount();
			if (size > twiceAverage) {
				subgraphsOverTwiceAverage++;
				if (size > thriceAverage)
					subgraphsOverThriceAverage++;
			} else if (size < halfAverage) {
				subgraphsUnderHalfAverage++;
			}
		}

		Log.log("Found %d maximal subgraphs.", maximalSubgraphs.size());
		Log.log("Min size %d, max size %d, average size %d", minSize, maxSize, averageSize);
		Log.log("Over twice average %d, over thrice average %d, under half average %d", subgraphsOverTwiceAverage,
				subgraphsOverThriceAverage, subgraphsUnderHalfAverage);

		Set<ApplicationModule> allConnectingModules = new HashSet<ApplicationModule>();
		ApplicationModule owningModule;
		Set<ApplicationModule> ambiguousOwnerSet = new HashSet<ApplicationModule>();
		subgraphs: for (AnonymousGraph subgraph : maximalSubgraphs) {
			// if (subgraph.getAllNodes().size() > 1000)
			// Log.log("\n === Subgraph of %d nodes with %d total hashes", subgraph.getExecutableNodeCount(),
			// subgraph.getGraphData().nodesByHash.keySet().size());

			ApplicationModule module;
			boolean ambiguousOwner = false;
			ambiguousOwnerSet.clear();
			owningModule = null;
			allConnectingModules.clear();
			if (subgraph.getEntryPoints().isEmpty() && !subgraph.isJIT()) {
				Log.log("Error: entry point missing for anonymous subgraph of %d nodes in %s!",
						subgraph.getNodeCount(), name);
				Log.log("\tOmitting this subgraph from the merge.");
				// subgraph.logGraph();
				continue subgraphs;
			}

			for (ModuleNode<?> entryPoint : subgraph.getEntryPoints()) {
				OrdinalEdgeList<?> edges = entryPoint.getOutgoingEdges();
				try {
					if (ApplicationModuleSet.getInstance().getClusterByAnonymousGencodeHash(entryPoint.getHash()) != null)
						continue; // no ownership by gencode, it's not reliable

					// int leftCallSiteCount = 0, rightCallSiteCount = 0;
					module = ApplicationModuleSet.getInstance().getClusterByAnonymousEntryHash(entryPoint.getHash());
					if (module == null) {
						module = ApplicationModuleSet.getInstance().getClusterByInterceptionHash(entryPoint.getHash());
					}
					if (module == null) {
						Log.log("Error: unrecognized entry point 0x%x for anonymous subgraph of %d nodes!",
								entryPoint.getHash(), subgraph.getNodeCount());
						Log.log("\tKeeping the edge but discarding this owner.");
						// Log.log("\tSubgraph is owned by %s", owningCluster);
						continue;
					}

					module = AnonymousGraphCollection.resolveAlias(module);

					/*
					 * if (isStoryboarding(cluster)) {
					 * Log.log("Warning: skipping anonymous subgraph with entry from 'ni' cluster %s",
					 * cluster.getUnitFilename()); continue subgraphs; }
					 */

					allConnectingModules.add(module);

					if (AnonymousGraphCollection.isEligibleOwner(module)) {
						if (ambiguousOwner) {
							ambiguousOwnerSet.add(module);
						} else {
							if (owningModule == null) {
								owningModule = module;
							} else if (owningModule != module) {
								ambiguousOwner = true;
								ambiguousOwnerSet.add(owningModule);
								ambiguousOwnerSet.add(module);
							}
						}
					}

					// clusterName = cluster.name;
					// leftCallSiteCount = getExitEdgeCount(entryPoint.getHash(), graphCache.getLeftGraph(cluster));
					// rightCallSiteCount = getExitEdgeCount(entryPoint.getHash(), graphCache.getRightGraph(cluster));

					// Log.log("     Entry point 0x%x (%s) reaches %d nodes from %d left call sites and %d right call sites",
					// entryPoint.getHash(), clusterName, edges.size(), leftCallSiteCount, rightCallSiteCount);
				} finally {
					edges.release();
				}
			}

			if (!ambiguousOwner) {
				for (ModuleNode<?> exitPoint : subgraph.getExitPoints()) {
					OrdinalEdgeList<?> edges = exitPoint.getIncomingEdges();
					try {
						// int leftTargetCount = 0, rightTargetCount = 0;
						module = ApplicationModuleSet.getInstance().getClusterByAnonymousExitHash(exitPoint.getHash());
						if (module == null) {
							// Log.log("     Callout 0x%x (%s) to an exported function", node.getHash());
							continue;
						}

						module = AnonymousGraphCollection.resolveAlias(module);

						/*
						 * if (isStoryboarding(cluster)) {
						 * Log.log("Warning: skipping anonymous subgraph with exit to 'ni' cluster %s",
						 * cluster.getUnitFilename()); continue subgraphs; }
						 */

						allConnectingModules.add(module);

						if (AnonymousGraphCollection.isEligibleOwner(module)) {
							if (owningModule == null) {
								owningModule = module;
							}
						}
					} finally {
						edges.release();
					}
				}
			}

			if (ambiguousOwner) {
				StringBuilder buffer = new StringBuilder();
				for (ApplicationModule c : ambiguousOwnerSet) {
					buffer.append(c.filename);
					buffer.append(", ");
				}
				buffer.setLength(buffer.length() - 2);
				Log.log("Error: subgraph of %d nodes has entry points from multiple clusters: %s",
						subgraph.getNodeCount(), buffer);
				Log.log("\tOmitting this subgraph from the merge.");
			} else {
				if (owningModule == null) {
					if (allConnectingModules.size() == 1) {
						owningModule = allConnectingModules.iterator().next();
					} else {
						Log.log("Error: could not determine the owning cluster of an anonymous subgraph with %d nodes!",
								subgraph.getNodeCount());
						Log.log("\tPotential owners are: %s", allConnectingModules);
						Log.log("\tOmitting this subgraph from the merge.");
						continue subgraphs;
					}
				}

				AnonymousGraphCollection.OwnerKey key = new AnonymousGraphCollection.OwnerKey(owningModule,
						subgraph.isJIT());
				AnonymousGraphCollection owner = modulesByOwner.get(key);
				if (owner == null) {
					owner = new AnonymousGraphCollection(owningModule);
					modulesByOwner.put(key, owner);
				}
				owner.addSubgraph(subgraph);
			}
		}
	}

	public void analyzeModules() throws IOException {
		for (AnonymousGraphCollection module : modulesByOwner.values()) {
			if (module.isJIT()) {
				Log.log(" === Anonymous black box module owned by %s ===", module.owningModule.name);
			} else {
				Log.log(" ==== Anonymous white box module owned by %s ====", module.owningModule.name);
				Log.log("\t%d subgraphs with %d total nodes", module.subgraphs.size(), module.getNodeCount());
			}

			ApplicationModule module;
			int arbitrarySubgraphId = -1;
			for (AnonymousGraph subgraph : module.subgraphs) {
				arbitrarySubgraphId++;
				if (module.hasEscapes(subgraph)) {
					// Log.log("\tEscapes in subgraph %d:", arbitrarySubgraphId);
					for (ModuleNode<?> entry : subgraph.getEntryPoints()) {
						if (ApplicationModuleSet.getInstance().getClusterByAnonymousGencodeHash(entry.getHash()) != null)
							continue;
						module = ApplicationModuleSet.getInstance().getClusterByAnonymousEntryHash(entry.getHash());
						if (module != module.owningModule)
							Log.log("\t\tEntry from %s; edge types: %s", (module == null) ? "unknown cluster"
									: module.name, EdgeTypes.getOutgoing(entry));
					}
					if (module.isJIT() || module.owningModule.filename.startsWith("chrome_child")) {
						for (ModuleNode<?> exit : subgraph.getExitPoints()) {
							module = ApplicationModuleSet.getInstance().getClusterByAnonymousExitHash(exit.getHash());
							if (module == null) {
								Log.log("\t\tExit to exported function with hash 0x%x; edge types %s", exit.getHash(),
										EdgeTypes.getIncoming(exit));
							} else if (module != module.owningModule) {
								if (module == ApplicationModule.SYSTEM_MODULE) {
									Log.log("\t\tExit to %s (calling sysnum #%d); edge types %s",
											module.name,
											ApplicationModuleSet.getInstance().sysnumsBySyscallHash.get(exit.getHash()),
											EdgeTypes.getIncoming(exit));
								} else {
									Log.log("\t\tExit to %s; edge types %s", module.name, EdgeTypes.getIncoming(exit));

									// if (module.owningCluster.getUnitFilename().startsWith("chrome_child"))
									// subgraph.logGraph();
								}
							}
						}
					}
				} else if (module.isJIT()) {
					Log.log("\tNo escapes in subgraph %d", arbitrarySubgraphId);
				}
			}

			Log.log();
		}
	}

	void printDotFiles() throws IOException {
		for (AnonymousGraphCollection module : modulesByOwner.values()) {
			module.printDotFiles();
		}
	}

	private boolean isStoryboarding(ApplicationModule cluster) {
		return cluster.name.contains(".ni.dll-");
	}

	private int getEntryEdgeCount(long entryHash, ModuleGraph<?> targetGraph) {
		if (targetGraph == null)
			return 0;

		Node<?> targetEntry = targetGraph.getEntryPoint(entryHash);
		if (targetEntry != null) {
			OrdinalEdgeList<?> entryEdges = targetEntry.getOutgoingEdges();
			try {
				return entryEdges.size();
			} finally {
				entryEdges.release();
			}
		}
		return 0;
	}

	private int getExitEdgeCount(long exitHash, ModuleGraph<?> targetGraph) {
		if (targetGraph == null)
			return 0;

		Node<?> targetExit = targetGraph.getNode(new ModuleBoundaryNode.Key(exitHash, MetaNodeType.MODULE_EXIT));
		if (targetExit != null) {
			OrdinalEdgeList<?> entryEdges = targetExit.getIncomingEdges();
			try {
				return entryEdges.size();
			} finally {
				entryEdges.release();
			}
		}
		return 0;
	}

	void localizedCompatibilityAnalysis(ModuleGraph<ModuleNode<?>> left, ModuleGraph<ModuleNode<?>> right) {
		AnonymousSubgraphCompatibilityAnalysis analysis = new AnonymousSubgraphCompatibilityAnalysis(left, right);
		analysis.localCompatibilityPerNode(20);
	}

	void fullCompatibilityAnalysis(ModuleGraph<ModuleNode<?>> left, ModuleGraph<ModuleNode<?>> right) {
		AnonymousSubgraphCompatibilityAnalysis analysis = new AnonymousSubgraphCompatibilityAnalysis(left, right);
		analysis.fullCompatibilityPerEntry();
	}

	public static void main(String[] args) {
		long foo = (0L ^ (1L << 0x3fL));
		System.out.println(String.format("foo: 0x%x", foo));
	}
}
