package edu.uci.eecs.crowdsafe.graph.io.modular;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianOutputStream;
import edu.uci.eecs.crowdsafe.common.io.TraceDataSourceException;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModuleSet;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleBoundaryNode;

public class ModularTraceDirectory implements ModularTraceDataSource, ModularTraceDataSink {

	private static final EnumSet<ModularTraceStreamType> ALL_STREAM_TYPES = EnumSet.allOf(ModularTraceStreamType.class);
	private static final EnumSet<ModularTraceStreamType> CFG_STREAM_TYPES = EnumSet.of(
			ModularTraceStreamType.GRAPH_NODE, ModularTraceStreamType.GRAPH_EDGE, ModularTraceStreamType.META);
	private static final String XHASH_FILENAME = "xhash.tab";

	private final Map<ApplicationModule, Map<ModularTraceStreamType, File>> filesByModule = new HashMap<ApplicationModule, Map<ModularTraceStreamType, File>>();

	private final File directory;
	private final Set<ModularTraceStreamType> streamTypes;
	private final Set<ModularTraceStreamType> requiredStreamTypes;
	private final Set<ModularTraceStreamType> optionalStreamTypes;

	private final Map<ModularTraceStreamType, Pattern> filePatterns = new EnumMap<ModularTraceStreamType, Pattern>(
			ModularTraceStreamType.class);

	public ModularTraceDirectory(File dir) throws TraceDataSourceException {
		this(dir, CFG_STREAM_TYPES);
	}

	public ModularTraceDirectory(File directory, Set<ModularTraceStreamType> requiredStreamTypes)
			throws TraceDataSourceException {
		this(directory, requiredStreamTypes, EnumSet.noneOf(ModularTraceStreamType.class));
	}

	public ModularTraceDirectory(File directory, Set<ModularTraceStreamType> requiredStreamTypes,
			Set<ModularTraceStreamType> optionalStreamTypes) throws TraceDataSourceException {
		this.directory = directory;
		streamTypes = EnumSet.noneOf(ModularTraceStreamType.class);
		streamTypes.addAll(requiredStreamTypes);
		streamTypes.addAll(optionalStreamTypes);
		this.requiredStreamTypes = requiredStreamTypes;
		this.optionalStreamTypes = optionalStreamTypes;
	}

	@Override
	public File getDirectory() {
		return directory;
	}

	public ModularTraceDirectory loadExistingFiles() throws IOException {
		File[] ls = directory.listFiles();

		if ((ls == null) || (ls.length == 0)) {
			throw new IllegalStateException(String.format("No files in run directory %s", directory.getAbsolutePath()));
		}

		String sample = ls[0].getName();
		if (sample.endsWith("transform-graphs.log") && (ls.length > 1))
			sample = ls[1].getName();
		String processName = sample.substring(0, sample.indexOf('.'));
		for (ModularTraceStreamType streamType : streamTypes) {
			filePatterns.put(streamType, Pattern.compile(String.format("%s\\.(.*)\\.%s\\.%s", processName,
					streamType.id, streamType.extension)));
		}

		for (File file : ls) {
			if (file.isDirectory())
				continue;

			if (file.getName().equals(XHASH_FILENAME)) {
				BufferedReader reader = new BufferedReader(new FileReader(file));
				try {
					for (String line = reader.readLine(); line != null; line = reader.readLine()) {
						ModuleBoundaryNode.HashLabel label = new ModuleBoundaryNode.HashLabel(line);
						ApplicationModuleSet.getInstance().crossModuleLabels.put(label.hash, label);
					}
				} finally {
					reader.close();
				}
				continue;
			}

			for (ModularTraceStreamType streamType : streamTypes) {
				Matcher matcher = filePatterns.get(streamType).matcher(file.getName());
				if (matcher.matches()) {
					ApplicationModule module = ApplicationModuleSet.getInstance().establishModuleByFileSystemName(
							matcher.group(1));
					Map<ModularTraceStreamType, File> files = filesByModule.get(module);
					if (files == null) {
						files = new EnumMap<ModularTraceStreamType, File>(ModularTraceStreamType.class);
						filesByModule.put(module, files);
					}
					if (files.containsKey(streamType))
						throw new TraceDataSourceException(String.format(
								"Directory %s contains multiple files of type %s for cluster %s: %s and %s",
								directory.getAbsolutePath(), streamType, module.name, file.getName(),
								files.get(streamType).getName()));
					files.put(streamType, file);
				}
			}
		}

		for (Map.Entry<ApplicationModule, Map<ModularTraceStreamType, File>> files : new ArrayList<Map.Entry<ApplicationModule, Map<ModularTraceStreamType, File>>>(
				filesByModule.entrySet())) {
			for (ModularTraceStreamType streamType : requiredStreamTypes) {
				if (!files.getValue().containsKey(streamType)) {
					Set<ModularTraceStreamType> requiredTypes = EnumSet.copyOf(requiredStreamTypes);
					requiredTypes.removeAll(files.getValue().keySet());
					Log.log("Error! Directory %s contains some but not all files for cluster %s.\n\tMissing types are %s.\n\tSkipping this cluster.",
							directory.getAbsolutePath(), files.getKey().name, requiredTypes);
					filesByModule.remove(files.getKey());
				}
			}
		}

		return this;
	}

	@Override
	public String parseTraceName() {
		if (filesByModule.isEmpty())
			throw new IllegalStateException("Directory has no files from which to parse a name.");

		Map.Entry<ApplicationModule, Map<ModularTraceStreamType, File>> clusterEntry = filesByModule.entrySet()
				.iterator().next();
		Map.Entry<ModularTraceStreamType, File> fileEntry = clusterEntry.getValue().entrySet().iterator().next();
		Pattern pattern = Pattern.compile(String.format("(.*)\\.%s.*", clusterEntry.getKey().id));
		Matcher matcher = pattern.matcher(fileEntry.getValue().getName());
		if (!matcher.matches())
			throw new IllegalStateException("Failed to parse the trace name from file "
					+ fileEntry.getValue().getAbsolutePath());
		return matcher.group(1);
	}

	@Override
	public void addModule(ApplicationModule module, String filenameFormat) {
		Map<ModularTraceStreamType, File> files = new EnumMap<ModularTraceStreamType, File>(
				ModularTraceStreamType.class);
		for (ModularTraceStreamType streamType : streamTypes) {
			files.put(streamType,
					new File(directory, String.format(filenameFormat, module.id, streamType.id, streamType.extension)));
		}
		filesByModule.put(module, files);
	}

	@Override
	public Collection<ApplicationModule> getReprsentedModules() {
		return Collections.unmodifiableSet(filesByModule.keySet());
	}

	@Override
	public boolean hasDataInputStream(ApplicationModule module, ModularTraceStreamType streamType) {
		Map<ModularTraceStreamType, File> files = filesByModule.get(module);
		if (files == null)
			return false;
		else
			return files.containsKey(streamType);
	}

	@Override
	public InputStream getDataInputStream(ApplicationModule module, ModularTraceStreamType streamType)
			throws IOException {
		if (!filesByModule.containsKey(module))
			return null;

		return new FileInputStream(filesByModule.get(module).get(streamType));
	}

	@Override
	public LittleEndianInputStream getLittleEndianInputStream(ApplicationModule module,
			ModularTraceStreamType streamType) throws IOException {
		if (!filesByModule.containsKey(module))
			return null;

		File file = filesByModule.get(module).get(streamType);
		return new LittleEndianInputStream(new FileInputStream(file), "file:" + file.getAbsolutePath());
	}

	@Override
	public OutputStream getDataOutputStream(ApplicationModule module, ModularTraceStreamType streamType)
			throws IOException {
		return new FileOutputStream(filesByModule.get(module).get(streamType));
	}

	@Override
	public LittleEndianOutputStream getLittleEndianOutputStream(ApplicationModule module,
			ModularTraceStreamType streamType) throws IOException {
		File file = filesByModule.get(module).get(streamType);
		return new LittleEndianOutputStream(new FileOutputStream(file), "file:" + file.getAbsolutePath());
	}

	@Override
	public Path getHashLabelPath() throws IOException {
		return Paths.get(new File(directory, XHASH_FILENAME).getAbsolutePath());
	}

	private boolean matches(String filename, ApplicationModule module, ModularTraceStreamType streamType) {
		return matches(filename, ".*", module, streamType);
	}

	private boolean matches(String filename, String processName, ApplicationModule module,
			ModularTraceStreamType streamType) {
		return Pattern.matches(
				String.format("%s\\.%s\\.%s\\.%s", processName, module.id, streamType.id, streamType.extension),
				filename);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + ": " + directory.getAbsolutePath();
	}
}
