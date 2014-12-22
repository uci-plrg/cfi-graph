package edu.uci.eecs.crowdsafe.graph.main;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap.OptionMode;
import edu.uci.eecs.crowdsafe.graph.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.loader.ClusterGraphLoadSession;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterMetadataExecution;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterMetadataSequence;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterUIB;
import edu.uci.eecs.crowdsafe.graph.io.cluster.ClusterTraceDataSource;
import edu.uci.eecs.crowdsafe.graph.io.cluster.ClusterTraceDirectory;

public class RelocationAnalyzer {

	private class ModuleRelocations {
		private Set<Long> relocatableTargets = new HashSet<Long>();

		public ModuleRelocations(File relocationFile) throws IOException {
			LittleEndianInputStream in = new LittleEndianInputStream(new FileInputStream(relocationFile), "Relocations");
			while (in.ready()) {
				Long relocatableTarget = (long) in.readInt();
				relocatableTargets.add(relocatableTarget);
				//System.out.println(String.format("\t0x%x", relocatableTarget));
			}
		}
	}

	private static final OptionArgumentMap.StringOption relocationOption = OptionArgumentMap.createStringOption('r',
			OptionMode.REQUIRED);

	private final ArgumentStack args;
	private final CommonMergeOptions options;

	private ClusterTraceDataSource dataSource;
	private ClusterGraphLoadSession loadSession;

	private File relocationDirectory;

	private RelocationAnalyzer(ArgumentStack args) {
		this.args = args;
		this.options = new CommonMergeOptions(args, CommonMergeOptions.crowdSafeCommonDir, relocationOption);
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

			List<ModuleGraphCluster<?>> graphs = new ArrayList<ModuleGraphCluster<?>>();

			for (AutonomousSoftwareDistribution cluster : dataSource.getReprsentedClusters()) {
				ModuleGraphCluster<?> graph = loadSession.loadClusterGraph(cluster);
				if (graph.metadata.sequences.size() > 0) {
					if (graph.metadata.sequences.size() == 1) {
						graphs.add(graph);
					} else {
						throw new IllegalArgumentException("Error--multiple metadata sequences in module " + graph.name);
					}
				}
			}

			System.out.println("----");

			for (ModuleGraphCluster<?> graph : graphs) {
				String moduleName = graph.cluster.getUnitFilename();
				ModuleRelocations relocations = null;

				File relocationFile = new File(relocationDirectory, moduleName + ".relocations.dat");
				if (!relocationFile.exists()) {
					System.err.println("Warning: no relocations for module " + moduleName);
				} else {
					relocations = new ModuleRelocations(relocationFile);
					System.out.println("Found " + relocations.relocatableTargets.size()
							+ " relocatable targets for module " + moduleName);
				}

				ClusterMetadataSequence sequence = graph.metadata.sequences.values().iterator().next();
				System.out.println("Loaded graph " + graph.name + " with " + sequence.executions.size()
						+ " metadata executions");
				if (relocations != null) {
					int relocatableTargets = 0;
					for (ClusterMetadataExecution execution : sequence.executions) {
						for (ClusterUIB uib : execution.uibs) {
							if (!uib.edge.getToNode().isMetaNode() && !uib.isAdmitted) {
								boolean isRelocatableTarget = relocations.relocatableTargets.contains(uib.edge
										.getToNode().getRelativeTag());
								if (isRelocatableTarget)
									System.out.println("SUIB: " + uib.edge);
								else
									relocatableTargets++;
							}
						}
					}
					System.out.println("Cleared suspicion for " + relocatableTargets + " relocatable targets in "
							+ moduleName);
				}
			}

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
