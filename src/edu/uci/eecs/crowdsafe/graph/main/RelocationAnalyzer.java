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
import edu.uci.eecs.crowdsafe.graph.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.graph.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.graph.data.graph.Node;
import edu.uci.eecs.crowdsafe.graph.data.graph.NodeHashMap;
import edu.uci.eecs.crowdsafe.graph.data.graph.NodeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.loader.ClusterGraphLoadSession;
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
			System.out.println("branch-stats: Total edges: " + indirectBranchCount);
			System.out.println("branch-stats: Total targets: " + targetCount);
			System.out.println("branch-stats: Mean: " + (targetCount / (double) indirectBranchCount));
			System.out.println("branch-stats: Total edges (degree 5): " + degree5BranchCount);
			System.out.println("branch-stats: Percent having degree 2 or more: "
					+ (degree2BranchCount / (double) indirectBranchCount));
			System.out.println("branch-stats: Percent having degree 3 or more: "
					+ (degree3BranchCount / (double) indirectBranchCount));
			System.out.println("branch-stats: Percent having degree 5 or more: "
					+ (degree5BranchCount / (double) indirectBranchCount));
			System.out.println("branch-stats: Percent having degree 10 or more: "
					+ (degree10BranchCount / (double) indirectBranchCount));
		}
	}

	private static final OptionArgumentMap.StringOption relocationOption = OptionArgumentMap.createStringOption('r',
			OptionMode.REQUIRED);

	private final ArgumentStack args;
	private final CommonMergeOptions options;

	private ClusterTraceDataSource dataSource;
	private ClusterGraphLoadSession loadSession;

	private EdgeCounter edgeCounter = new EdgeCounter();

	private File relocationDirectory;

	private Map<String, ModuleGraphCluster<ClusterNode<?>>> graphs = new HashMap<String, ModuleGraphCluster<ClusterNode<?>>>();

	private RelocationAnalyzer(ArgumentStack args) {
		this.args = args;
		this.options = new CommonMergeOptions(args, CommonMergeOptions.crowdSafeCommonDir, relocationOption);
	}

	private ModuleGraphCluster<?> findGraphForExit(long exitHash, String fromModuleName) {
		for (Map.Entry<String, ModuleGraphCluster<ClusterNode<?>>> entry : graphs.entrySet()) {
			long hash = CrowdSafeTraceUtil.stringHash(String.format("%s/%s!callback", fromModuleName, entry.getKey()));
			if (hash == exitHash)
				return entry.getValue();
		}
		return null;
	}

	private ModuleGraphCluster<?> findGraphForEntry(long entryHash, String toModuleName) {
		for (Map.Entry<String, ModuleGraphCluster<ClusterNode<?>>> entry : graphs.entrySet()) {
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
			loadSession = new ClusterGraphLoadSession(dataSource);

			for (AutonomousSoftwareDistribution cluster : dataSource.getReprsentedClusters()) {
				ModuleGraphCluster<?> graph = loadSession.loadClusterGraph(cluster);

				edgeCounter.countEdges(graph.getGraphData().nodesByHash);

				if (graph.metadata.sequences.size() > 0) {
					if (graph.metadata.sequences.size() == 1) {
						graphs.put(graph.cluster.getUnitFilename(), (ModuleGraphCluster<ClusterNode<?>>) graph);
					} else {
						throw new IllegalArgumentException("Error--multiple metadata sequences in module " + graph.name);
					}
				}
			}

			edgeCounter.report();

			Map<AutonomousSoftwareDistribution, Boolean> suspiciousExitMap = new HashMap<AutonomousSoftwareDistribution, Boolean>();
			for (ModuleGraphCluster<ClusterNode<?>> graph : graphs.values()) {
				suspiciousExitMap.put(graph.cluster, false);
				ClusterMetadataSequence sequence = graph.metadata.sequences.values().iterator().next();
				for (ClusterMetadataExecution execution : sequence.executions) {
					for (ClusterUIB uib : execution.uibs) {
						if (!uib.isAdmitted && uib.edge.getFromNode().getType() != MetaNodeType.RETURN
								&& uib.edge.getToNode().getType() == MetaNodeType.CLUSTER_EXIT) {
							suspiciousExitMap.put(graph.cluster, true);
						}
					}
				}
				System.out.println("Cluster " + graph.cluster.getUnitFilename() + " has "
						+ (suspiciousExitMap.get(graph.cluster) ? "" : "no") + " suspicious exits");

				long anonymousExitHash = CrowdSafeTraceUtil.stringHash(String.format("%s/<anonymous>!callback",
						graph.cluster.getUnitFilename()));
				int urCount = 0;
				for (ClusterNode<?> exit : graph.getExitPoints()) {
					if (exit.getHash() == anonymousExitHash) {
						OrdinalEdgeList<ClusterNode<?>> incoming = exit.getIncomingEdges();
						try {
							for (Edge<ClusterNode<?>> exitEdge : incoming) {
								if (exitEdge.getEdgeType() == EdgeType.UNEXPECTED_RETURN) {
									urCount++;
									OrdinalEdgeList<ClusterNode<?>> outgoing = exitEdge.getFromNode()
											.getOutgoingEdges();
									try {
										for (Edge<ClusterNode<?>> peerEdge : outgoing) {
											if (peerEdge != exitEdge
													&& (peerEdge.getToNode().getType() != MetaNodeType.CLUSTER_EXIT || peerEdge
															.getToNode().getHash() != anonymousExitHash)) {
												System.out
														.println("Node "
																+ exitEdge.getFromNode()
																+ " has unexpected returns into gencode and also edges to other places");
												System.out.println("\tUR: " + exitEdge);
												System.out.println("\tOther: " + peerEdge);
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
				System.out.println("Checked " + urCount + " unexpected returns for module "
						+ graph.cluster.getUnitFilename());
			}

			int whiteBoxCycleCount = 0, checkedWhiteBoxEntries = 0, checkedWhiteBoxNodes = 0;
			ModuleGraphCluster<ClusterNode<?>> anonymousGraph = loadSession
					.loadClusterGraph(ConfiguredSoftwareDistributions.ANONYMOUS_CLUSTER); // graphs.get(SoftwareUnit.ANONYMOUS_UNIT_NAME);
			if (anonymousGraph != null) {
				Set<ClusterNode<?>> visitedNodes = new HashSet<ClusterNode<?>>();
				LinkedList<ClusterNode<?>> queue = new LinkedList<ClusterNode<?>>();
				for (ClusterNode<?> entry : anonymousGraph.getEntryPoints()) {
					checkedWhiteBoxEntries++;
					OrdinalEdgeList<ClusterNode<?>> outgoing = entry.getOutgoingEdges();
					try {
						for (Edge<ClusterNode<?>> entryEdge : outgoing) {
							if (entryEdge.getToNode().isBlackBoxSingleton())
								continue;
							visitedNodes.clear();
							queue.clear();
							checkedWhiteBoxNodes++;
							queue.addFirst(entryEdge.getToNode());
							while (!queue.isEmpty()) {
								ClusterNode<?> next = queue.removeLast();
								if (visitedNodes.contains(next)) {
									whiteBoxCycleCount++;
									break;
								}
								visitedNodes.add(next);
								OrdinalEdgeList<ClusterNode<?>> traversal = next.getOutgoingEdges();
								try {
									for (Edge<ClusterNode<?>> traversalEdge : traversal) {
										if (traversalEdge.getToNode().getType() != MetaNodeType.CLUSTER_EXIT) {
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

			System.out.println("White boxes having cycles: " + whiteBoxCycleCount + " (checked "
					+ checkedWhiteBoxEntries + " entries and " + checkedWhiteBoxNodes + " nodes)");

			System.out.println("----");

			int halfClearedSuspicion = 0;
			int halfOriginallySuspicious = 0;
			int totalClearedSuspicion = 0;
			int totalOriginallySuspicious = 0;
			int singletonExecutionModulesSkipped = 0;
			int multipleExecutionModulesSkipped = 0;
			int totalSuibSkipped = 0;
			int maxExecutionsPerModule = 0;
			for (ModuleGraphCluster<?> graph : graphs.values()) {
				String moduleName = graph.cluster.getUnitFilename();
				ModuleRelocations relocations = null;
				boolean singleton = false;

				File relocationFile = new File(relocationDirectory, moduleName + ".relocations.dat");
				if (relocationFile.exists()) {
					relocations = new ModuleRelocations(relocationFile);
					System.out.println("Found " + relocations.size() + " relocatable targets for module " + moduleName);
				}

				ClusterMetadataSequence sequence = graph.metadata.sequences.values().iterator().next();
				System.out.println("Graph " + graph.name + " has " + sequence.executions.size()
						+ " metadata executions");

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
								System.out.println("Warning: UIB from a return node! " + uib.edge);
							if (uib.isAdmitted)
								continue;

							System.out.println("SUIB (original): " + uib.edge);

							boolean isRelocatableTarget = relocations != null
									&& relocations.containsTag((long) uib.edge.getToNode().getRelativeTag());
							if (uib.edge.getToNode().isMetaNode()) {
								if (!isRelocatableTarget)
									System.out.println("SUIB (exit): " + uib.edge);
								continue;
							}
							originallySuspicious++;
							isSuspicious = true;
							if (isRelocatableTarget) {
								isSuspicious = false;
								continue;
							}
							if (uib.edge.getFromNode().getType() == MetaNodeType.CLUSTER_ENTRY) {
								ModuleGraphCluster<?> fromGraph = findGraphForEntry(uib.edge.getFromNode().getHash(),
										graph.cluster.getUnitFilename());
								if (fromGraph == null) {
									AutonomousSoftwareDistribution cluster = ConfiguredSoftwareDistributions
											.getInstance().getClusterByAnonymousExitHash(
													uib.edge.getFromNode().getHash());
									if (cluster != null) {
										System.out.println("SUIB from anonymous: " + uib.edge);
										continue;
									}
									long dllEntryHash = CrowdSafeTraceUtil.stringHash("!DllMain");
									if (dllEntryHash == uib.edge.getFromNode().getHash()) {
										isSuspicious = false;
										continue; // dll entry is not suspicious...
									} else {
										System.out.println("SUIB: " + uib.edge);
										System.out.println("Warning: cannot find the exit graph matching the entry of "
												+ uib.edge);
									}
									continue;
								}
								if (!suspiciousExitMap.get(fromGraph.cluster)) {
									isSuspicious = false;
									continue;
								}
								Node<?> exit = fromGraph.getExitPoint(uib.edge.getFromNode().getHash());
								if (exit == null) {
									System.out.println("Warning: cannot find the exit node in "
											+ fromGraph.cluster.getUnitFilename() + " (matching the entry of "
											+ uib.edge + ")");
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
										// System.out.println("\tSUIB: from " + edge);
									}
									if (allReturns) {
										isSuspicious = false;
										continue;
									}
									System.out.println("SUIB: <" + fromGraph.cluster.getUnitFilename() + "> "
											+ uib.edge);
								} finally {
									incoming.release();
								}
							} else if (uib.edge.getFromNode().getType() == MetaNodeType.RETURN) {
								System.out.println("Warning: SUIB from a return node! " + uib.edge);
								isSuspicious = false;
							} else {
								System.out.println("SUIB: " + uib.edge + "; other edges: ");
								OrdinalEdgeList<?> outgoing = uib.edge.getFromNode().getOutgoingEdges();
								try {
									for (Edge<?> edge : outgoing) {
										if (edge != uib.edge) {
											isRelocatableTarget = relocations != null
													&& relocations
															.containsTag((long) edge.getToNode().getRelativeTag());
											String tag = "";
											if (edge.getToNode().getType() == MetaNodeType.CLUSTER_EXIT) {
												ModuleGraphCluster<?> toGraph = findGraphForExit(edge.getToNode()
														.getHash(), graph.cluster.getUnitFilename());
												if (toGraph != null)
													tag = "<" + toGraph.cluster.getUnitFilename() + ">";
											}
											if (isRelocatableTarget)
												System.out.println("\t> !SUIB: " + edge + " " + tag);
											else
												System.out.println("\t> ?SUIB: " + edge + " " + tag);
										}
									}
								} finally {
									outgoing.release();
								}
							}
						} finally {
							if (isSuspicious) {
								System.out.println(String.format("&SUIB-#%d&%s&%s", executionIndex,
										uib.edge.getFromNode(), uib.edge.getToNode()));
								/*
								 * OrdinalEdgeList<ClusterNode<?>> edges = uib.edge.getFromNode().getOutgoingEdges();
								 * try { System.out
								 * .println(String.format("&SUIB-#%d&%s&%s -- total outgoing edges: %d", executionIndex,
								 * uib.edge.getFromNode(), uib.edge.getToNode(), edges.size())); for (Edge<?> edge :
								 * edges) { if (edge != uib.edge) { boolean isRelocatableTarget = relocations != null &&
								 * relocations.relocatableTargets.contains((long) edge.getToNode() .getRelativeTag());
								 * String tag = ""; if (edge.getToNode().getType() == MetaNodeType.CLUSTER_EXIT) {
								 * ModuleGraphCluster<?> toGraph = findGraphForExit(edge.getToNode() .getHash(),
								 * graph.cluster.getUnitFilename()); if (toGraph != null) tag = "<" +
								 * toGraph.cluster.getUnitFilename() + ">"; } if (isRelocatableTarget)
								 * System.out.println("\t> !SUIB: " + edge + " " + tag); else
								 * System.out.println("\t> ?SUIB: " + edge + " " + tag); } }
								 * System.out.println("&SUIB&"); } finally { edges.release(); }
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
					System.out.println("Warning: no relocations for module " + moduleName + " ("
							+ sequence.executions.size() + " executions). Skipping " + suibSkipped
							+ " suspicious branches.");
				}
				totalClearedSuspicion += clearedSuspicion;
				totalOriginallySuspicious += originallySuspicious;
				System.out.println("Cleared suspicion for #" + clearedSuspicion + "# of #" + originallySuspicious
						+ "# suspcious targets in " + moduleName + " (#" + (originallySuspicious - clearedSuspicion)
						+ "# remain suspicious)");
			}

			System.out.println("@ Analyzed " + maxExecutionsPerModule + " total app executions");

			System.out.println("Half: #" + halfClearedSuspicion + "# of #" + halfOriginallySuspicious
					+ "# suspicious targets (#" + (halfOriginallySuspicious - halfClearedSuspicion)
					+ "# remain suspicious)");
			System.out.println("Total: cleared suspicion for #" + totalClearedSuspicion + "# of #"
					+ totalOriginallySuspicious + "# suspicious targets (#"
					+ (totalOriginallySuspicious - totalClearedSuspicion) + "# remain suspicious, #" + totalSuibSkipped
					+ "# skipped)");
			System.out.println("Skip: #" + singletonExecutionModulesSkipped
					+ "# singleton execution modules skipped, #" + multipleExecutionModulesSkipped
					+ "# multiple execution modules skipped, #" + totalSuibSkipped + "# skipped)");

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
