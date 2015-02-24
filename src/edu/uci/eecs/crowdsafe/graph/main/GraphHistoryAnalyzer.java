package edu.uci.eecs.crowdsafe.graph.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.graph.data.graph.Node;
import edu.uci.eecs.crowdsafe.graph.data.graph.NodeHashMap;
import edu.uci.eecs.crowdsafe.graph.data.graph.NodeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.loader.ClusterGraphLoadSession;
import edu.uci.eecs.crowdsafe.graph.io.cluster.ClusterTraceDataSource;
import edu.uci.eecs.crowdsafe.graph.io.cluster.ClusterTraceDirectory;

public class GraphHistoryAnalyzer {

	private class NodeIdentifier {
		final String moduleName;
		final int tag;

		NodeIdentifier(Node<?> node) {
			this.moduleName = node.getModule().unit.name + ":" + node.getModule().unit.version;
			this.tag = node.getRelativeTag();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + ((moduleName == null) ? 0 : moduleName.hashCode());
			result = prime * result + (int) (tag ^ (tag >>> 32));
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			NodeIdentifier other = (NodeIdentifier) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (moduleName == null) {
				if (other.moduleName != null)
					return false;
			} else if (!moduleName.equals(other.moduleName))
				return false;
			if (tag != other.tag)
				return false;
			return true;
		}

		private GraphHistoryAnalyzer getOuterType() {
			return GraphHistoryAnalyzer.this;
		}
	}

	private class EdgeAnalyzer {

		private final List<Long> anonymousEntryHashes = new ArrayList<Long>();
		private final List<Long> anonymousExitHashes = new ArrayList<Long>();

		private final Set<NodeIdentifier> toGencode = new HashSet<NodeIdentifier>();
		private final Set<NodeIdentifier> fromGencode = new HashSet<NodeIdentifier>();
		private final Set<NodeIdentifier> unexpectedReturnSites = new HashSet<NodeIdentifier>();

		private final Map<String, ModuleRelocations> moduleRelocations;

		EdgeAnalyzer(File relocationDirectory) throws IOException {
			moduleRelocations = ModuleRelocations.loadAllRelocations(relocationDirectory);
		}

		private void analyzeGraph(ModuleGraphCluster<?> graph) throws IOException {
			if (graph.cluster.isAnonymous())
				return;

			NodeHashMap<?> nodeMap = graph.getGraphData().nodesByHash;
			for (Long hash : nodeMap.keySet()) {
				NodeList<?> nodes = nodeMap.get(hash);
				for (int i = 0; i < nodes.size(); i++) {
					ClusterNode<?> node = (ClusterNode<?>) nodes.get(i);
					OrdinalEdgeList<ClusterNode<?>> outgoing = node.getOutgoingEdges();
					try {
						for (Edge<?> edge : outgoing) {
							if (edge.getEdgeType() == EdgeType.UNEXPECTED_RETURN) {
								NodeIdentifier id = new NodeIdentifier(node);
								if (!unexpectedReturnSites.contains(id)) {
									unexpectedReturnSites.add(id);
									Log.log("#%d: new unexpected return site %s (%s)", graphCount, node, currentRun);
								}
							}
						}
					} finally {
						outgoing.release();
					}
				}
			}

			for (long entryHash : anonymousEntryHashes) {
				ClusterNode<?> anonymousEntry = (ClusterNode<?>) graph.getEntryPoint(entryHash);
				if (anonymousEntry != null) {
					OrdinalEdgeList<ClusterNode<?>> outgoing = anonymousEntry.getOutgoingEdges();
					try {
						for (Edge<?> edge : outgoing) {
							NodeIdentifier id = new NodeIdentifier(edge.getToNode());
							if (!fromGencode.contains(id)) {
								fromGencode.add(id);
								Log.log("#%d: new callout target from gencode to %s (%s in graph %s). %s.", graphCount,
										edge.getToNode(), edge.getEdgeType(), currentRun,
										isRelocatable(graph, edge.getToNode()));
							}
						}
					} finally {
						outgoing.release();
					}
				}
			}

			for (long exitHash : anonymousExitHashes) {
				ClusterNode<?> anonymousExit = (ClusterNode<?>) graph.getExitPoint(exitHash);
				if (anonymousExit != null) {
					OrdinalEdgeList<ClusterNode<?>> incoming = anonymousExit.getIncomingEdges();
					try {
						for (Edge<ClusterNode<?>> edge : incoming) {
							NodeIdentifier id = new NodeIdentifier(edge.getFromNode());
							if (!toGencode.contains(id)) {
								toGencode.add(id);
								Log.log("#%d: new callsite into gencode from %s (%s in graph %s)", graphCount,
										edge.getFromNode(), edge.getEdgeType(), currentRun);
							}
						}
					} finally {
						incoming.release();
					}
				}
			}
		}

		private String isRelocatable(ModuleGraphCluster<?> graph, Node<?> node) {
			ModuleRelocations relocations = moduleRelocations.get(graph.cluster.getUnitFilename());
			if (relocations == null)
				return "<no-relocations>";

			if (relocations.containsTag((long) node.getRelativeTag())) {
				return "<relocatable-target>";
			} else {
				return "<obscure-target>";
			}
		}

		private void setupAnonymousHashes(ModuleGraphCluster<?> anonymous) {
			anonymousEntryHashes.clear();
			anonymousExitHashes.clear();

			if (anonymous == null || !anonymous.cluster.isAnonymous()) {
				return;
			}

			for (Object entry : anonymous.getEntryPoints()) {
				anonymousExitHashes.add(((ClusterNode<?>) entry).getHash());
			}
			for (Object exit : anonymous.getExitPoints()) {
				anonymousEntryHashes.add(((ClusterNode<?>) exit).getHash());
			}

			// Log.log("Setup %d entries into gencode and %d exits from gencode (for cluster %s)",
			// anonymousEntryHashes.size(), anonymousExitHashes.size(), anonymous.cluster.id);
		}
	}

	private final ArgumentStack args;
	private final CommonMergeOptions options;

	private static final OptionArgumentMap.StringOption relocationOption = OptionArgumentMap.createStringOption('r',
			OptionMode.REQUIRED);

	private ClusterTraceDataSource dataSource;
	private ClusterGraphLoadSession loadSession;

	private EdgeAnalyzer edgeAnalyzer;

	private File relocationDirectory;

	private Map<String, ModuleGraphCluster<ClusterNode<?>>> graphs = new HashMap<String, ModuleGraphCluster<ClusterNode<?>>>();

	private int graphCount = 0;
	private String currentRun;

	private GraphHistoryAnalyzer(ArgumentStack args) {
		this.args = args;
		this.options = new CommonMergeOptions(args, CommonMergeOptions.crowdSafeCommonDir, relocationOption);
	}

	private ModuleGraphCluster<?> loadGraph(AutonomousSoftwareDistribution cluster) throws IOException {
		ModuleGraphCluster<?> graph;
		Log.setSilent(true);
		graph = loadSession.loadClusterGraph(cluster);
		Log.setSilent(false);
		return graph;
	}

	private void run() {
		try {
			options.parseOptions();
			options.initializeGraphEnvironment();

			Log.addOutput(System.out);

			relocationDirectory = new File(relocationOption.getValue());
			edgeAnalyzer = new EdgeAnalyzer(relocationDirectory);
			if (!relocationDirectory.exists())
				throw new IllegalArgumentException("No such directory '" + relocationDirectory.getName() + "'");

			String path = args.pop();
			File runCatalog = new File(path);
			if (!(runCatalog.exists() && runCatalog.isFile())) {
				Log.error("Illegal run catalog '%s'; no such file.", runCatalog.getAbsolutePath());
				printUsageAndExit();
			}

			List<File> runDirectories = new ArrayList<File>();
			BufferedReader in = new BufferedReader(new FileReader(runCatalog));
			boolean failed = false;
			while (in.ready()) {
				String runPath = in.readLine();
				File runDirectory = new File(runPath);
				if (!(runDirectory.exists() && runDirectory.isDirectory())) {
					Log.error("Run catalog contains an invalid run directory: %s. Exiting now.",
							runDirectory.getAbsolutePath());
					failed = true;
					break;
				}
				runDirectories.add(runDirectory);
			}
			in.close();
			if (failed)
				return;

			for (File runDirectory : runDirectories) {
				dataSource = new ClusterTraceDirectory(runDirectory).loadExistingFiles();
				loadSession = new ClusterGraphLoadSession(dataSource);

				edgeAnalyzer.setupAnonymousHashes(loadGraph(ConfiguredSoftwareDistributions.ANONYMOUS_CLUSTER));
				graphCount++;
				currentRun = runDirectory.getName();
				for (AutonomousSoftwareDistribution cluster : dataSource.getReprsentedClusters()) {
					ModuleGraphCluster<?> graph = loadGraph(cluster);
					edgeAnalyzer.analyzeGraph(graph);
				}
			}

			Log.log("Analyzed %d graphs", runDirectories.size());
		} catch (Throwable t) {
			t.printStackTrace(System.err);
		}
	}

	private void printUsageAndExit() {
		System.out.println(String.format("Usage: %s <run-catalog>", getClass().getSimpleName()));
		System.out.println("# The run catalog lists relative paths to the run directories.");
		System.out.println("# Entries must be in execution sequence, one per line..");
		System.exit(1);
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		GraphHistoryAnalyzer analyzer = new GraphHistoryAnalyzer(stack);
		analyzer.run();
	}
}
