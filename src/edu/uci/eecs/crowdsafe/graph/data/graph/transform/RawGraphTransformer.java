package edu.uci.eecs.crowdsafe.graph.data.graph.transform;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import edu.uci.eecs.crowdsafe.common.config.CrowdSafeConfiguration;
import edu.uci.eecs.crowdsafe.common.exception.InvalidGraphException;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianOutputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.graph.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.graph.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.graph.data.dist.SoftwareModule;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterBasicBlock;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterBoundaryNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterModule;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.writer.ClusterDataWriter;
import edu.uci.eecs.crowdsafe.graph.data.graph.execution.ModuleInstance;
import edu.uci.eecs.crowdsafe.graph.data.graph.execution.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.execution.ProcessExecutionModuleSet;
import edu.uci.eecs.crowdsafe.graph.data.graph.execution.loader.ProcessModuleLoader;
import edu.uci.eecs.crowdsafe.graph.io.execution.ExecutionTraceDataSource;
import edu.uci.eecs.crowdsafe.graph.io.execution.ExecutionTraceDirectory;
import edu.uci.eecs.crowdsafe.graph.io.execution.ExecutionTraceStreamType;
import edu.uci.eecs.crowdsafe.graph.util.CrowdSafeTraceUtil;

public class RawGraphTransformer {

	private enum MetadataType {
		TIMEPOINT(0),
		UIB(1),
		INTERVAL(2),
		SSC(3),
		SGE(4);

		final int id;

		MetadataType(int id) {
			this.id = id;
		}

		static MetadataType forId(int id) {
			switch (id) {
				case 0:
					return TIMEPOINT;
				case 1:
					return UIB;
				case 2:
					return INTERVAL;
				case 3:
					return SSC;
				case 4:
					return SGE;
			}
			return null;
		}
	}

	private static class UnexpectedIndirectBranches {
		static final Map<Integer, RawUnexpectedIndirectBranch> uibsByEdgeIndex = new TreeMap<Integer, RawUnexpectedIndirectBranch>();

		final Map<Integer, RawUnexpectedIndirectBranch> uibsByRawEdgeIndex = new HashMap<Integer, RawUnexpectedIndirectBranch>();

		void add(RawUnexpectedIndirectBranch uib) {
			uibsByRawEdgeIndex.remove(uib.rawEdgeIndex); // take only the last UIB per raw edge index
			uibsByRawEdgeIndex.put(uib.rawEdgeIndex, uib);
		}

		Collection<RawUnexpectedIndirectBranch> sortAndMerge() {
			uibsByEdgeIndex.clear();

			for (RawUnexpectedIndirectBranch uib : uibsByRawEdgeIndex.values()) {
				RawUnexpectedIndirectBranch match = uibsByEdgeIndex.get(uib.getClusterEdgeIndex());
				if (match == null) {
					uibsByEdgeIndex.put(uib.getClusterEdgeIndex(), uib);
				} else {
					match.merge(uib);
				}
			}

			return uibsByEdgeIndex.values();
		}
	}

	private static final OptionArgumentMap.BooleanOption verboseOption = OptionArgumentMap.createBooleanOption('v');
	private static final OptionArgumentMap.StringOption logOption = OptionArgumentMap.createStringOption('l');
	private static final OptionArgumentMap.StringOption inputOption = OptionArgumentMap.createStringOption('i');
	private static final OptionArgumentMap.StringOption outputOption = OptionArgumentMap.createStringOption('o');
	private static final OptionArgumentMap.BooleanOption unitClusterOption = OptionArgumentMap.createBooleanOption('u',
			true);

	private final ArgumentStack args;

	private final ProcessModuleLoader executionModuleLoader = new ProcessModuleLoader();

	// transitory per run:

	private File outputDir = null;
	private ExecutionTraceDataSource dataSource = null;
	private ProcessExecutionModuleSet executionModules = null;
	private ClusterDataWriter.Directory<IndexedClusterNode> graphWriters = null;
	private final Map<AutonomousSoftwareDistribution, RawClusterData> nodesByCluster = new HashMap<AutonomousSoftwareDistribution, RawClusterData>();
	private final Map<AutonomousSoftwareDistribution, Map<RawEdge, RawEdge>> edgesByCluster = new HashMap<AutonomousSoftwareDistribution, Map<RawEdge, RawEdge>>();

	private final Set<AutonomousSoftwareDistribution> metadataClusters = new HashSet<AutonomousSoftwareDistribution>();
	private final Map<AutonomousSoftwareDistribution, UnexpectedIndirectBranches> uibsByCluster = new HashMap<AutonomousSoftwareDistribution, UnexpectedIndirectBranches>();
	private final Map<AutonomousSoftwareDistribution, Set<RawSuspiciousGencodeEntry>> sgesByCluster = new HashMap<AutonomousSoftwareDistribution, Set<RawSuspiciousGencodeEntry>>();
	private final Map<AutonomousSoftwareDistribution, Set<RawSuspiciousSystemCall>> sscsByCluster = new HashMap<AutonomousSoftwareDistribution, Set<RawSuspiciousSystemCall>>();

	private final Map<RawTag, IndexedClusterNode> nodesByRawTag = new HashMap<RawTag, IndexedClusterNode>();
	private final Map<RawTag, Integer> fakeAnonymousModuleTags = new HashMap<RawTag, Integer>();
	private int fakeAnonymousTagIndex = ClusterNode.FAKE_ANONYMOUS_TAG_START;
	private final Map<Long, IndexedClusterNode> syscallSingletons = new HashMap<Long, IndexedClusterNode>();
	private final Map<AutonomousSoftwareDistribution, ClusterNode<?>> blackBoxSingletons = new HashMap<AutonomousSoftwareDistribution, ClusterNode<?>>();

	private int mainModuleStartAddress;
	private AutonomousSoftwareDistribution mainCluster;
	private final Map<RawUnexpectedIndirectBranchInterval.Key, RawUnexpectedIndirectBranchInterval> uibIntervals = new HashMap<RawUnexpectedIndirectBranchInterval.Key, RawUnexpectedIndirectBranchInterval>();
	private final LinkedList<RawUnexpectedIndirectBranch> intraModuleUIBQueue = new LinkedList<RawUnexpectedIndirectBranch>();
	private final LinkedList<RawUnexpectedIndirectBranch> crossModuleUIBQueue = new LinkedList<RawUnexpectedIndirectBranch>();
	private final LinkedList<RawSuspiciousGencodeEntry> gencodeEntryQueue = new LinkedList<RawSuspiciousGencodeEntry>();
	private final LinkedList<RawSuspiciousSystemCall> intraModuleSuspiciousSyscallQueue = new LinkedList<RawSuspiciousSystemCall>();
	private final LinkedList<RawSuspiciousSystemCall> crossModuleSuspiciousSyscallQueue = new LinkedList<RawSuspiciousSystemCall>();

	public RawGraphTransformer(ArgumentStack args) {
		this.args = args;

		OptionArgumentMap.populateOptions(args, verboseOption, logOption, inputOption, outputOption, unitClusterOption);
	}

	private void run() {
		try {
			if (verboseOption.getValue() || (logOption.getValue() == null)) {
				Log.addOutput(System.out);
			}
			if (logOption.getValue() != null) {
				Log.addOutput(new File(logOption.getValue()));
			}

			if ((inputOption.getValue() == null) != (outputOption.getValue() == null))
				throw new IllegalArgumentException("The input (-i) and output (-o) options must be used together!");

			List<String> pathList = new ArrayList<String>();
			if (inputOption.getValue() == null) {
				while (args.size() > 0)
					pathList.add(args.pop());
			} else {
				if (args.size() > 0)
					throw new IllegalArgumentException(
							"The input (-i) and output (-o) options cannot be used with a list of run directories!");

				pathList.add(inputOption.getValue());
			}

			CrowdSafeConfiguration
					.initialize(new CrowdSafeConfiguration.Environment[] { CrowdSafeConfiguration.Environment.CROWD_SAFE_COMMON_DIR });

			ConfiguredSoftwareDistributions.ClusterMode clusterMode;
			if (unitClusterOption.hasValue())
				clusterMode = ConfiguredSoftwareDistributions.ClusterMode.UNIT;
			else
				clusterMode = ConfiguredSoftwareDistributions.ClusterMode.GROUP;
			ConfiguredSoftwareDistributions.initialize(clusterMode);

			for (String inputPath : pathList) {
				try {
					File runDir = new File(inputPath);
					if (!(runDir.exists() && runDir.isDirectory())) {
						Log.log("Warning: input path %s is not a directory!", runDir.getAbsolutePath());
						continue;
					}

					dataSource = new ExecutionTraceDirectory(runDir, ProcessExecutionGraph.EXECUTION_GRAPH_FILE_TYPES,
							ProcessExecutionGraph.EXECUTION_GRAPH_REQUIRED_FILE_TYPES);

					File outputDir;
					if (outputOption.getValue() == null) {
						outputDir = new File(runDir, "cluster");
					} else {
						outputDir = new File(outputOption.getValue());
					}
					outputDir.mkdirs();
					graphWriters = new ClusterDataWriter.Directory<IndexedClusterNode>(outputDir,
							dataSource.getProcessName());
					Log.log("Transform %s to %s", runDir.getAbsolutePath(), outputDir.getAbsolutePath());
					transformGraph();
				} catch (Throwable t) {
					Log.log("Error transforming %s", inputPath);
					Log.log(t);

					System.err.println("Error transforming " + inputPath);
					t.printStackTrace();
				}

				outputDir = null;
				dataSource = null;
				executionModules = null;
				graphWriters = null;
				nodesByCluster.clear();
				edgesByCluster.clear();
				nodesByRawTag.clear();
				fakeAnonymousModuleTags.clear();
				fakeAnonymousTagIndex = ClusterNode.FAKE_ANONYMOUS_TAG_START;
				syscallSingletons.clear();
				blackBoxSingletons.clear();
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private void transformGraph() throws IOException {
		executionModules = executionModuleLoader.loadModules(dataSource);

		loadMetadata(ExecutionTraceStreamType.META);
		transformNodes(ExecutionTraceStreamType.GRAPH_NODE);
		transformEdges(ExecutionTraceStreamType.GRAPH_EDGE);
		transformCrossModuleEdges(ExecutionTraceStreamType.CROSS_MODULE_EDGE);

		Log.log("After transforming all elements, queues contains: %d intra-module, %d cross-module, %d gencode entry, %d suspicious system calls (intra-module), %d suspicious system calls (cross-module)",
				intraModuleUIBQueue.size(), crossModuleUIBQueue.size(), gencodeEntryQueue.size(),
				intraModuleSuspiciousSyscallQueue.size(), crossModuleSuspiciousSyscallQueue.size());

		writeNodes();
		writeEdges();
		writeMetadata();
		writeModules();
		graphWriters.flush();
	}

	private void loadMetadata(ExecutionTraceStreamType streamType) throws IOException {
		LittleEndianInputStream input = dataSource.getLittleEndianInputStream(streamType);
		if (input == null)
			return;

		long mainModuleStartAddress = input.readLong();
		ModuleInstance mainModule = executionModules.getModule(mainModuleStartAddress, 0,
				ExecutionTraceStreamType.GRAPH_NODE);
		mainCluster = ConfiguredSoftwareDistributions.getInstance().distributionsByUnit.get(mainModule.unit);
		graphWriters.establishClusterWriters(establishClusterData(mainCluster));
		Log.log("Main cluster is %s", mainCluster);

		RawGraphEntry.OneWordFactory factory = new RawGraphEntry.OneWordFactory(input);

		// TODO: structure, new entry content, levels

		while (factory.hasMoreEntries()) {
			RawGraphEntry.OneWordEntry nodeEntry = factory.createEntry();

			MetadataType type = MetadataType.forId((int) (nodeEntry.first & 0xff));
			if (type == MetadataType.TIMEPOINT)
				continue;

			switch (type) {
				case UIB:
					RawUnexpectedIndirectBranch uib = RawUnexpectedIndirectBranch.parse(nodeEntry.first);

					// Log.log("Loaded UIB #%d: %d traversals", uib.rawEdgeIndex, uib.getTraversalCount());

					if (uib.isCrossModule)
						crossModuleUIBQueue.add(uib);
					else
						intraModuleUIBQueue.add(uib);
					break;
				case INTERVAL:
					RawUnexpectedIndirectBranchInterval interval = RawUnexpectedIndirectBranchInterval
							.parse(nodeEntry.first);
					uibIntervals.remove(interval.key); // extract the last interval per {type+span}
					uibIntervals.put(interval.key, interval);
					break;
				case SSC:
					RawSuspiciousSystemCall syscall = RawSuspiciousSystemCall.parse(nodeEntry.first);
					if (syscall.isCrossModule)
						crossModuleSuspiciousSyscallQueue.add(syscall);
					else
						intraModuleSuspiciousSyscallQueue.add(syscall);
					break;
				case SGE:
					RawSuspiciousGencodeEntry gencodeEntry = RawSuspiciousGencodeEntry.parse(nodeEntry.first);
					gencodeEntryQueue.add(gencodeEntry);
					break;
			}
		}

		Collections.sort(intraModuleUIBQueue, RawUnexpectedIndirectBranch.ExecutionEdgeIndexSorter.INSTANCE);
		Collections.sort(crossModuleUIBQueue, RawUnexpectedIndirectBranch.ExecutionEdgeIndexSorter.INSTANCE);
		Collections.sort(gencodeEntryQueue, RawSuspiciousGencodeEntry.ExecutionEdgeIndexSorter.INSTANCE);
		Collections.sort(crossModuleSuspiciousSyscallQueue, RawSuspiciousSystemCall.ExecutionEdgeIndexSorter.INSTANCE);
		Collections.sort(intraModuleSuspiciousSyscallQueue, RawSuspiciousSystemCall.ExecutionEdgeIndexSorter.INSTANCE);

		Log.log("Queue sizes: %d IM, %d CM, %d GE", intraModuleUIBQueue.size(), crossModuleUIBQueue.size(),
				gencodeEntryQueue.size());
	}

	private void transformNodes(ExecutionTraceStreamType streamType) throws IOException {
		LittleEndianInputStream input = dataSource.getLittleEndianInputStream(streamType);
		RawGraphEntry.TwoWordFactory factory = new RawGraphEntry.TwoWordFactory(input);

		RawClusterData nodeData = establishClusterData(ConfiguredSoftwareDistributions.SYSTEM_CLUSTER);
		nodeData.moduleList.establishModule(ModuleInstance.SYSTEM_MODULE.unit);
		ClusterNode<?> node = new ClusterBasicBlock(SoftwareModule.SYSTEM_MODULE, ClusterNode.PROCESS_ENTRY_SINGLETON,
				0, ClusterNode.PROCESS_ENTRY_SINGLETON, MetaNodeType.SINGLETON);
		IndexedClusterNode nodeId = nodeData.addNode(node);
		RawTag rawTag = new RawTag(ClusterNode.PROCESS_ENTRY_SINGLETON, 0);
		fakeAnonymousModuleTags.put(rawTag, ClusterNode.PROCESS_ENTRY_SINGLETON);
		nodesByRawTag.put(rawTag, nodeId);
		graphWriters.establishClusterWriters(nodesByCluster.get(ConfiguredSoftwareDistributions.SYSTEM_CLUSTER));

		node = new ClusterBasicBlock(SoftwareModule.SYSTEM_MODULE, ClusterNode.SYSTEM_SINGLETON, 0,
				ClusterNode.SYSTEM_SINGLETON, MetaNodeType.SINGLETON);
		nodeId = nodeData.addNode(node);
		rawTag = new RawTag(ClusterNode.SYSTEM_SINGLETON, 0);
		fakeAnonymousModuleTags.put(rawTag, ClusterNode.SYSTEM_SINGLETON);
		nodesByRawTag.put(rawTag, nodeId);

		node = new ClusterBasicBlock(SoftwareModule.SYSTEM_MODULE, ClusterNode.CHILD_PROCESS_SINGLETON, 0,
				ClusterNode.CHILD_PROCESS_SINGLETON, MetaNodeType.SINGLETON);
		nodeId = nodeData.addNode(node);
		rawTag = new RawTag(ClusterNode.CHILD_PROCESS_SINGLETON, 0);
		fakeAnonymousModuleTags.put(rawTag, ClusterNode.CHILD_PROCESS_SINGLETON);
		nodesByRawTag.put(rawTag, nodeId);

		long entryIndex = -1L;
		while (factory.hasMoreEntries()) {
			RawGraphEntry.TwoWordEntry nodeEntry = factory.createEntry();
			entryIndex++;

			long absoluteTag = CrowdSafeTraceUtil.getTag(nodeEntry.first);
			int tagVersion = CrowdSafeTraceUtil.getTagVersion(nodeEntry.first);
			MetaNodeType nodeType = CrowdSafeTraceUtil.getNodeMetaType(nodeEntry.first);

			ModuleInstance moduleInstance;
			if (nodeType == MetaNodeType.SINGLETON) {
				if ((absoluteTag == ClusterNode.PROCESS_ENTRY_SINGLETON)
						|| (absoluteTag == ClusterNode.SYSTEM_SINGLETON)
						|| (absoluteTag == ClusterNode.CHILD_PROCESS_SINGLETON)) {
					moduleInstance = ModuleInstance.SYSTEM;
				} else if ((absoluteTag >= ClusterNode.BLACK_BOX_SINGLETON_START)
						&& (absoluteTag < ClusterNode.BLACK_BOX_SINGLETON_END)) {
					moduleInstance = ModuleInstance.ANONYMOUS;
				} else {
					throw new InvalidGraphException("Error: unknown singleton with tag 0x%x!", absoluteTag);
				}
			} else if ((absoluteTag >= ClusterNode.BLACK_BOX_SINGLETON_START) // FIXME: temporary hack
					&& (absoluteTag < ClusterNode.BLACK_BOX_SINGLETON_END)) {
				moduleInstance = ModuleInstance.ANONYMOUS;
			} else {
				moduleInstance = executionModules.getModule(absoluteTag, entryIndex, streamType);
			}
			if (moduleInstance == null) {
				Log.log("Error: cannot find the module for node 0x%x-v%d (type %s)", absoluteTag, tagVersion, nodeType);
				continue;
			}

			AutonomousSoftwareDistribution cluster = ConfiguredSoftwareDistributions.getInstance().distributionsByUnit
					.get(moduleInstance.unit);

			int relativeTag;
			ClusterModule clusterModule;
			AutonomousSoftwareDistribution blackBoxOwner = null;
			boolean isNewBlackBoxSingleton = false;
			if (cluster.isAnonymous()) {
				if ((absoluteTag == ClusterNode.PROCESS_ENTRY_SINGLETON)
						|| (absoluteTag == ClusterNode.SYSTEM_SINGLETON)
						|| (absoluteTag == ClusterNode.CHILD_PROCESS_SINGLETON)) {
					clusterModule = establishClusterData(ConfiguredSoftwareDistributions.SYSTEM_CLUSTER).moduleList
							.establishModule(SoftwareModule.SYSTEM_MODULE.unit);
					cluster = ConfiguredSoftwareDistributions.SYSTEM_CLUSTER;
					moduleInstance = ModuleInstance.SYSTEM;
				} else {
					clusterModule = establishClusterData(ConfiguredSoftwareDistributions.ANONYMOUS_CLUSTER).moduleList
							.establishModule(SoftwareModule.ANONYMOUS_MODULE.unit);
					cluster = ConfiguredSoftwareDistributions.ANONYMOUS_CLUSTER;
					moduleInstance = ModuleInstance.ANONYMOUS;
					if (nodeType == MetaNodeType.SINGLETON) {
						blackBoxOwner = ConfiguredSoftwareDistributions.getInstance().getClusterByAnonymousEntryHash(
								nodeEntry.second);
						if (blackBoxOwner == null)
							new InvalidGraphException("Error: cannot find the owner of black box with entry 0x%x!",
									nodeEntry.second);

						// TODO: removing extraneous nodes requires patching the data set for the whole
						// anonymous module
					}
				}

				RawTag lookup = new RawTag(absoluteTag, tagVersion);
				Integer tag = null;
				if (blackBoxOwner == null) {
					tag = fakeAnonymousModuleTags.get(lookup);
				} else { // this is not necessary now, there is only one instance of the singleton
					ClusterNode<?> singleton = blackBoxSingletons.get(blackBoxOwner);
					if (singleton != null) {
						tag = singleton.getRelativeTag();
						fakeAnonymousModuleTags.put(lookup, tag);
					}
				}

				if (tag == null) {
					if (blackBoxOwner != null) {
						isNewBlackBoxSingleton = true;
						tag = (int) absoluteTag;
					} else {
						tag = fakeAnonymousTagIndex++;
						fakeAnonymousModuleTags.put(lookup, tag);
						Log.log("Mapping 0x%x-v%d => 0x%x for module %s (hash 0x%x)", absoluteTag, tagVersion, tag,
								moduleInstance.unit.filename, nodeEntry.second);
					}
				}
				relativeTag = tag;
			} else {
				relativeTag = (int) (absoluteTag - moduleInstance.start);
			}

			nodeData = establishClusterData(cluster);
			if ((blackBoxOwner == null) || isNewBlackBoxSingleton) {
				clusterModule = establishClusterData(cluster).moduleList.establishModule(moduleInstance.unit);
				node = new ClusterBasicBlock(clusterModule, relativeTag, cluster.isAnonymous() ? 0 : tagVersion,
						nodeEntry.second, nodeType);
				if (isNewBlackBoxSingleton)
					blackBoxSingletons.put(blackBoxOwner, node);
				nodeId = nodeData.addNode(node);
			} else {
				node = blackBoxSingletons.get(blackBoxOwner);
				nodeId = nodeData.getNode(node.getKey());
			}

			graphWriters.establishClusterWriters(nodesByCluster.get(cluster));

			if (cluster.isAnonymous())
				nodesByRawTag.put(new RawTag(absoluteTag, tagVersion), nodeId);
		}
	}

	private void transformEdges(ExecutionTraceStreamType streamType) throws IOException {
		LittleEndianInputStream input = dataSource.getLittleEndianInputStream(streamType);
		RawGraphEntry.TwoWordFactory factory = new RawGraphEntry.TwoWordFactory(input);

		long entryIndex = -1L;
		while (factory.hasMoreEntries()) {
			RawGraphEntry.TwoWordEntry edgeEntry = factory.createEntry();
			entryIndex++;

			long absoluteFromTag = CrowdSafeTraceUtil.getTag(edgeEntry.first);
			int fromTagVersion = CrowdSafeTraceUtil.getTagVersion(edgeEntry.first);
			IndexedClusterNode fromNodeId = identifyNode(absoluteFromTag, fromTagVersion, entryIndex, streamType);

			EdgeType type = CrowdSafeTraceUtil.getTagEdgeType(edgeEntry.first);
			int ordinal = CrowdSafeTraceUtil.getEdgeOrdinal(edgeEntry.first);
			if (type == EdgeType.GENCODE_PERM) // hack
				ordinal = 3;
			else if (type == EdgeType.GENCODE_WRITE)
				ordinal = 4;

			long absoluteToTag = CrowdSafeTraceUtil.getTag(edgeEntry.second);
			int toTagVersion = CrowdSafeTraceUtil.getTagVersion(edgeEntry.second);
			IndexedClusterNode toNodeId = identifyNode(absoluteToTag, toTagVersion, entryIndex, streamType);

			// if (type == EdgeType.UNEXPECTED_RETURN)
			// Log.log("Loaded unexpected return from 0x%x to 0x%x", absoluteFromTag, absoluteToTag);

			if (fromNodeId == null) {
				if (toNodeId == null) {
					Log.log("Error: both nodes missing in edge (0x%x-v%d) -%s-%d-> (0x%x-v%d)", absoluteFromTag,
							fromTagVersion, type.code, ordinal, absoluteToTag, toTagVersion);
				} else {
					Log.log("Error in cluster %s: missing 'from' node: (0x%x-v%d) -%s-%d-> (0x%x-v%d)",
							toNodeId.cluster.getUnitFilename(), absoluteFromTag, fromTagVersion, type.code, ordinal,
							absoluteToTag, toTagVersion);
				}
				continue;
			}

			if (toNodeId == null) {
				Log.log("Error in cluster %s: missing 'to' node: (0x%x-v%d) -%s-%d-> (0x%x-v%d)",
						fromNodeId.cluster.getUnitFilename(), absoluteFromTag, fromTagVersion, type.code, ordinal,
						absoluteToTag, toTagVersion);
				continue;
			}

			if (type.isHighOrdinal(ordinal))
				Log.log("Warning: high ordinal in %s edge (0x%x-v%d) -%s-%d-> (0x%x-v%d)",
						fromNodeId.cluster.getUnitFilename(), absoluteFromTag, fromTagVersion, type.code, ordinal,
						absoluteToTag, toTagVersion);

			if (fromNodeId.cluster == toNodeId.cluster) {
				RawEdge edge;
				if (fromNodeId.cluster.isAnonymous())
					edge = addEdge(ConfiguredSoftwareDistributions.ANONYMOUS_CLUSTER, fromNodeId, toNodeId, type,
							ordinal);
				else
					edge = addEdge(fromNodeId.cluster, fromNodeId, toNodeId, type, ordinal);

				RawUnexpectedIndirectBranch uib = null;
				while (!intraModuleUIBQueue.isEmpty() && (intraModuleUIBQueue.peekFirst().rawEdgeIndex == entryIndex))
					uib = intraModuleUIBQueue.removeFirst();
				while (!intraModuleSuspiciousSyscallQueue.isEmpty()
						&& intraModuleSuspiciousSyscallQueue.peekFirst().edgeIndex == entryIndex) {
					RawSuspiciousSystemCall ssc = intraModuleSuspiciousSyscallQueue.removeFirst();
					ssc.entryEdge = ssc.exitEdge = edge;
					establishSSCs(fromNodeId.cluster).add(ssc);

					// Log.log("SSC: raising edge: %s (index %d); next IM SSC edge index is %d",
					// edge,
					// ssc.edgeIndex,
					// intraModuleSuspiciousSyscallQueue.isEmpty() ? 0 : intraModuleSuspiciousSyscallQueue
					// .peekFirst().edgeIndex);
				}

				if (uib != null) {
					uib.clusterEdge = edge;
					establishUIBs(fromNodeId.cluster).add(uib);
				}
			} else {
				Log.log("Error! Intra-module edge from %s to %s crosses a cluster boundary (%s to %s)!",
						fromNodeId.node, toNodeId.node, fromNodeId.cluster, toNodeId.cluster);
				// throw new IllegalStateException(String.format(
				// "Intra-module edge from %s to %s crosses a cluster boundary!", fromNodeId.node, toNodeId.node));
			}
		}
	}

	private void transformCrossModuleEdges(ExecutionTraceStreamType streamType) throws IOException {
		LittleEndianInputStream input = dataSource.getLittleEndianInputStream(streamType);
		RawGraphEntry.ThreeWordFactory factory = new RawGraphEntry.ThreeWordFactory(input);

		long entryIndex = -1L;
		while (factory.hasMoreEntries()) {
			RawGraphEntry.ThreeWordEntry edgeEntry = factory.createEntry();
			entryIndex++;

			long absoluteFromTag = CrowdSafeTraceUtil.getTag(edgeEntry.first);
			int fromTagVersion = CrowdSafeTraceUtil.getTagVersion(edgeEntry.first);
			EdgeType type = CrowdSafeTraceUtil.getTagEdgeType(edgeEntry.first);
			int ordinal = CrowdSafeTraceUtil.getEdgeOrdinal(edgeEntry.first);
			if (type == EdgeType.GENCODE_PERM) // hack
				ordinal = 3;
			else if (type == EdgeType.GENCODE_WRITE)
				ordinal = 4;
			IndexedClusterNode fromNodeId = identifyNode(absoluteFromTag, fromTagVersion, entryIndex, streamType);

			long absoluteToTag = CrowdSafeTraceUtil.getTag(edgeEntry.second);
			int toTagVersion = CrowdSafeTraceUtil.getTagVersion(edgeEntry.second);
			IndexedClusterNode toNodeId = identifyNode(absoluteToTag, toTagVersion, entryIndex, streamType);

			long hash = edgeEntry.third;

			if (fromNodeId == null) {
				if (toNodeId == null) {
					Log.log("Error: both nodes missing in cross-module %s edge 0x%x-v%d -> 0x%x-v%d", type.code,
							absoluteFromTag, fromTagVersion, absoluteToTag, toTagVersion);
				} else {
					Log.log("Error: missing 'from' node 0x%x-v%d in cross-module %s edge to %s(0x%x-v%d) ",
							absoluteFromTag, fromTagVersion, type.code, toNodeId.cluster.getUnitFilename(),
							absoluteToTag, toTagVersion);
				}
				continue;
			}

			if (toNodeId == null) {
				Log.log("Error: missing 'to' node 0x%x-v%d in cross-module %s edge from %s 0x%x-v%d", absoluteToTag,
						toTagVersion, type.code, fromNodeId.cluster.getUnitFilename(), absoluteFromTag, fromTagVersion);
				continue;
			}

			if (type.isHighOrdinal(ordinal))
				Log.log("Warning: high ordinal in cross-module edge %s(0x%x-v%d) -%s-%d-> %s(0x%x-v%d)",
						fromNodeId.cluster.getUnitFilename(), absoluteFromTag, fromTagVersion, type.code, ordinal,
						toNodeId.cluster.getUnitFilename(), absoluteToTag, toTagVersion);

			if (fromNodeId.cluster == toNodeId.cluster) { // TODO: why is this possible? what about UIB?
				addEdge(fromNodeId.cluster, fromNodeId, toNodeId, type, ordinal);
			} else if (fromNodeId.cluster.isAnonymous() && toNodeId.cluster.isAnonymous()) {
				addEdge(ConfiguredSoftwareDistributions.ANONYMOUS_CLUSTER, fromNodeId, toNodeId, type, ordinal);
			} else {
				ClusterBoundaryNode entry = new ClusterBoundaryNode(hash, MetaNodeType.CLUSTER_ENTRY);
				IndexedClusterNode entryId = nodesByCluster.get(toNodeId.cluster).addNode(entry);
				RawEdge rawEntry = addEdge(toNodeId.cluster, entryId, toNodeId, type, type.getClusterEntryOrdinal());

				ClusterBoundaryNode exit = new ClusterBoundaryNode(hash, MetaNodeType.CLUSTER_EXIT);
				IndexedClusterNode exitId = nodesByCluster.get(fromNodeId.cluster).addNode(exit);
				RawEdge rawExit = addEdge(fromNodeId.cluster, fromNodeId, exitId, type, ordinal);

				if (toNodeId.cluster == ConfiguredSoftwareDistributions.SYSTEM_CLUSTER)
					Log.log("Creating an edge into the system cluster: %s", rawExit);

				RawUnexpectedIndirectBranch uib = null;
				while ((!crossModuleUIBQueue.isEmpty()) && (crossModuleUIBQueue.peekFirst().rawEdgeIndex == entryIndex))
					uib = crossModuleUIBQueue.removeFirst();

				if (uib != null) {
					uib.clusterEdge = rawEntry;
					establishUIBs(toNodeId.cluster).add(uib);

					uib = new RawUnexpectedIndirectBranch(uib);
					uib.clusterEdge = rawExit;
					establishUIBs(fromNodeId.cluster).add(uib);
				}

				if ((!gencodeEntryQueue.isEmpty()) && (gencodeEntryQueue.peekFirst().edgeIndex == entryIndex)) {
					RawSuspiciousGencodeEntry gencodeEntry = gencodeEntryQueue.removeFirst();
					gencodeEntry.clusterEdge = rawExit;
					establishSGEs(fromNodeId.cluster).add(gencodeEntry);
				}
				while (!crossModuleSuspiciousSyscallQueue.isEmpty()
						&& crossModuleSuspiciousSyscallQueue.peekFirst().edgeIndex == entryIndex) {
					RawSuspiciousSystemCall ssc = crossModuleSuspiciousSyscallQueue.removeFirst();
					ssc.entryEdge = rawEntry;
					ssc.exitEdge = rawExit;
					establishSSCs(fromNodeId.cluster).add(ssc);

					// Log.log("SSC: raising edges: [%s] and [%s] (index %d); next CM SSC edge index: %d", rawEntry,
					// rawExit, ssc.edgeIndex, crossModuleSuspiciousSyscallQueue.isEmpty() ? 0
					// : crossModuleSuspiciousSyscallQueue.peekFirst().edgeIndex);
				}
			}
		}
	}

	private RawClusterData establishClusterData(AutonomousSoftwareDistribution cluster) {
		RawClusterData data = nodesByCluster.get(cluster);
		if (data == null) {
			data = new RawClusterData(cluster);
			nodesByCluster.put(cluster, data);
		}
		return data;
	}

	private RawEdge addEdge(AutonomousSoftwareDistribution cluster, IndexedClusterNode fromNode,
			IndexedClusterNode toNode, EdgeType type, int ordinal) {
		Map<RawEdge, RawEdge> clusterEdges = establishEdgeSet(cluster);
		RawEdge edge = new RawEdge(fromNode, toNode, type, ordinal);
		RawEdge existing = clusterEdges.get(edge);
		if (existing == null) {
			clusterEdges.put(edge, edge);
			return edge;
		} else {
			return existing;
		}
	}

	private Map<RawEdge, RawEdge> establishEdgeSet(AutonomousSoftwareDistribution cluster) {
		Map<RawEdge, RawEdge> set = edgesByCluster.get(cluster);
		if (set == null) {
			set = new HashMap<RawEdge, RawEdge>();
			edgesByCluster.put(cluster, set);
		}
		return set;
	}

	private UnexpectedIndirectBranches establishUIBs(AutonomousSoftwareDistribution cluster) {
		UnexpectedIndirectBranches uibs = uibsByCluster.get(cluster);
		if (uibs == null) {
			uibs = new UnexpectedIndirectBranches();
			uibsByCluster.put(cluster, uibs);
			metadataClusters.add(cluster);
		}
		return uibs;
	}

	private Set<RawSuspiciousGencodeEntry> establishSGEs(AutonomousSoftwareDistribution cluster) {
		Set<RawSuspiciousGencodeEntry> sges = sgesByCluster.get(cluster);
		if (sges == null) {
			sges = new HashSet<RawSuspiciousGencodeEntry>();
			sgesByCluster.put(cluster, sges);
			metadataClusters.add(cluster);
		}
		return sges;
	}

	private Set<RawSuspiciousSystemCall> establishSSCs(AutonomousSoftwareDistribution cluster) {
		Set<RawSuspiciousSystemCall> sscs = sscsByCluster.get(cluster);
		if (sscs == null) {
			sscs = new HashSet<RawSuspiciousSystemCall>();
			sscsByCluster.put(cluster, sscs);
			metadataClusters.add(cluster);
		}
		return sscs;
	}

	private IndexedClusterNode identifyNode(long absoluteTag, int tagVersion, long entryIndex,
			ExecutionTraceStreamType streamType) {
		if ((absoluteTag >= ClusterNode.SYSCALL_SINGLETON_START) && (absoluteTag < ClusterNode.SYSCALL_SINGLETON_END)) {
			IndexedClusterNode nodeId = syscallSingletons.get(absoluteTag);
			if (nodeId == null) {
				RawClusterData nodeData = establishClusterData(ConfiguredSoftwareDistributions.SYSTEM_CLUSTER);
				ClusterBasicBlock node = new ClusterBasicBlock(SoftwareModule.SYSTEM_MODULE, absoluteTag, 0, 0L,
						MetaNodeType.SINGLETON);
				nodeId = nodeData.addNode(node);
				syscallSingletons.put(absoluteTag, nodeId);
			}
			return nodeId;
		}

		ModuleInstance moduleInstance;
		AutonomousSoftwareDistribution cluster;
		if ((absoluteTag == ClusterNode.PROCESS_ENTRY_SINGLETON) || (absoluteTag == ClusterNode.SYSTEM_SINGLETON)
				|| (absoluteTag == ClusterNode.CHILD_PROCESS_SINGLETON)) {
			moduleInstance = ModuleInstance.SYSTEM;
			cluster = ConfiguredSoftwareDistributions.SYSTEM_CLUSTER;
		} else if ((absoluteTag >= ClusterNode.BLACK_BOX_SINGLETON_START)
				&& (absoluteTag < ClusterNode.BLACK_BOX_SINGLETON_END)) {
			return nodesByRawTag.get(new RawTag(absoluteTag, tagVersion));
		} else {
			moduleInstance = executionModules.getModule(absoluteTag, entryIndex, streamType);
			if (moduleInstance == null)
				return null;
			if (moduleInstance.unit.isAnonymous)
				cluster = ConfiguredSoftwareDistributions.ANONYMOUS_CLUSTER;
			else
				cluster = ConfiguredSoftwareDistributions.getInstance().distributionsByUnit.get(moduleInstance.unit);
		}

		ClusterModule clusterModule = null;
		RawClusterData nodeData = nodesByCluster.get(cluster);
		if (nodeData == null) {
			toString();
		} else {
			clusterModule = nodeData.moduleList.getModule(moduleInstance.unit);
		}

		// ClusterModule clusterModule = nodesByCluster.get(cluster).moduleList.getModule(moduleInstance.unit);

		if (moduleInstance.unit.isAnonymous) {
			IndexedClusterNode node = nodesByRawTag.get(new RawTag(absoluteTag, tagVersion));
			return node;
		}

		long tag = (absoluteTag - moduleInstance.start);
		ClusterBasicBlock.Key key = new ClusterBasicBlock.Key(clusterModule, tag, tagVersion);
		RawClusterData clusterData = nodesByCluster.get(cluster);
		if (clusterData == null)
			return null;
		IndexedClusterNode node = clusterData.getNode(key);
		return node;
	}

	private void writeNodes() throws IOException {
		for (AutonomousSoftwareDistribution cluster : nodesByCluster.keySet()) {
			ClusterDataWriter<IndexedClusterNode> writer = graphWriters.getWriter(cluster);
			for (IndexedClusterNode node : nodesByCluster.get(cluster).getSortedNodeList()) {
				writer.writeNode(node);
			}
		}
	}

	private void writeEdges() throws IOException {
		for (Map.Entry<AutonomousSoftwareDistribution, Map<RawEdge, RawEdge>> clusterEdgeList : edgesByCluster
				.entrySet()) {
			// List<RawEdge> orderedEdges = new ArrayList<RawEdge>(clusterEdgeList.getValue().values());
			// Collections.sort(orderedEdges, RawEdge.EdgeIndexSorter.INSTANCE);
			int edgeIndex = 0;
			ClusterDataWriter<IndexedClusterNode> writer = graphWriters.getWriter(clusterEdgeList.getKey());
			for (RawEdge edge : clusterEdgeList.getValue().values()) {
				edge.setEdgeIndex(edgeIndex++);
				writer.writeEdge(edge);
			}
		}
	}

	private void writeMetadata() throws IOException {
		UnexpectedIndirectBranches uibsMain = null;
		Set<RawSuspiciousGencodeEntry> sgesMain = null;
		Set<RawSuspiciousSystemCall> sscsMain = null;
		UUID executionId = UUID.randomUUID();
		for (AutonomousSoftwareDistribution cluster : metadataClusters) {
			UnexpectedIndirectBranches uibs = uibsByCluster.get(cluster);
			Set<RawSuspiciousGencodeEntry> sges = sgesByCluster.get(cluster);
			Set<RawSuspiciousSystemCall> sscs = sscsByCluster.get(cluster);
			if (cluster == mainCluster) {
				uibsMain = uibs;
				sgesMain = sges;
				sscsMain = sscs;
				continue;
			}

			ClusterDataWriter<IndexedClusterNode> writer = graphWriters.getWriter(cluster);
			writer.writeMetadataHeader(false);
			writer.writeSequenceMetadataHeader(1, true);
			Collection<RawUnexpectedIndirectBranch> uibsSorted = null;
			if (uibs != null)
				uibsSorted = uibs.sortAndMerge();
			writer.writeExecutionMetadataHeader(executionId, (uibsSorted == null) ? 0 : uibsSorted.size(), 0,
					(sscs == null) ? 0 : sscs.size(), (sges == null) ? 0 : sges.size());
			if (uibsSorted != null) {
				for (RawUnexpectedIndirectBranch uib : uibsSorted)
					writer.writeUIB(uib.getClusterEdgeIndex(), uib.isAdmitted(), uib.getTraversalCount(),
							uib.getInstanceCount());
			}
			if (sscs != null) {
				for (RawSuspiciousSystemCall ssc : sscs)
					writer.writeSSC(ssc.sysnum, ssc.exitEdge.getEdgeIndex());
			}
			if (sges != null) {
				for (RawSuspiciousGencodeEntry sge : sges)
					writer.writeSGE(sge.clusterEdge.getEdgeIndex(), sge.uibCount, sge.suibCount);
			}
		}
		if (mainCluster != null) {
			ClusterDataWriter<IndexedClusterNode> writer = graphWriters.getWriter(mainCluster);
			// if (writer == null)
			// writer = graphWriters.createMetadataWriter(mainCluster);
			writer.writeMetadataHeader(true);
			writer.writeSequenceMetadataHeader(1, true);
			Collection<RawUnexpectedIndirectBranch> uibsSorted = null;
			if (uibsMain != null) {
				uibsSorted = uibsMain.sortAndMerge();
			}
			writer.writeExecutionMetadataHeader(executionId, uibsSorted == null ? 0 : uibsSorted.size(),
					uibIntervals.size(), (sscsMain == null) ? 0 : sscsMain.size(),
					(sgesMain == null) ? 0 : sgesMain.size());
			if (uibsSorted != null) {
				for (RawUnexpectedIndirectBranch uib : uibsSorted)
					writer.writeUIB(uib.getClusterEdgeIndex(), uib.isAdmitted(), uib.getTraversalCount(),
							uib.getInstanceCount());
			}
			for (RawUnexpectedIndirectBranchInterval interval : uibIntervals.values()) {
				writer.writeUIBInterval(interval.key.type.id, interval.key.span, interval.count,
						interval.maxConsecutive);
			}
			if (sscsMain != null) {
				for (RawSuspiciousSystemCall ssc : sscsMain)
					writer.writeSSC(ssc.sysnum, ssc.exitEdge.getEdgeIndex());
			}
			if (sgesMain != null) {
				for (RawSuspiciousGencodeEntry sge : sgesMain)
					writer.writeSGE(sge.clusterEdge.getEdgeIndex(), sge.uibCount, sge.suibCount);
			}
		} else {
			Log.log("Warning: main cluster not found!");
		}
	}

	private void writeModules() throws IOException {
		for (AutonomousSoftwareDistribution cluster : nodesByCluster.keySet()) {
			ClusterDataWriter<IndexedClusterNode> output = graphWriters.getWriter(cluster);
			if (output != null)
				output.writeModules();
		}
	}

	private RawGraphEntry.Writer<?> createWriter(LittleEndianOutputStream output, int recordSize) {
		switch (recordSize) {
			case 1:
				return new RawGraphEntry.OneWordWriter(output);
			case 2:
				return new RawGraphEntry.TwoWordWriter(output);
			case 3:
				return new RawGraphEntry.ThreeWordWriter(output);
			default:
				throw new IllegalArgumentException(String.format("The %s only supports records of size 2 or 3!",
						getClass().getSimpleName()));
		}
	}

	// this can be used to remove duplicates in block-hash and pair-hash files
	/**
	 * <pre>
	private void packRawFile(String filename) {
		Set<RawGraphEntry> records = new HashSet<RawGraphEntry>();

		LittleEndianInputStream input = new LittleEndianInputStream(new File(filename));
		RawGraphEntry.Factory factory = createFactory(input);

		while (factory.hasMoreEntries()) {
			records.add(factory.createEntry());
		}

		File outputFile = new File(filename + ".pack");
		LittleEndianOutputStream output = new LittleEndianOutputStream(outputFile);
		RawGraphEntry.Writer writer = createWriter(output);
		for (RawGraphEntry record : records) {
			writer.writeRecord(record);
		}
		writer.flush();
		records.clear();
	}
	 */

	public static void main(String[] args) {
		RawGraphTransformer packer = new RawGraphTransformer(new ArgumentStack(args));
		packer.run();
	}
}
