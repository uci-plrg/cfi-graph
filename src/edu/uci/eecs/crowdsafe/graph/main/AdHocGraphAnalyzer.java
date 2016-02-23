package edu.uci.eecs.crowdsafe.graph.main;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.graph.data.dist.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.dist.ApplicationModuleSet;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.GraphLoadEventListener;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.Node;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ModuleBoundaryNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ModuleNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.loader.ModuleGraphLoadSession;
import edu.uci.eecs.crowdsafe.graph.data.graph.execution.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.execution.loader.ProcessGraphLoadSession;
import edu.uci.eecs.crowdsafe.graph.io.cluster.ClusterTraceDataSource;
import edu.uci.eecs.crowdsafe.graph.io.cluster.ClusterTraceDirectory;
import edu.uci.eecs.crowdsafe.graph.io.execution.ExecutionTraceDataSource;
import edu.uci.eecs.crowdsafe.graph.io.execution.ExecutionTraceDirectory;
import edu.uci.eecs.crowdsafe.graph.util.CrowdSafeTraceUtil;

public class AdHocGraphAnalyzer {

	class LoadListener implements GraphLoadEventListener {
		final Map<Long, Integer> syscallNumbersByExportHash = new HashMap<Long, Integer>();
		final Set<ApplicationModule> blackBoxLinkedModules = new HashSet<ApplicationModule>();
		final Set<ApplicationModule> whiteBoxLinkedModules = new HashSet<ApplicationModule>();

		public LoadListener() {
			for (int i = 0; i < 4000; i++) {
				syscallNumbersByExportHash.put(CrowdSafeTraceUtil.stringHash(String.format("syscall#%d", i + 1000)), i);
			}
		}

		@Override
		public void edgeCreation(Edge<?> edge) {
			if ((edge.getFromNode() instanceof ModuleBoundaryNode) && edge.getToNode().getModule().isAnonymous) {
				ModuleBoundaryNode.HashLabel entryLabel = ApplicationModuleSet.getInstance().crossModuleLabels.get(edge
						.getFromNode().getHash());
				ApplicationModule module = ApplicationModuleSet.getInstance().modulesByName
						.get(entryLabel.toModuleName);
				if (((ModuleNode) edge.getToNode()).isBlackBoxSingleton())
					blackBoxLinkedModules.add(module);
				else
					whiteBoxLinkedModules.add(module);
			} else if ((edge.getToNode() instanceof ModuleBoundaryNode) && edge.getFromNode().getModule().isAnonymous) {
				ModuleBoundaryNode.HashLabel exitLabel = ApplicationModuleSet.getInstance().crossModuleLabels.get(edge
						.getToNode().getHash());
				ApplicationModule module = ApplicationModuleSet.getInstance().modulesByName
						.get(exitLabel.fromModuleName);
				if (((ModuleNode) edge.getFromNode()).isBlackBoxSingleton())
					blackBoxLinkedModules.add(module);
				else
					whiteBoxLinkedModules.add(module);
			}

			/**
			 * <pre>
			if (edge.getToNode().getType() == MetaNodeType.SINGLETON) {
				// Log.log("Singleton %s has incoming edge %s", edge.getToNode(), edge);

//				Integer sysnum = syscallNumbersByExportHash.get(edge.getToNode().getHash());
//				if (sysnum != null) {
//					Log.log("Cluster exit %s calls sysnum %d", edge, sysnum);
					// } else {
					// Log.log("Cluster exit %s calls no sysnums", edge);
				}
			} else if (edge.getEdgeType() == EdgeType.UNEXPECTED_RETURN) {
				Log.log("Unexpected return %s", edge);

				OrdinalEdgeList<?> edges = edge.getToNode().getIncomingEdges();
				if (!edges.isEmpty()) {
					Log.log("\tIncoming edges for 'to' node %s (%d)", edge.getToNode(), edges.size());
					try {
						listEdges(edges);
					} finally {
						edges.release();
					}
				}

				edges = edge.getToNode().getOutgoingEdges();
				if (!edges.isEmpty()) {
					Log.log("\tOutgoing edges for 'to' node %s (%d)", edge.getToNode(), edges.size());
					try {
						listEdges(edges);
					} finally {
						edges.release();
					}
				}

				edges = edge.getFromNode().getIncomingEdges();
				if (!edges.isEmpty()) {
					Log.log("\tIncoming edges for 'from' node %s (%d)", edge.getFromNode(), edges.size());
					try {
						listEdges(edges);
					} finally {
						edges.release();
					}
				}

				edges = edge.getFromNode().getOutgoingEdges();
				if (!edges.isEmpty()) {
					Log.log("\tOutgoing edges for 'from' node %s (%d)", edge.getFromNode(), edges.size());
					try {
						listEdges(edges);
					} finally {
						edges.release();
					}
				}
			}
			 */
			/**
			 * <pre>
			else if ((edge.getToNode().getType() == MetaNodeType.CLUSTER_EXIT)
					&& (edge.getToNode().getHash() == 0xcfe19f4f90723b02L))
				Log.log("Cluster exit to syscall: %s", edge);
			 */
		}

		private void listEdges(OrdinalEdgeList<?> edges) {
			for (Edge<?> edge : edges) {
				Log.log("\t\t%s", edge);
			}
		}

		@Override
		public void nodeLoadReference(long tag, long hash, LoadTarget target) {
		}

		@Override
		public void nodeLoadReference(Node<?> node, LoadTarget target) {
		}

		@Override
		public void nodeCreation(Node<?> node) {
			// Log.log("Loaded node %s", node);
		}

		@Override
		public void graphAddition(Node<?> node, ModuleGraph<?> cluster) {
		}
	}

	private final ArgumentStack args;
	private final CommonMergeOptions options;

	private AdHocGraphAnalyzer(ArgumentStack args) {
		this.args = args;
		this.options = new CommonMergeOptions(args, CommonMergeOptions.crowdSafeCommonDir,
				CommonMergeOptions.restrictedClusterOption, CommonMergeOptions.unitClusterOption,
				CommonMergeOptions.excludeClusterOption);
	}

	private void run() {
		try {
			Log.addOutput(System.out);
			options.parseOptions();
			options.initializeGraphEnvironment();

			String path = args.pop();
			File directory = new File(path.substring(path.indexOf(':') + 1));
			if (!(directory.exists() && directory.isDirectory())) {
				Log.log("Illegal argument '" + directory + "'; no such directory.");
				printUsageAndExit();
			}

			switch (path.charAt(0)) {
				case 'c':
					analyzeClusterGraph(directory);
					break;
				case 'e':
					analyzeExecutionGraph(directory);
					break;
				default:
					Log.log("Error! No graph type specified. Exiting now.");
					printUsageAndExit();
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private void analyzeClusterGraph(File directory) throws IOException {
		Log.log("Ad hoc graph analysis of directory '%s'", directory.getName());

		ModuleGraph<?> mainGraph = null;

		ClusterTraceDataSource dataSource = new ClusterTraceDirectory(directory).loadExistingFiles();
		ModuleGraphLoadSession loadSession = new ModuleGraphLoadSession(dataSource);
		LoadListener listener = new LoadListener();
		for (ApplicationModule module : dataSource.getReprsentedModules()) {
			if (options.includeModule(module)) {
				ModuleGraph<?> graph = loadSession.loadModuleGraph(module, listener);

				if (graph.metadata.isMain())
					mainGraph = graph;
			}
		}

		Log.log("Total white box linked modules: " + listener.whiteBoxLinkedModules.size());
		Log.log("Total black box linked modules: " + listener.blackBoxLinkedModules.size());
	}

	private void analyzeExecutionGraph(File directory) throws IOException {
		Log.log("Ad hoc graph analysis of directory '%s'", directory.getName());

		ExecutionTraceDataSource dataSource = new ExecutionTraceDirectory(directory,
				ProcessExecutionGraph.EXECUTION_GRAPH_FILE_TYPES,
				ProcessExecutionGraph.EXECUTION_GRAPH_REQUIRED_FILE_TYPES);
		ProcessGraphLoadSession loadSession = new ProcessGraphLoadSession();
		ProcessExecutionGraph graph = loadSession.loadGraph(dataSource, null);
	}

	private void printUsageAndExit() {
		System.out.println(String.format("Usage: %s {c: | e:}<run-dir>", getClass().getSimpleName()));
		System.exit(1);
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		AdHocGraphAnalyzer printer = new AdHocGraphAnalyzer(stack);
		printer.run();
	}
}
