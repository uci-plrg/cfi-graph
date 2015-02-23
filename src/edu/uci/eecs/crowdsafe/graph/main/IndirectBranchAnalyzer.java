package edu.uci.eecs.crowdsafe.graph.main;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.graph.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.graph.data.graph.Node;
import edu.uci.eecs.crowdsafe.graph.data.graph.NodeHashMap;
import edu.uci.eecs.crowdsafe.graph.data.graph.NodeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.loader.ClusterGraphLoadSession;
import edu.uci.eecs.crowdsafe.graph.io.cluster.ClusterTraceDataSource;
import edu.uci.eecs.crowdsafe.graph.io.cluster.ClusterTraceDirectory;

public class IndirectBranchAnalyzer {

	private class EdgeCounter {
		private long returnCount = 0;
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
					if (node.getType() == MetaNodeType.RETURN) {
						returnCount++;
					} else {
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
		}

		private void report() {
			System.out.println("branch-stats: Total branchpoints: " + indirectBranchCount);
			System.out.println("branch-stats: Total returns: " + returnCount);
			System.out.println("branch-stats: Return ratio: "
					+ (returnCount / (double) (indirectBranchCount + returnCount)));

			System.out.println("branch-stats: Total branch targets: " + targetCount);
			System.out.println("branch-stats: Mean targets per branchpoint: "
					+ (targetCount / (double) indirectBranchCount));

			System.out.println("branch-stats: Total branchpoints (degree 2): " + degree2BranchCount);
			System.out.println("branch-stats: Total branchpoints (degree 3): " + degree3BranchCount);
			System.out.println("branch-stats: Total branchpoints (degree 5): " + degree5BranchCount);
			System.out.println("branch-stats: Total branchpoints (degree 10): " + degree10BranchCount);
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

	private final ArgumentStack args;
	private final CommonMergeOptions options;

	private ClusterTraceDataSource dataSource;
	private ClusterGraphLoadSession loadSession;

	private EdgeCounter edgeCounter = new EdgeCounter();

	private File relocationDirectory;

	private Map<String, ModuleGraphCluster<ClusterNode<?>>> graphs = new HashMap<String, ModuleGraphCluster<ClusterNode<?>>>();

	private IndirectBranchAnalyzer(ArgumentStack args) {
		this.args = args;
		this.options = new CommonMergeOptions(args, CommonMergeOptions.crowdSafeCommonDir);
	}

	private void run() {
		try {
			options.parseOptions();
			options.initializeGraphEnvironment();

			Log.addOutput(System.out);

			while (args.size() > 0) {
				String path = args.pop();
				File directory = new File(path);
				if (!(directory.exists() && directory.isDirectory())) {
					Log.log("Illegal cluster graph directory '" + directory + "'; no such directory.");
					printUsageAndExit();
				}

				dataSource = new ClusterTraceDirectory(directory).loadExistingFiles();
				loadSession = new ClusterGraphLoadSession(dataSource);

				for (AutonomousSoftwareDistribution cluster : dataSource.getReprsentedClusters()) {
					ModuleGraphCluster<?> graph = loadSession.loadClusterGraph(cluster);
					edgeCounter.countEdges(graph.getGraphData().nodesByHash);
				}
			}

			edgeCounter.report();
		} catch (Throwable t) {
			t.printStackTrace(System.err);
		}
	}

	private void printUsageAndExit() {
		System.out.println(String.format("Usage: %s <cluster-data-dir> [ <cluster-data-dir> ... ]", getClass()
				.getSimpleName()));
		System.exit(1);
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		IndirectBranchAnalyzer analyzer = new IndirectBranchAnalyzer(stack);
		analyzer.run();
	}
}
