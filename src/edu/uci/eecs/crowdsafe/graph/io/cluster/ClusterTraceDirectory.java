package edu.uci.eecs.crowdsafe.graph.io.cluster;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import edu.uci.eecs.crowdsafe.graph.data.dist.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.dist.ApplicationModuleSet;
import edu.uci.eecs.crowdsafe.graph.data.dist.ApplicationModule;

public class ClusterTraceDirectory implements ClusterTraceDataSource, ClusterTraceDataSink {

	private static final EnumSet<ClusterTraceStreamType> ALL_STREAM_TYPES = EnumSet.allOf(ClusterTraceStreamType.class);

	private final Map<ApplicationModule, Map<ClusterTraceStreamType, File>> filesByModule = new HashMap<ApplicationModule, Map<ClusterTraceStreamType, File>>();

	private final File directory;
	private final Set<ClusterTraceStreamType> streamTypes;

	private final Map<ClusterTraceStreamType, Pattern> filePatterns = new EnumMap<ClusterTraceStreamType, Pattern>(
			ClusterTraceStreamType.class);

	public ClusterTraceDirectory(File dir) throws TraceDataSourceException {
		this(dir, ALL_STREAM_TYPES);
	}

	public ClusterTraceDirectory(File directory, Set<ClusterTraceStreamType> streamTypes)
			throws TraceDataSourceException {
		this.directory = directory;
		this.streamTypes = streamTypes;
	}

	@Override
	public File getDirectory() {
		return directory;
	}

	public ClusterTraceDirectory loadExistingFiles() {
		File[] ls = directory.listFiles();

		if ((ls == null) || (ls.length == 0)) {
			throw new IllegalStateException(String.format("No files in run directory %s", directory.getAbsolutePath()));
		}

		String sample = ls[0].getName();
		if (sample.endsWith("transform-graphs.log") && (ls.length > 1))
			sample = ls[1].getName();
		String processName = sample.substring(0, sample.indexOf('.'));
		for (ClusterTraceStreamType streamType : streamTypes) {
			filePatterns.put(streamType, Pattern.compile(String.format("%s\\.(.*)\\.%s\\.%s", processName,
					streamType.id, streamType.extension)));
		}

		for (File file : ls) {
			if (file.isDirectory())
				continue;

			for (ClusterTraceStreamType streamType : streamTypes) {
				Matcher matcher = filePatterns.get(streamType).matcher(file.getName());
				if (matcher.matches()) {
					ApplicationModule unit = ApplicationModuleSet.getInstance().establishUnitByFileSystemName(
							matcher.group(1));
					ApplicationModule cluster = ApplicationModuleSet.getInstance().distributionsByUnit
							.get(unit);
					Map<ClusterTraceStreamType, File> files = filesByModule.get(cluster);
					if (files == null) {
						files = new EnumMap<ClusterTraceStreamType, File>(ClusterTraceStreamType.class);
						filesByModule.put(cluster, files);
					}
					if (files.containsKey(streamType))
						throw new TraceDataSourceException(String.format(
								"Directory %s contains multiple files of type %s for cluster %s: %s and %s",
								directory.getAbsolutePath(), streamType, cluster.name, file.getName(),
								files.get(streamType).getName()));
					files.put(streamType, file);
				}
			}
		}

		for (Map.Entry<ApplicationModule, Map<ClusterTraceStreamType, File>> files : new ArrayList<Map.Entry<ApplicationModule, Map<ClusterTraceStreamType, File>>>(
				filesByModule.entrySet())) {
			if (files.getValue().size() < ALL_STREAM_TYPES.size()) {
				Set<ClusterTraceStreamType> requiredTypes = EnumSet.copyOf(streamTypes);
				requiredTypes.removeAll(files.getValue().keySet());
				Log.log("Error! Directory %s contains some but not all files for cluster %s.\n\tMissing types are %s.\n\tSkipping this cluster.",
						directory.getAbsolutePath(), files.getKey().name, requiredTypes);
				filesByModule.remove(files.getKey());
			}
		}

		return this;
	}

	@Override
	public String parseTraceName() {
		if (filesByModule.isEmpty())
			throw new IllegalStateException("Directory has no files from which to parse a name.");

		Map.Entry<ApplicationModule, Map<ClusterTraceStreamType, File>> clusterEntry = filesByModule
				.entrySet().iterator().next();
		Map.Entry<ClusterTraceStreamType, File> fileEntry = clusterEntry.getValue().entrySet().iterator().next();
		Pattern pattern = Pattern.compile(String.format("(.*)\\.%s.*", clusterEntry.getKey().id));
		Matcher matcher = pattern.matcher(fileEntry.getValue().getName());
		if (!matcher.matches())
			throw new IllegalStateException("Failed to parse the trace name from file "
					+ fileEntry.getValue().getAbsolutePath());
		return matcher.group(1);
	}

	@Override
	public void addCluster(ApplicationModule cluster, String filenameFormat) {
		Map<ClusterTraceStreamType, File> files = new EnumMap<ClusterTraceStreamType, File>(
				ClusterTraceStreamType.class);
		for (ClusterTraceStreamType streamType : streamTypes) {
			files.put(streamType,
					new File(directory, String.format(filenameFormat, cluster.id, streamType.id, streamType.extension)));
		}
		filesByModule.put(cluster, files);
	}

	@Override
	public Collection<ApplicationModule> getReprsentedModules() {
		return Collections.unmodifiableSet(filesByModule.keySet());
	}

	@Override
	public InputStream getDataInputStream(ApplicationModule cluster, ClusterTraceStreamType streamType)
			throws IOException {
		if (!filesByModule.containsKey(cluster))
			return null;

		return new FileInputStream(filesByModule.get(cluster).get(streamType));
	}

	@Override
	public LittleEndianInputStream getLittleEndianInputStream(ApplicationModule cluster,
			ClusterTraceStreamType streamType) throws IOException {
		if (!filesByModule.containsKey(cluster))
			return null;

		File file = filesByModule.get(cluster).get(streamType);
		return new LittleEndianInputStream(new FileInputStream(file), "file:" + file.getAbsolutePath());
	}

	@Override
	public OutputStream getDataOutputStream(ApplicationModule cluster, ClusterTraceStreamType streamType)
			throws IOException {
		return new FileOutputStream(filesByModule.get(cluster).get(streamType));
	}

	@Override
	public LittleEndianOutputStream getLittleEndianOutputStream(ApplicationModule cluster,
			ClusterTraceStreamType streamType) throws IOException {
		File file = filesByModule.get(cluster).get(streamType);
		return new LittleEndianOutputStream(new FileOutputStream(file), "file:" + file.getAbsolutePath());
	}

	private boolean matches(String filename, ApplicationModule cluster, ClusterTraceStreamType streamType) {
		return matches(filename, ".*", cluster, streamType);
	}

	private boolean matches(String filename, String processName, ApplicationModule cluster,
			ClusterTraceStreamType streamType) {
		return Pattern.matches(
				String.format("%s\\.%s\\.%s\\.%s", processName, cluster.id, streamType.id, streamType.extension),
				filename);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + ": " + directory.getAbsolutePath();
	}
}
