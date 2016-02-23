package edu.uci.eecs.crowdsafe.graph.main;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap.OptionMode;
import edu.uci.eecs.crowdsafe.graph.data.ModuleRelocations;
import edu.uci.eecs.crowdsafe.graph.data.dist.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.dist.ApplicationModuleSet;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.Node;
import edu.uci.eecs.crowdsafe.graph.data.graph.NodeHashMap;
import edu.uci.eecs.crowdsafe.graph.data.graph.NodeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ModuleBoundaryNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ModuleNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.loader.ModuleGraphLoadSession;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterMetadataExecution;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterMetadataSequence;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterUIB;
import edu.uci.eecs.crowdsafe.graph.io.cluster.ClusterTraceDataSource;
import edu.uci.eecs.crowdsafe.graph.io.cluster.ClusterTraceDirectory;
import edu.uci.eecs.crowdsafe.graph.util.CrowdSafeTraceUtil;

public class RelocationAnalyzer {

	private class EdgeCounter {
		private long indirectBranchCount = 0;
		private long targetCount = 0;
		private long degree2BranchCount = 0;
		private long degree2TargetCount = 0;
		private long degree3BranchCount = 0;
		private long degree3TargetCount = 0;
		private long degree5BranchCount = 0;
		private long degree5TargetCount = 0;
		private long degree10BranchCount = 0;
		private long degree10TargetCount = 0;

		private void countEdges(NodeHashMap<?> nodeMap) {
			for (Long hash : nodeMap.keySet()) {
				NodeList<?> nodes = nodeMap.get(hash);
				for (int i = 0; i < nodes.size(); i++) {
					Node<?> node = nodes.get(i);
					for (int j = 0; j < node.getOutgoingOrdinalCount(); j++) {
						if (node.getOrdinalEdgeType(j) == EdgeType.INDIRECT) {
							OrdinalEdgeList outgoing = node.getOutgoingEdges(j);
							int count = outgoing.size();
							outgoing.release();

							indirectBranchCount++;
							targetCount += count;
							if (count >= 2) {
								degree2BranchCount++;
								degree2TargetCount += count;
							}
							if (count >= 3) {
								degree3BranchCount++;
								degree3TargetCount += count;
							}
							if (count >= 5) {
								degree5BranchCount++;
								degree5TargetCount += count;
							}
							if (count >= 10) {
								degree10BranchCount++;
								degree10TargetCount += count;
							}
						}
					}
				}
			}
		}

		private void report() {
			Log.log("branch-stats: Total edges: " + indirectBranchCount);
			Log.log("branch-stats: Total targets: " + targetCount);
			Log.log("branch-stats: Mean: " + (targetCount / (double) indirectBranchCount));
			Log.log("branch-stats: Total edges (degree 5): " + degree5BranchCount);
			Log.log("branch-stats: Percent having degree 2 or more: "
					+ (degree2BranchCount / (double) indirectBranchCount));
			Log.log("branch-stats: Percent having degree 3 or more: "
					+ (degree3BranchCount / (double) indirectBranchCount));
			Log.log("branch-stats: Percent having degree 5 or more: "
					+ (degree5BranchCount / (double) indirectBranchCount));
			Log.log("branch-stats: Percent having degree 10 or more: "
					+ (degree10BranchCount / (double) indirectBranchCount));
		}
	}

	private static final OptionArgumentMap.StringOption relocationOption = OptionArgumentMap.createStringOption('r',
			OptionMode.REQUIRED);

	private final ArgumentStack args;
	private final CommonMergeOptions options;

	private ClusterTraceDataSource dataSource;
	private ModuleGraphLoadSession loadSession;

	private EdgeCounter edgeCounter = new EdgeCounter();

	private File relocationDirectory;

	private Map<String, ModuleGraph<ModuleNode<?>>> graphs = new HashMap<String, ModuleGraph<ModuleNode<?>>>();

	private RelocationAnalyzer(ArgumentStack args) {
		this.args = args;
		this.options = new CommonMergeOptions(args, CommonMergeOptions.crowdSafeCommonDir, relocationOption);
	}

	private ModuleGraph<?> findGraphForExit(long exitHash, String fromModuleName) {
		for (Map.Entry<String, ModuleGraph<ModuleNode<?>>> entry : graphs.entrySet()) {
			long hash = CrowdSafeTraceUtil.stringHash(String.format("%s/%s!callback", fromModuleName, entry.getKey()));
			if (hash == exitHash)
				return entry.getValue();
		}
		return null;
	}

	private ModuleGraph<?> findGraphForEntry(long entryHash, String toModuleName) {
		for (Map.Entry<String, ModuleGraph<ModuleNode<?>>> entry : graphs.entrySet()) {
			long hash = CrowdSafeTraceUtil.stringHash(String.format("%s/%s!callback", entry.getKey(), toModuleName));
			if (hash == entryHash)
				return entry.getValue();
		}
		return null;
	}

	private void run() {
		try {
			options.parseOptions();
			options.initializeGraphEnvironment();

			Log.addOutput(System.out);

			String path = args.pop();
			File directory = new File(path);
			if (!(directory.exists() && directory.isDirectory())) {
				Log.log("Illegal cluster graph directory '" + directory + "'; no such directory.");
				printUsageAndExit();
			}

			relocationDirectory = new File(relocationOption.getValue());
			if (!relocationDirectory.exists())
				throw new IllegalArgumentException("No such directory '" + relocationDirectory.getName() + "'");

			dataSource = new ClusterTraceDirectory(directory).loadExistingFiles();
			loadSession = new ModuleGraphLoadSession(dataSource);

			for (ApplicationModule cluster : dataSource.getReprsentedModules()) {
				ModuleGraph<?> graph = loadSession.loadClusterGraph(cluster);

				edgeCounter.countEdges(graph.getGraphData().nodesByHash);

				if (graph.metadata.sequences.size() > 0) {
					if (graph.metadata.sequences.size() == 1) {
						graphs.put(graph.module.filename, (ModuleGraph<ModuleNode<?>>) graph);
					} else {
						throw new IllegalArgumentException("Error--multiple metadata sequences in module " + graph.name);
					}
				}
			}

			edgeCounter.report();

			Map<ApplicationModule, Boolean> suspiciousExitMap = new HashMap<ApplicationModule, Boolean>();
			for (ModuleGraph<ModuleNode<?>> graph : graphs.values()) {
				suspiciousExitMap.put(graph.module, false);
				ClusterMetadataSequence sequence = graph.metadata.sequences.values().iterator().next();
				for (ClusterMetadataExecution execution : sequence.executions) {
					for (ClusterUIB uib : execution.uibs) {
						if (!uib.isAdmitted && uib.edge.getFromNode().getType() != MetaNodeType.RETURN
								&& uib.edge.getToNode().getType() == MetaNodeType.MODULE_EXIT) {
							suspiciousExitMap.put(graph.module, true);
						}
					}
				}
				Log.log("Cluster " + graph.module.filename + " has "
						+ (suspiciousExitMap.get(graph.module) ? "" : "no") + " suspicious exits");

				long anonymousExitHash = CrowdSafeTraceUtil.stringHash(String.format("%s/<anonymous>!callback",
						graph.module.filename));
				int urCount = 0;
				for (ModuleNode<?> exit : graph.getExitPoints()) {
					if (exit.getHash() == anonymousExitHash) {
						OrdinalEdgeList<ModuleNode<?>> incoming = exit.getIncomingEdges();
						try {
							for (Edge<ModuleNode<?>> exitEdge : incoming) {
								if (exitEdge.getEdgeType() == EdgeType.UNEXPECTED_RETURN) {
									urCount++;
									OrdinalEdgeList<ModuleNode<?>> outgoing = exitEdge.getFromNode().getOutgoingEdges();
									try {
										for (Edge<ModuleNode<?>> peerEdge : outgoing) {
											if (peerEdge != exitEdge
													&& (peerEdge.getToNode().getType() != MetaNodeType.MODULE_EXIT || peerEdge
															.getToNode().getHash() != anonymousExitHash)) {
												System.out
														.println("Node "
																+ exitEdge.getFromNode()
																+ " has unexpected returns into gencode and also edges to other places");
												Log.log("\tUR: " + exitEdge);
												Log.log("\tOther: " + peerEdge);
											}
										}
									} finally {
										outgoing.release();
									}
								}
							}
						} finally {
							incoming.release();
						}
					}
				}
				Log.log("Checked " + urCount + " unexpected returns for module " + graph.module.filename);
			}

			int whiteBoxCycleCount = 0, checkedWhiteBoxEntries = 0, checkedWhiteBoxNodes = 0;
			ModuleGraph<ModuleNode<?>> anonymousGraph = loadSession
					.loadClusterGraph(ApplicationModule.ANONYMOUS_MODULE); // graphs.get(ApplicationModule.ANONYMOUS_UNIT_NAME);
			if (anonymousGraph != null) {
				Set<ModuleNode<?>> visitedNodes = new HashSet<ModuleNode<?>>();
				LinkedList<ModuleNode<?>> queue = new LinkedList<ModuleNode<?>>();
				for (ModuleNode<?> entry : anonymousGraph.getEntryPoints()) {
					checkedWhiteBoxEntries++;
					OrdinalEdgeList<ModuleNode<?>> outgoing = entry.getOutgoingEdges();
					try {
						for (Edge<ModuleNode<?>> entryEdge : outgoing) {
							if (entryEdge.getToNode().isBlackBoxSingleton())
								continue;
							visitedNodes.clear();
							queue.clear();
							checkedWhiteBoxNodes++;
							queue.addFirst(entryEdge.getToNode());
							while (!queue.isEmpty()) {
								ModuleNode<?> next = queue.removeLast();
								if (visitedNodes.contains(next)) {
									whiteBoxCycleCount++;
									break;
								}
								visitedNodes.add(next);
								OrdinalEdgeList<ModuleNode<?>> traversal = next.getOutgoingEdges();
								try {
									for (Edge<ModuleNode<?>> traversalEdge : traversal) {
										if (traversalEdge.getToNode().getType() != MetaNodeType.MODULE_EXIT) {
											checkedWhiteBoxNodes++;
											queue.addFirst(traversalEdge.getToNode());
										}
									}
								} finally {
									traversal.release();
								}
							}
						}
					} finally {
						outgoing.release();
					}
				}
			}

			Log.log("White boxes having cycles: " + whiteBoxCycleCount + " (checked " + checkedWhiteBoxEntries
					+ " entries and " + checkedWhiteBoxNodes + " nodes)");

			Log.log("----");

			int halfClearedSuspicion = 0;
			int halfOriginallySuspicious = 0;
			int totalClearedSuspicion = 0;
			int totalOriginallySuspicious = 0;
			int singletonExecutionModulesSkipped = 0;
			int multipleExecutionModulesSkipped = 0;
			int totalSuibSkipped = 0;
			int maxExecutionsPerModule = 0;
			for (ModuleGraph<?> graph : graphs.values()) {
				String moduleName = graph.module.filename;
				ModuleRelocations relocations = null;
				boolean singleton = false;

				File relocationFile = new File(relocationDirectory, moduleName + ".relocations.dat");
				if (relocationFile.exists()) {
					relocations = new ModuleRelocations(relocationFile);
					Log.log("Found " + relocations.size() + " relocatable targets for module " + moduleName);
				}

				ClusterMetadataSequence sequence = graph.metadata.sequences.values().iterator().next();
				Log.log("Graph " + graph.name + " has " + sequence.executions.size() + " metadata executions");

				singleton = sequence.executions.size() == 1;
				if (relocations == null) {
					if (singleton)
						singletonExecutionModulesSkipped++;
					else
						multipleExecutionModulesSkipped++;
				}

				if (sequence.executions.size() > maxExecutionsPerModule)
					maxExecutionsPerModule = sequence.executions.size();

				boolean isSuspicious;
				int clearedSuspicion = 0;
				int originallySuspicious = 0;
				int executionIndex = 0;
				int suibSkipped = 0;
				int suspiciousInExecution;
				boolean lastHalf = false;
				for (ClusterMetadataExecution execution : sequence.executions) {
					if (relocations == null) {
						for (ClusterUIB uib : execution.uibs) {
							if (uib.edge == null) {
								System.err.println("Error: edge missing from uib!");
								continue;
							}
							if (!uib.isAdmitted)
								suibSkipped++;
						}
						continue;
					}

					executionIndex++;
					suspiciousInExecution = 0;
					if (!lastHalf && executionIndex >= (sequence.executions.size() / 2)) {
						lastHalf = true;
						halfOriginallySuspicious += originallySuspicious;
						halfClearedSuspicion += clearedSuspicion;
					}
					for (ClusterUIB uib : execution.uibs) {
						isSuspicious = false;
						try {
							if (uib.edge == null) {
								System.err.println("Error: edge missing from uib!");
								continue;
							}
							if (uib.edge.getFromNode().getType() == MetaNodeType.RETURN)
								Log.log("Warning: UIB from a return node! " + uib.edge);
							if (uib.isAdmitted)
								continue;

							Log.log("SUIB (original): " + uib.edge);

							boolean isRelocatableTarget = relocations != null
									&& relocations.containsTag((long) uib.edge.getToNode().getRelativeTag());
							if (uib.edge.getToNode().isMetaNode()) {
								if (!isRelocatableTarget)
									Log.log("SUIB (exit): " + uib.edge);
								continue;
							}
							originallySuspicious++;
							isSuspicious = true;
							if (isRelocatableTarget) {
								isSuspicious = false;
								continue;
							}
							if (uib.edge.getFromNode().getType() == MetaNodeType.MODULE_ENTRY) {
								ModuleGraph<?> fromGraph = findGraphForEntry(uib.edge.getFromNode().getHash(),
										graph.module.filename);
								if (fromGraph == null) {
									if (ApplicationModuleSet.getInstance().isFromAnonymous(
											uib.edge.getFromNode().getHash())) {
										Log.log("SUIB from anonymous: " + uib.edge);
										continue;
									}
									long dllEntryHash = CrowdSafeTraceUtil.stringHash("!DllMain");
									if (dllEntryHash == uib.edge.getFromNode().getHash()) {
										isSuspicious = false;
										continue; // dll entry is not suspicious...
									} else {
										Log.log("SUIB: " + uib.edge);
										Log.log("Warning: cannot find the exit graph matching the entry of " + uib.edge);
									}
									continue;
								}
								if (!suspiciousExitMap.get(fromGraph.module)) {
									isSuspicious = false;
									continue;
								}
								Node<?> exit = fromGraph.getExitPoint(uib.edge.getFromNode().getHash());
								if (exit == null) {
									Log.log("Warning: cannot find the exit node in " + fromGraph.module.filename
											+ " (matching the entry of " + uib.edge + ")");
									continue;
								}
								OrdinalEdgeList<?> incoming = exit.getIncomingEdges();
								try {
									boolean allReturns = true;
									for (Edge<?> edge : incoming) {
										if (edge.getFromNode().getType() != MetaNodeType.RETURN) {
											allReturns = false;
											break;
										}
										// Log.log("\tSUIB: from " + edge);
									}
									if (allReturns) {
										isSuspicious = false;
										continue;
									}
									Log.log("SUIB: <" + fromGraph.module.filename + "> " + uib.edge);
								} finally {
									incoming.release();
								}
							} else if (uib.edge.getFromNode().getType() == MetaNodeType.RETURN) {
								Log.log("Warning: SUIB from a return node! " + uib.edge);
								isSuspicious = false;
							} else {
								Log.log("SUIB: " + uib.edge + "; other edges: ");
								OrdinalEdgeList<?> outgoing = uib.edge.getFromNode().getOutgoingEdges();
								try {
									for (Edge<?> edge : outgoing) {
										if (edge != uib.edge) {
											isRelocatableTarget = relocations != null
													&& relocations
															.containsTag((long) edge.getToNode().getRelativeTag());
											String tag = "";
											if (edge.getToNode().getType() == MetaNodeType.MODULE_EXIT) {
												ModuleGraph<?> toGraph = findGraphForExit(edge.getToNode().getHash(),
														graph.module.filename);
												if (toGraph != null)
													tag = "<" + toGraph.module.filename + ">";
											}
											if (isRelocatableTarget)
												Log.log("\t> !SUIB: " + edge + " " + tag);
											else
												Log.log("\t> ?SUIB: " + edge + " " + tag);
										}
									}
								} finally {
									outgoing.release();
								}
							}
						} finally {
							if (isSuspicious) {
								Log.log(String.format("&SUIB-#%d&%s&%s", executionIndex, uib.edge.getFromNode(),
										uib.edge.getToNode()));
								/*
								 * OrdinalEdgeList<ClusterNode<?>> edges = uib.edge.getFromNode().getOutgoingEdges();
								 * try { System.out
								 * .println(String.format("&SUIB-#%d&%s&%s -- total outgoing edges: %d", executionIndex,
								 * uib.edge.getFromNode(), uib.edge.getToNode(), edges.size())); for (Edge<?> edge :
								 * edges) { if (edge != uib.edge) { boolean isRelocatableTarget = relocations != null &&
								 * relocations.relocatableTargets.contains((long) edge.getToNode() .getRelativeTag());
								 * String tag = ""; if (edge.getToNode().getType() == MetaNodeType.CLUSTER_EXIT) {
								 * ModuleGraphCluster<?> toGraph = findGraphForExit(edge.getToNode() .getHash(),
								 * graph.cluster.filename); if (toGraph != null) tag = "<" + toGraph.cluster.filename +
								 * ">"; } if (isRelocatableTarget) Log.log("\t> !SUIB: " + edge + " " + tag); else
								 * Log.log("\t> ?SUIB: " + edge + " " + tag); } } Log.log("&SUIB&"); } finally {
								 * edges.release(); }
								 */
								suspiciousInExecution++;
							} else {
								clearedSuspicion++;
							}
						}
					}
					if (suspiciousInExecution > 0)
						System.out
								.println("@ Found " + suspiciousInExecution + " SUIB in execution #" + executionIndex);
				}

				if (relocations == null) {
					totalSuibSkipped += suibSkipped;
					System.err.println("Warning: no relocations for module " + moduleName + " ("
							+ sequence.executions.size() + " executions). Skipping it.");
					Log.log("Warning: no relocations for module " + moduleName + " (" + sequence.executions.size()
							+ " executions). Skipping " + suibSkipped + " suspicious branches.");
				}
				totalClearedSuspicion += clearedSuspicion;
				totalOriginallySuspicious += originallySuspicious;
				Log.log("Cleared suspicion for #" + clearedSuspicion + "# of #" + originallySuspicious
						+ "# suspcious targets in " + moduleName + " (#" + (originallySuspicious - clearedSuspicion)
						+ "# remain suspicious)");
			}

			Log.log("@ Analyzed " + maxExecutionsPerModule + " total app executions");

			Log.log("Half: #" + halfClearedSuspicion + "# of #" + halfOriginallySuspicious + "# suspicious targets (#"
					+ (halfOriginallySuspicious - halfClearedSuspicion) + "# remain suspicious)");
			Log.log("Total: cleared suspicion for #" + totalClearedSuspicion + "# of #" + totalOriginallySuspicious
					+ "# suspicious targets (#" + (totalOriginallySuspicious - totalClearedSuspicion)
					+ "# remain suspicious, #" + totalSuibSkipped + "# skipped)");
			Log.log("Skip: #" + singletonExecutionModulesSkipped + "# singleton execution modules skipped, #"
					+ multipleExecutionModulesSkipped + "# multiple execution modules skipped, #" + totalSuibSkipped
					+ "# skipped)");

			// for (ClusterMetadataSequence sequence : graph.metadata.sequences.values()) {

			// }
		} catch (Throwable t) {
			t.printStackTrace(System.err);
		}
	}

	private void printUsageAndExit() {
		System.out.println(String
				.format("Usage: %s -r <relocation-dir> <cluster-data-dir>", getClass().getSimpleName()));
		System.exit(1);
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		RelocationAnalyzer analyzer = new RelocationAnalyzer(stack);
		analyzer.run();
	}
}
