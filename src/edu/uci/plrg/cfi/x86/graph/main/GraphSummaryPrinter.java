package edu.uci.plrg.cfi.x86.graph.main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.common.util.ArgumentStack;
import edu.uci.plrg.cfi.common.util.OptionArgumentMap;
import edu.uci.plrg.cfi.common.util.OptionArgumentMap.OptionMode;
import edu.uci.plrg.cfi.x86.graph.data.DataMessageType;
import edu.uci.plrg.cfi.x86.graph.data.application.ApplicationModule;
import edu.uci.plrg.cfi.x86.graph.data.graph.ModuleGraph;
import edu.uci.plrg.cfi.x86.graph.data.graph.execution.ProcessExecutionGraph;
import edu.uci.plrg.cfi.x86.graph.data.graph.execution.loader.ProcessGraphLoadSession;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.loader.ModuleGraphLoadSession;
import edu.uci.plrg.cfi.x86.graph.data.results.Graph;
import edu.uci.plrg.cfi.x86.graph.io.execution.ExecutionTraceDataSource;
import edu.uci.plrg.cfi.x86.graph.io.execution.ExecutionTraceDirectory;
import edu.uci.plrg.cfi.x86.graph.io.modular.ModularTraceDataSource;
import edu.uci.plrg.cfi.x86.graph.io.modular.ModularTraceDirectory;

public class GraphSummaryPrinter {

	private static final OptionArgumentMap.StringOption outputOption = OptionArgumentMap.createStringOption('o',
			OptionMode.REQUIRED);

	private final ArgumentStack args;
	private final CommonMergeOptions options;

	private GraphSummaryPrinter(ArgumentStack args) {
		this.args = args;
		this.options = new CommonMergeOptions(args, CommonMergeOptions.crowdSafeCommonDir,
				CommonMergeOptions.restrictedModuleOption, CommonMergeOptions.unitModuleOption,
				CommonMergeOptions.excludeModuleOption, outputOption);
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

			File outputFile = new File(outputOption.getValue());
			if (outputFile.getParentFile() == null)
				outputFile = new File(new File("."), outputOption.getValue());
			if (!outputFile.getParentFile().exists()) {
				Log.log("Illegal argument '" + outputFile.getParentFile().getAbsolutePath() + "'; no such directory.");
				printUsageAndExit();
			}

			Graph.Process process = null;
			switch (path.charAt(0)) {
				case 'c':
					process = summarizeModularGraph(directory);
					break;
				case 'e':
					process = summarizeExecutionGraph(directory);
					break;
			}
			FileOutputStream out = new FileOutputStream(outputFile);
			out.write(DataMessageType.PROCESS_GRAPH.id);
			process.writeTo(out);
			out.flush();
			out.close();
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private Graph.Process summarizeModularGraph(File directory) throws IOException {
		Graph.Process.Builder processBuilder = Graph.Process.newBuilder();
		processBuilder.setName(directory.getName());

		ModuleGraph<?> mainGraph = null;

		ModularTraceDataSource dataSource = new ModularTraceDirectory(directory).loadExistingFiles();
		ModuleGraphLoadSession loadSession = new ModuleGraphLoadSession(dataSource);
		for (ApplicationModule module : dataSource.getReprsentedModules()) {
			ModuleGraph<?> graph = loadSession.loadModuleGraph(module);
			processBuilder.addModule(graph.summarize(module.isAnonymous));

			if (graph.metadata.isMain())
				mainGraph = graph;
		}

		if (mainGraph != null) {
			processBuilder.setMetadata(mainGraph.metadata.summarizeProcess());
		}

		return processBuilder.build();
	}

	private Graph.Process summarizeExecutionGraph(File directory) throws IOException {
		ExecutionTraceDataSource dataSource = new ExecutionTraceDirectory(directory,
				ProcessExecutionGraph.EXECUTION_GRAPH_FILE_TYPES,
				ProcessExecutionGraph.EXECUTION_GRAPH_REQUIRED_FILE_TYPES);
		ProcessGraphLoadSession loadSession = new ProcessGraphLoadSession();
		ProcessExecutionGraph graph = loadSession.loadGraph(dataSource, null);
		return graph.summarizeProcess();
	}

	private void printUsageAndExit() {
		System.out.println(String.format("Usage: %s -o <output-file> {c: | e:}<run-dir>", getClass().getSimpleName()));
		System.exit(1);
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		GraphSummaryPrinter printer = new GraphSummaryPrinter(stack);
		printer.run();
	}
}
