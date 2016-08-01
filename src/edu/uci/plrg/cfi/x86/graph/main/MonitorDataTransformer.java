package edu.uci.plrg.cfi.x86.graph.main;

import java.io.File;

import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.common.util.ArgumentStack;
import edu.uci.plrg.cfi.common.util.OptionArgumentMap;
import edu.uci.plrg.cfi.common.util.OptionArgumentMap.OptionMode;
import edu.uci.plrg.cfi.x86.graph.data.monitor.MonitorDatasetGenerator;

public class MonitorDataTransformer {

	private static final OptionArgumentMap.BooleanOption verboseOption = OptionArgumentMap.createBooleanOption('v');
	private static final OptionArgumentMap.StringOption logOption = OptionArgumentMap.createStringOption('l');
	private static final OptionArgumentMap.StringOption outputOption = OptionArgumentMap.createStringOption('o',
			OptionMode.REQUIRED);
	private static final OptionArgumentMap.StringOption alarmConfigOption = OptionArgumentMap.createStringOption('a');

	private final ArgumentStack args;
	private final CommonMergeOptions options;

	private MonitorDataTransformer(ArgumentStack args) {
		this.args = args;
		this.options = new CommonMergeOptions(args, CommonMergeOptions.crowdSafeCommonDir,
				CommonMergeOptions.restrictedModuleOption, CommonMergeOptions.unitModuleOption,
				CommonMergeOptions.excludeModuleOption, verboseOption, logOption, outputOption, alarmConfigOption);
	}

	private void run() {
		try {
			options.parseOptions();
			options.initializeGraphEnvironment();

			if (verboseOption.getValue() || (logOption.getValue() == null)) {
				Log.addOutput(System.out);
			}
			if (logOption.getValue() != null) {
				Log.addOutput(new File(logOption.getValue()));
			}

			String path = args.pop();
			File directory = new File(path);
			if (!(directory.exists() && directory.isDirectory())) {
				Log.log("Illegal cluster graph directory '" + directory + "'; no such directory.");
				printUsageAndExit();
			}

			File outputFile = new File(outputOption.getValue());
			if (outputFile.getParentFile() == null)
				outputFile = new File(new File("."), outputOption.getValue());
			if (!outputFile.getParentFile().exists()) {
				Log.log("Illegal output file '" + outputOption.getValue() + "'; parent directory does not exist.");
				System.out.println("Parent directory does not exist: " + outputOption.getValue());
				printUsageAndExit();
			}

			File alarmConfigFile = null;
			if (alarmConfigOption.hasValue()) {
				alarmConfigFile = new File(alarmConfigOption.getValue());
			}

			MonitorDatasetGenerator generator = new MonitorDatasetGenerator(directory, outputFile, alarmConfigFile);
			generator.generateDataset();
		} catch (Throwable t) {
			t.printStackTrace(System.err);
		}
	}

	private void printUsageAndExit() {
		System.out.println(String.format("Usage: %s -o <output-file> <cluster-data-dir>", getClass().getSimpleName()));
		System.exit(1);
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		MonitorDataTransformer printer = new MonitorDataTransformer(stack);
		printer.run();
	}
}
