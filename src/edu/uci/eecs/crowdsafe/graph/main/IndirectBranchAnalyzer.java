package edu.uci.eecs.crowdsafe.graph.main;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap.OptionMode;
import edu.uci.eecs.crowdsafe.graph.data.ModuleRelocations;
import edu.uci.eecs.crowdsafe.graph.data.dist.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.Node;
import edu.uci.eecs.crowdsafe.graph.data.graph.NodeHashMap;
import edu.uci.eecs.crowdsafe.graph.data.graph.NodeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ModuleNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.loader.ModuleGraphLoadSession;
import edu.uci.eecs.crowdsafe.graph.io.cluster.ClusterTraceDataSource;
import edu.uci.eecs.crowdsafe.graph.io.cluster.ClusterTraceDirectory;

public class IndirectBranchAnalyzer {

	private class EdgeCounter {

		private class SingletonTargets {
			long relocatable = 0;
			long crossModule = 0;
			long reloctableIntraModule = 0;
		}

		private long returnCount = 0;
		private long indirectBranchCount = 0;
		private long targetCount = 0;
		private long degree1BranchCount = 0;
		private long degree1TargetCount = 0;
		private long degree2BranchCount = 0;
		private long degree2TargetCount = 0;
		private long degree3BranchCount = 0;
		private long degree3TargetCount = 0;
		private long degree5BranchCount = 0;
		private long degree5TargetCount = 0;
		private long degree10BranchCount = 0;
		private long degree10TargetCount = 0;
		private final SingletonTargets singletonTargets = new SingletonTargets();

		private void countEdges(NodeHashMap<?> nodeMap, ModuleRelocations relocations) {
			for (Long hash : nodeMap.keySet()) {
				NodeList<?> nodes = nodeMap.get(hash);
				for (int i = 0; i < nodes.size(); i++) {
					Node<?> node = nodes.get(i);
					if (node.getType() == MetaNodeType.RETURN) {
						returnCount++;
					} else {
						for (int j = 0; j < node.getOutgoingOrdinalCount(); j++) {
							if (node.getOrdinalEdgeType(j) == EdgeType.INDIRECT) {
								OrdinalEdgeList<?> outgoing = node.getOutgoingEdges(j);
								int count = outgoing.size();
								if (count == 1) {
									Edge<?> edge = outgoing.get(0);
									boolean relocatable = relocations != null
											&& relocations.containsTag(edge.getToNode().getRelativeTag());
									if (relocatable) {
										singletonTargets.relocatable++;
									}
									if (edge.getFromNode().getModule() != edge.getToNode().getModule())
										singletonTargets.crossModule++;
									else if (relocatable)
										singletonTargets.reloctableIntraModule++;
								}
								outgoing.release();

								indirectBranchCount++;
								targetCount += count;
								if (count == 1) {
									degree1BranchCount++;
									degree1TargetCount += count;
								}
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
			Log.log("branch-stats: Total branchpoints: " + indirectBranchCount);
			Log.log("branch-stats: Total returns: " + returnCount);
			Log.log("branch-stats: Return ratio: %.2f", percent(returnCount, indirectBranchCount + returnCount));

			Log.log("branch-stats: Total branch targets: " + targetCount);
			Log.log("branch-stats: Mean targets per branchpoint: %.2f", percent(targetCount, indirectBranchCount));

			Log.log("branch-stats: Total branchpoints (degree 1): " + degree1BranchCount);
			Log.log("branch-stats: Total branchpoints (degree 2): " + degree2BranchCount);
			Log.log("branch-stats: Total branchpoints (degree 3): " + degree3BranchCount);
			Log.log("branch-stats: Total branchpoints (degree 5): " + degree5BranchCount);
			Log.log("branch-stats: Total branchpoints (degree 10): " + degree10BranchCount);
			Log.log("branch-stats: Percent having degree 2 or more: %.2f",
					percent(degree2BranchCount, indirectBranchCount));
			Log.log("branch-stats: Percent having degree 3 or more: %.2f",
					percent(degree3BranchCount, indirectBranchCount));
			Log.log("branch-stats: Percent having degree 5 or more: %.2f",
					percent(degree5BranchCount, indirectBranchCount));
			Log.log("branch-stats: Percent having degree 10 or more: %.2f",
					percent(degree10BranchCount, indirectBranchCount));
			Log.log("");
			Log.log("Singleton indirect branches: %.2f", percent(degree1BranchCount, indirectBranchCount));
			Log.log("\tRelocatable targets: %.2f", percent(singletonTargets.relocatable, degree1BranchCount));
			Log.log("\tCross module targets: %.2f", percent(singletonTargets.crossModule, degree1BranchCount));
			Log.log("\tRelocatable intra module targets: %.2f",
					percent(singletonTargets.reloctableIntraModule, degree1BranchCount));
		}

		private double percent(long n, long d) {
			return (n / (double) d) * 100d;
		}
	}

	private final ArgumentStack args;
	private final CommonMergeOptions options;

	private static final OptionArgumentMap.StringOption relocationOption = OptionArgumentMap.createStringOption('r',
			OptionMode.REQUIRED);

	private File relocationDirectory;

	private ClusterTraceDataSource dataSource;
	private ModuleGraphLoadSession loadSession;

	private EdgeCounter edgeCounter = new EdgeCounter();

	private Map<String, ModuleGraph<ModuleNode<?>>> graphs = new HashMap<String, ModuleGraph<ModuleNode<?>>>();
	private Map<String, ModuleRelocations> moduleRelocations;

	private IndirectBranchAnalyzer(ArgumentStack args) {
		this.args = args;
		this.options = new CommonMergeOptions(args, CommonMergeOptions.crowdSafeCommonDir, relocationOption);
	}

	private void run() {
		try {
			options.parseOptions();
			options.initializeGraphEnvironment();

			Log.addOutput(System.out);

			relocationDirectory = new File(relocationOption.getValue());
			moduleRelocations = ModuleRelocations.loadAllRelocations(relocationDirectory);

			while (args.size() > 0) {
				String path = args.pop();
				File directory = new File(path);
				if (!(directory.exists() && directory.isDirectory())) {
					Log.log("Illegal cluster graph directory '" + directory + "'; no such directory.");
					printUsageAndExit();
				}

				dataSource = new ClusterTraceDirectory(directory).loadExistingFiles();
				loadSession = new ModuleGraphLoadSession(dataSource);

				for (ApplicationModule cluster : dataSource.getReprsentedModules()) {
					Log.setSilent(true);
					ModuleGraph<?> graph = loadSession.loadClusterGraph(cluster);
					Log.setSilent(false);
					edgeCounter.countEdges(graph.getGraphData().nodesByHash,
							moduleRelocations.get(graph.module.filename));
				}
			}

			edgeCounter.report();
		} catch (Throwable t) {
			t.printStackTrace(System.err);
		}
	}

	private void printUsageAndExit() {
		System.out.println(String.format("Usage: %s -r <relocation-dir> <cluster-data-dir> [ <cluster-data-dir> ... ]",
				getClass().getSimpleName()));
		System.exit(1);
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		IndirectBranchAnalyzer analyzer = new IndirectBranchAnalyzer(stack);
		analyzer.run();
	}
}
