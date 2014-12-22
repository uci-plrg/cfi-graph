package edu.uci.eecs.crowdsafe.graph.data.monitor;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.graph.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.graph.data.dist.SoftwareUnit;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.graph.data.graph.Node;
import edu.uci.eecs.crowdsafe.graph.data.graph.NodeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.OrdinalEdgeList;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterBasicBlock;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.loader.ClusterGraphLoadSession;
import edu.uci.eecs.crowdsafe.graph.io.cluster.ClusterTraceDataSource;
import edu.uci.eecs.crowdsafe.graph.io.cluster.ClusterTraceDirectory;

public class MonitorDatasetGenerator {

	private static class CatalogPointers {
		int namePointsTo;
		int dataPointsTo;
	}

	private static class AnonymousNodeEntry {
		final int offset;
		final long hash;
		final int edgeCountWord;
		final Edge<ClusterNode<?>> intraModuleEdges[];
		final long calloutSites[];
		final long exports[];

		@SuppressWarnings("unchecked")
		public AnonymousNodeEntry(int offset, long hash, int edgeCountWord, int intraModuleCount, int calloutSiteCount,
				int exportCount) {
			this.offset = offset;
			this.hash = hash;
			this.edgeCountWord = edgeCountWord;
			intraModuleEdges = new Edge[intraModuleCount];
			calloutSites = new long[calloutSiteCount];
			exports = new long[exportCount];
		}
	}

	private static class OrderByRelativeTag implements Comparator<ClusterBasicBlock> {
		@Override
		public int compare(ClusterBasicBlock first, ClusterBasicBlock second) {
			return (first.getRelativeTag() - second.getRelativeTag());
		}
	}

	private static class OrderByModuleName implements Comparator<AutonomousSoftwareDistribution> {
		@Override
		public int compare(AutonomousSoftwareDistribution first, AutonomousSoftwareDistribution second) {
			return first.getUnits().iterator().next().name.compareTo(second.getUnits().iterator().next().name);
		}
	}

	private static class OrderByHash implements Comparator<Long> {
		@Override
		public int compare(Long first, Long second) {
			long firstHalf = ((first >> 0x20) & 0xffffffffL);
			long secondHalf = ((second >> 0x20) & 0xffffffffL);
			int comparison = Long.signum(firstHalf - secondHalf);
			if (comparison != 0)
				return comparison;

			firstHalf = (first & 0xffffffffL);
			secondHalf = (second & 0xffffffffL);
			return Long.signum(firstHalf - secondHalf);
		}
	}

	private enum MonitorFileSegment {
		IMAGE_INDEX("image-index"),
		IMAGE_NAMES("image-names"),
		IMAGE_GRAPHS("image-graphs"),
		ANONYMOUS_INDEX("anonymous-index"),
		ANONYMOUS_GRAPH("anonymous-graph"),
		ALARM_CONFIGURATION("alarm-configuration");

		final String id;

		private MonitorFileSegment(String id) {
			this.id = id;
		}
	}

	public static final int INTRA_MODULE_EDGE_MASK = 0x7ff;
	public static final int CALLOUT_EDGE_MASK = 0xfff;
	public static final int EXPORT_EDGE_MASK = 0xff;

	private static final Charset ascii = Charset.forName("US-ASCII");

	private final File clusterDataDirectory;

	private final ClusterTraceDataSource dataSource;
	private final ClusterGraphLoadSession loadSession;

	private final Map<SoftwareUnit, CatalogPointers> catalogPointers = new LinkedHashMap<SoftwareUnit, CatalogPointers>();
	private final Map<ClusterBasicBlock, Integer> nodePointers = new LinkedHashMap<ClusterBasicBlock, Integer>();
	private final Map<Long, Integer> hashChainPointers = new HashMap<Long, Integer>();

	private final List<AutonomousSoftwareDistribution> sortedImageClusters;
	private final Map<Long, NodeList<ClusterNode<?>>> anonymousNodesBySortedHash;

	private final AlarmConfiguration alarmConfiguration;

	private final Map<MonitorFileSegment, File> outputFiles = new EnumMap<MonitorFileSegment, File>(
			MonitorFileSegment.class);
	private final File outputFile;

	private final int imageIndexSize;
	private int imageNamesSize;
	private int imageDataSize;

	public MonitorDatasetGenerator(File clusterDataDirectory, File outputFile, File alarmConfigFile) throws IOException {
		this.clusterDataDirectory = clusterDataDirectory;

		dataSource = new ClusterTraceDirectory(clusterDataDirectory).loadExistingFiles();
		loadSession = new ClusterGraphLoadSession(dataSource);

		OrderByModuleName nameOrder = new OrderByModuleName();
		sortedImageClusters = new ArrayList<AutonomousSoftwareDistribution>();
		for (AutonomousSoftwareDistribution cluster : dataSource.getReprsentedClusters()) {
			if (cluster != ConfiguredSoftwareDistributions.ANONYMOUS_CLUSTER) {
				if (cluster.isAnonymous())
					throw new IllegalArgumentException(String.format(
							"Found a dynamic module that has not been integrated into the anonymous module: %s.\n"
									+ "Please merge the graph before generating monitor data (unity merge is valid).",
							cluster.name));
				sortedImageClusters.add(cluster);
			}
		}
		Collections.sort(sortedImageClusters, nameOrder);
		imageIndexSize = sortedImageClusters.size() * 8;

		ModuleGraphCluster<ClusterNode<?>> anonymousGraph = loadSession
				.loadClusterGraph(ConfiguredSoftwareDistributions.ANONYMOUS_CLUSTER);
		if (anonymousGraph == null) {
			anonymousNodesBySortedHash = null;
		} else {
			anonymousNodesBySortedHash = new TreeMap<Long, NodeList<ClusterNode<?>>>(new OrderByHash());
			for (Long hash : anonymousGraph.getGraphData().nodesByHash.keySet()) {
				anonymousNodesBySortedHash.put(hash, anonymousGraph.getGraphData().nodesByHash.get(hash));
			}
		}

		if (alarmConfigFile == null)
			alarmConfiguration = null;
		else
			alarmConfiguration = new AlarmConfiguration(alarmConfigFile);

		String baseFilename = outputFile.getName();
		if (outputFile.getParentFile() == null)
			outputFile = new File(new File("."), outputFile.getName());
		this.outputFile = outputFile;

		int lastDot = baseFilename.lastIndexOf('.');
		if (lastDot >= 0) {
			String start = baseFilename.substring(0, lastDot);
			String end = baseFilename.substring(lastDot + 1);
			for (MonitorFileSegment segment : MonitorFileSegment.values()) {
				outputFiles.put(segment,
						new File(outputFile.getParentFile(), String.format("%s.%s.%s", start, segment.id, end)));
			}
		} else {
			for (MonitorFileSegment segment : MonitorFileSegment.values()) {
				outputFiles.put(segment,
						new File(outputFile.getParentFile(), String.format("%s.%s", baseFilename, segment.id)));
			}
		}
	}

	public void generateDataset() throws IOException {
		generateNameBlock();
		generateModules();
		generateImageIndex();

		if (anonymousNodesBySortedHash != null)
			generateAnonymousModule();
		generateAnonymousIndex();

		if (alarmConfiguration != null)
			generateAlarmConfiguration();

		generateConcatenationScript();
	}

	private void generateNameBlock() throws IOException {
		LittleEndianCursorWriter writer = new LittleEndianCursorWriter(outputFiles.get(MonitorFileSegment.IMAGE_NAMES));
		int baseOffset = imageIndexSize;
		for (AutonomousSoftwareDistribution cluster : sortedImageClusters) {
			SoftwareUnit unit = cluster.getUnits().iterator().next();
			CatalogPointers unitPointers = new CatalogPointers();
			catalogPointers.put(unit, unitPointers);
			unitPointers.namePointsTo = baseOffset + writer.getCursor();

			writer.writeString(unit.name, ascii);
		}
		writer.alignData(4);
		imageNamesSize = writer.getCursor();

		writer.conclude();
	}

	private void generateModules() throws IOException {
		LittleEndianCursorWriter writer = new LittleEndianCursorWriter(outputFiles.get(MonitorFileSegment.IMAGE_GRAPHS));
		List<ClusterBasicBlock> nodeSorter = new ArrayList<ClusterBasicBlock>();
		OrderByRelativeTag sortOrder = new OrderByRelativeTag();
		List<Edge<ClusterNode<?>>> intraModule = new ArrayList<Edge<ClusterNode<?>>>();
		List<Edge<ClusterNode<?>>> callSites = new ArrayList<Edge<ClusterNode<?>>>();
		List<Edge<ClusterNode<?>>> exports = new ArrayList<Edge<ClusterNode<?>>>();
		int baseOffset = imageIndexSize + imageNamesSize;

		for (AutonomousSoftwareDistribution cluster : sortedImageClusters) {
			nodeSorter.clear();
			SoftwareUnit unit = cluster.getUnits().iterator().next();

			ModuleGraphCluster<?> graph = loadSession.loadClusterGraph(cluster);
			for (Node<?> node : graph.getAllNodes()) {
				if ((node.getType() == MetaNodeType.NORMAL) || (node.getType() == MetaNodeType.RETURN)
						|| (node.getType() == MetaNodeType.SINGLETON))
					nodeSorter.add((ClusterBasicBlock) node);
			}
			Collections.sort(nodeSorter, sortOrder);

			int edgeCountWord;
			int intraModuleWord;
			boolean isIntraModuleIndirectTarget;
			for (ClusterBasicBlock node : nodeSorter) {
				nodePointers.put(node, baseOffset + writer.getCursor());

				intraModule.clear();
				callSites.clear();
				exports.clear();
				isIntraModuleIndirectTarget = false;

				OrdinalEdgeList<ClusterNode<?>> edges = node.getOutgoingEdges();
				try {
					for (Edge<ClusterNode<?>> edge : edges) {
						if (edge.getToNode().getType() == MetaNodeType.CLUSTER_EXIT) {
							callSites.add(edge);
						} else {
							intraModule.add(edge);
						}
					}
				} finally {
					edges.release();
				}

				edges = node.getIncomingEdges();
				try {
					for (Edge<ClusterNode<?>> edge : edges) {
						if (edge.getFromNode().getType() == MetaNodeType.CLUSTER_ENTRY) {
							exports.add(edge);
						} else {
							isIntraModuleIndirectTarget |= (edge.getEdgeType() == EdgeType.INDIRECT);
						}
					}
				} finally {
					edges.release();
				}

				while (intraModule.size() >= INTRA_MODULE_EDGE_MASK)
					intraModule.remove(intraModule.size() - 1); // hack!

				if (intraModule.size() >= INTRA_MODULE_EDGE_MASK)
					throw new IllegalStateException(String.format(
							"Intra-module edge count %d exceeds the data format limit of %d for node %s",
							intraModule.size(), INTRA_MODULE_EDGE_MASK, node));
				if (callSites.size() >= CALLOUT_EDGE_MASK)
					throw new IllegalStateException(String.format(
							"Callout edge count %d exceeds the data format limit of %d for node %s", callSites.size(),
							CALLOUT_EDGE_MASK, node));
				if (exports.size() >= EXPORT_EDGE_MASK)
					throw new IllegalStateException(String.format(
							"Export edge count %d exceeds the data format limit of %d for node %s", exports.size(),
							EXPORT_EDGE_MASK, node));

				edgeCountWord = (isIntraModuleIndirectTarget ? 1 : 0);
				edgeCountWord |= intraModule.size() << 1;
				edgeCountWord |= (callSites.size() << 0xc);
				edgeCountWord |= (exports.size() << 0x18);
				writer.writeInt(edgeCountWord);

				writer.writeLong(node.getHash());
				for (Edge<ClusterNode<?>> edge : intraModule) {
					if (edge.getToNode().getRelativeTag() > 0xfffffff)
						throw new IllegalStateException(
								"Relative tag exceeds intra-module edge format limit 0xfffffff!");

					intraModuleWord = edge.getToNode().getRelativeTag() & 0xfffffff;
					intraModuleWord |= (edge.getOrdinal() << 0x1c);
					writer.writeInt(intraModuleWord);
				}
				for (Edge<ClusterNode<?>> edge : callSites) {
					writer.writeLong(edge.getToNode().getHash());
				}
				for (Edge<ClusterNode<?>> edge : exports) {
					writer.writeLong(edge.getFromNode().getHash());
				}
			}

			CatalogPointers unitPointers = catalogPointers.get(unit);
			unitPointers.dataPointsTo = baseOffset + writer.getCursor();

			writer.writeInt(nodeSorter.size());
			for (ClusterBasicBlock node : nodeSorter) {
				writer.writeInt(node.getRelativeTag());
				writer.writeInt(nodePointers.get(node));
			}
		}

		imageDataSize = writer.getCursor();
		writer.conclude();
	}

	private void generateImageIndex() throws IOException {
		LittleEndianCursorWriter writer = new LittleEndianCursorWriter(outputFiles.get(MonitorFileSegment.IMAGE_INDEX));
		for (AutonomousSoftwareDistribution cluster : sortedImageClusters) {
			if (cluster.getUnitCount() > 1)
				throw new UnsupportedOperationException("Monitor dataset currently only supports singleton clusters.");

			SoftwareUnit unit = cluster.getUnits().iterator().next();
			CatalogPointers unitPointers = catalogPointers.get(unit);
			writer.writeInt(unitPointers.namePointsTo);
			writer.writeInt(unitPointers.dataPointsTo);

			catalogPointers.put(unit, unitPointers);
		}
		writer.conclude();
	}

	private void generateAnonymousModule() throws IOException {
		int cursor = anonymousNodesBySortedHash.size() * 12; // (8) hash, (4) pointer to node chain

		List<AnonymousNodeEntry> entries = new ArrayList<AnonymousNodeEntry>();
		Map<ClusterBasicBlock, Integer> entryOffsets = new HashMap<ClusterBasicBlock, Integer>();
		List<Edge<ClusterNode<?>>> intraModule = new ArrayList<Edge<ClusterNode<?>>>();
		List<Edge<ClusterNode<?>>> callSites = new ArrayList<Edge<ClusterNode<?>>>();
		List<Edge<ClusterNode<?>>> exports = new ArrayList<Edge<ClusterNode<?>>>();
		ClusterBasicBlock node;
		AnonymousNodeEntry entry;
		int edgeCountWord;
		int intraModuleWord;
		int lastIndexInChain;
		for (Map.Entry<Long, NodeList<ClusterNode<?>>> hashGroup : anonymousNodesBySortedHash.entrySet()) {
			hashChainPointers.put(hashGroup.getKey(), cursor);
			lastIndexInChain = hashGroup.getValue().size() - 1;
			for (int i = 0; i <= lastIndexInChain; i++) {
				node = (ClusterBasicBlock) hashGroup.getValue().get(i);
				entryOffsets.put(node, cursor);

				intraModule.clear();
				callSites.clear();
				exports.clear();

				OrdinalEdgeList<ClusterNode<?>> edges = node.getOutgoingEdges();
				try {
					for (Edge<ClusterNode<?>> edge : edges) {
						if (edge.getToNode().getType() == MetaNodeType.CLUSTER_EXIT) {
							callSites.add(edge);
						} else {
							intraModule.add(edge);
						}
					}
				} finally {
					edges.release();
				}

				edges = node.getIncomingEdges();
				try {
					for (Edge<ClusterNode<?>> edge : edges) {
						if (edge.getFromNode().getType() == MetaNodeType.CLUSTER_ENTRY) {
							exports.add(edge);
							if ((edge.getEdgeType() == EdgeType.GENCODE_PERM)
									|| (edge.getEdgeType() == EdgeType.GENCODE_WRITE))
								Log.log("Exporting gencode edge %s", edge);
						} else if ((edge.getEdgeType() == EdgeType.GENCODE_PERM)
								|| (edge.getEdgeType() == EdgeType.GENCODE_WRITE)) {
							Log.log("Not exporting gencode edge %s", edge);
						}
					}
				} finally {
					edges.release();
				}

				edgeCountWord = intraModule.size();
				edgeCountWord |= (callSites.size() << 0x8);
				edgeCountWord |= (exports.size() << 0x14);
				if (i != lastIndexInChain)
					edgeCountWord |= (1 << 0x1f);

				entry = new AnonymousNodeEntry(cursor, node.getHash(), edgeCountWord, intraModule.size(),
						callSites.size(), exports.size());
				entries.add(entry);

				for (int j = 0; j < intraModule.size(); j++) {
					Edge<ClusterNode<?>> edge = intraModule.get(j);
					entry.intraModuleEdges[j] = edge;
				}
				for (int j = 0; j < callSites.size(); j++) {
					Edge<ClusterNode<?>> edge = callSites.get(j);
					entry.calloutSites[j] = edge.getToNode().getHash();
				}
				for (int j = 0; j < exports.size(); j++) {
					Edge<ClusterNode<?>> edge = exports.get(j);
					entry.exports[j] = edge.getFromNode().getHash();
				}

				cursor += (8 /* hash */+ 4 /* edgeCountWord */+ (intraModule.size() * 4) + (callSites.size() * 8) + (exports
						.size() * 8));

				if (cursor > 0xffffff)
					throw new IllegalStateException("Cursor exceeds max offset 0xffffff!");
			}
		}

		LittleEndianCursorWriter writer = new LittleEndianCursorWriter(
				outputFiles.get(MonitorFileSegment.ANONYMOUS_GRAPH));
		for (AnonymousNodeEntry nodeEntry : entries) {
			writer.writeLong(nodeEntry.hash);
			writer.writeInt(nodeEntry.edgeCountWord);

			for (int i = 0; i < nodeEntry.intraModuleEdges.length; i++) {
				intraModuleWord = (entryOffsets.get(nodeEntry.intraModuleEdges[i].getToNode()) & 0xffffff);
				intraModuleWord |= (nodeEntry.intraModuleEdges[i].getOrdinal() << 0x18);
				intraModuleWord |= (nodeEntry.intraModuleEdges[i].getEdgeType().ordinal() << 0x1c);
				writer.writeInt(intraModuleWord);
			}

			for (int i = 0; i < nodeEntry.calloutSites.length; i++) {
				writer.writeLong(nodeEntry.calloutSites[i]);
			}

			for (int i = 0; i < nodeEntry.exports.length; i++) {
				writer.writeLong(nodeEntry.exports[i]);
			}
		}

		writer.conclude();
	}

	private void generateAnonymousIndex() throws IOException {
		LittleEndianCursorWriter writer = new LittleEndianCursorWriter(
				outputFiles.get(MonitorFileSegment.ANONYMOUS_INDEX));

		if ((anonymousNodesBySortedHash == null) || anonymousNodesBySortedHash.isEmpty()) {
			writer.writeLong(0L);
			writer.writeInt(0xf0f0f0f);
			Log.log("Write empty anonymous graph index");
		} else {
			for (Long hash : anonymousNodesBySortedHash.keySet()) {
				writer.writeLong(hash);
				writer.writeInt(hashChainPointers.get(hash));
			}
			Log.log("Write anonymous graph index");
		}

		writer.conclude();
	}

	private void generateAlarmConfiguration() throws IOException {
		LittleEndianCursorWriter writer = new LittleEndianCursorWriter(
				outputFiles.get(MonitorFileSegment.ALARM_CONFIGURATION));

		for (AlarmConfiguration.UnitPredicate predicate : AlarmConfiguration.UnitPredicate.values()) {
			writer.writeInt(alarmConfiguration.predicateInstanceCounts.get(predicate));
		}
		for (AlarmConfiguration.UnitPredicate predicate : AlarmConfiguration.UnitPredicate.values()) {
			writer.writeInt(alarmConfiguration.predicateInvocationCounts.get(predicate));
		}
		for (AlarmConfiguration.UIBInterval interval : AlarmConfiguration.UIBInterval.values()) {
			writer.writeInt(alarmConfiguration.uibIntervalCounts.get(interval));
		}
		for (AlarmConfiguration.UIBInterval interval : AlarmConfiguration.UIBInterval.values()) {
			writer.writeInt(alarmConfiguration.suibIntervalCounts.get(interval));
		}
		for (int i = 0; i < alarmConfiguration.suspiciousSyscallCounts.length; i++) {
			writer.writeInt(alarmConfiguration.suspiciousSyscallCounts[i]);
		}

		writer.conclude();
	}

	private void generateConcatenationScript() throws IOException {
		File script = new File(outputFile.getParentFile(), "concatenate");
		script.setExecutable(true);

		PrintWriter writer = new PrintWriter(script);

		writer.println("#!/bin/bash");
		writer.println();
		writer.println(String.format("cd %s", script.getParentFile().getAbsolutePath()));
		writer.println(String.format("mv %s %s", outputFiles.get(MonitorFileSegment.IMAGE_INDEX).getName(),
				outputFile.getName()));

		writer.println(String.format("cat %s >> %s", outputFiles.get(MonitorFileSegment.IMAGE_NAMES).getName(),
				outputFile.getName()));
		writer.println(String.format("rm %s", outputFiles.get(MonitorFileSegment.IMAGE_NAMES).getName()));

		writer.println(String.format("cat %s >> %s", outputFiles.get(MonitorFileSegment.IMAGE_GRAPHS).getName(),
				outputFile.getName()));
		writer.println(String.format("rm %s", outputFiles.get(MonitorFileSegment.IMAGE_GRAPHS).getName()));

		if (outputFiles.get(MonitorFileSegment.ANONYMOUS_INDEX).exists()) {
			writer.println(String.format("cat %s >> %s", outputFiles.get(MonitorFileSegment.ANONYMOUS_INDEX).getName(),
					outputFile.getName()));
			writer.println(String.format("rm %s", outputFiles.get(MonitorFileSegment.ANONYMOUS_INDEX).getName()));

			if (outputFiles.get(MonitorFileSegment.ANONYMOUS_GRAPH).exists()) {
				writer.println(String.format("cat %s >> %s", outputFiles.get(MonitorFileSegment.ANONYMOUS_GRAPH)
						.getName(), outputFile.getName()));
				writer.println(String.format("rm %s", outputFiles.get(MonitorFileSegment.ANONYMOUS_GRAPH).getName()));
			}
		}

		if (outputFiles.get(MonitorFileSegment.ALARM_CONFIGURATION).exists()) {
			writer.println(String.format("cat %s >> %s", outputFiles.get(MonitorFileSegment.ALARM_CONFIGURATION)
					.getName(), outputFile.getName()));
			writer.println(String.format("rm %s", outputFiles.get(MonitorFileSegment.ALARM_CONFIGURATION).getName()));
		}

		writer.flush();
		writer.close();
	}

	public static void main(String[] args) {
		List<Long> jumble = new ArrayList<Long>();
		Random random = new Random();

		for (int i = 0; i < 1000; i++) {
			jumble.add(random.nextLong());
		}

		Collections.sort(jumble, new OrderByHash());
		for (Long number : jumble) {
			System.out.println(String.format("0x%x", number));
		}
	}
}
