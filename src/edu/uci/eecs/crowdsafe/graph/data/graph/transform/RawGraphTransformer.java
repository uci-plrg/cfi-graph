package edu.uci.eecs.crowdsafe.graph.data.graph.transform;

import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
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
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModuleSet;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.anonymous.ApplicationAnonymousGraphs;
import edu.uci.eecs.crowdsafe.graph.data.graph.execution.ModuleInstance;
import edu.uci.eecs.crowdsafe.graph.data.graph.execution.ProcessExecutionGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.execution.ProcessExecutionModuleSet;
import edu.uci.eecs.crowdsafe.graph.data.graph.execution.loader.ProcessModuleLoader;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleBasicBlock;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleBoundaryNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.writer.AnonymousGraphWriter;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.writer.ModuleDataWriter;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.writer.ModuleGraphWriter;
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
				RawUnexpectedIndirectBranch match = uibsByEdgeIndex.get(uib.getModuleEdgeIndex());
				if (match == null) {
					uibsByEdgeIndex.put(uib.getModuleEdgeIndex(), uib);
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
	private static final OptionArgumentMap.BooleanOption unitModuleOption = OptionArgumentMap.createBooleanOption('u',
			true);

	private final ArgumentStack args;

	private final ProcessModuleLoader executionModuleLoader = new ProcessModuleLoader();

	// transitory per run:

	private File outputDir = null;
	private ExecutionTraceDataSource dataSource = null;
	private ProcessExecutionModuleSet executionModules = null;
	private ModuleDataWriter.Directory graphWriters = null;
	private final Map<ApplicationModule, RawModuleData> nodesByModule = new HashMap<ApplicationModule, RawModuleData>();
	/* using Map<RawEdge,RawEdge> to facilitate lookup */
	private final Map<ApplicationModule, Map<RawEdge, RawEdge>> edgesByModule = new HashMap<ApplicationModule, Map<RawEdge, RawEdge>>();

	private final Set<ApplicationModule> metadataModules = new HashSet<ApplicationModule>();
	private final Map<ApplicationModule, UnexpectedIndirectBranches> uibsByModule = new HashMap<ApplicationModule, UnexpectedIndirectBranches>();
	private final Map<ApplicationModule, Set<RawSuspiciousGencodeEntry>> sgesByModule = new HashMap<ApplicationModule, Set<RawSuspiciousGencodeEntry>>();
	private final Map<ApplicationModule, Set<RawSuspiciousSystemCall>> sscsByModule = new HashMap<ApplicationModule, Set<RawSuspiciousSystemCall>>();

	private final Map<RawTag, IndexedModuleNode> nodesByRawTag = new HashMap<RawTag, IndexedModuleNode>();
	private final Map<RawTag, Integer> fakeAnonymousModuleTags = new HashMap<RawTag, Integer>();
	private int fakeAnonymousTagIndex = ModuleNode.FAKE_ANONYMOUS_TAG_START;
	private final Map<Long, IndexedModuleNode> syscallSingletons = new HashMap<Long, IndexedModuleNode>();
	private final Map<ApplicationModule, ModuleNode<?>> jitSingletons = new HashMap<ApplicationModule, ModuleNode<?>>();

	private int mainModuleStartAddress;
	private ApplicationModule mainModule;
	private final Map<RawUnexpectedIndirectBranchInterval.Key, RawUnexpectedIndirectBranchInterval> uibIntervals = new HashMap<RawUnexpectedIndirectBranchInterval.Key, RawUnexpectedIndirectBranchInterval>();
	private final LinkedList<RawUnexpectedIndirectBranch> intraModuleUIBQueue = new LinkedList<RawUnexpectedIndirectBranch>();
	private final LinkedList<RawUnexpectedIndirectBranch> crossModuleUIBQueue = new LinkedList<RawUnexpectedIndirectBranch>();
	private final LinkedList<RawSuspiciousGencodeEntry> gencodeEntryQueue = new LinkedList<RawSuspiciousGencodeEntry>();
	private final LinkedList<RawSuspiciousSystemCall> intraModuleSuspiciousSyscallQueue = new LinkedList<RawSuspiciousSystemCall>();
	private final LinkedList<RawSuspiciousSystemCall> crossModuleSuspiciousSyscallQueue = new LinkedList<RawSuspiciousSystemCall>();

	public RawGraphTransformer(ArgumentStack args) {
		this.args = args;

		OptionArgumentMap.populateOptions(args, verboseOption, logOption, inputOption, outputOption, unitModuleOption);
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

			ApplicationModuleSet.initialize();

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
						outputDir = new File(runDir, "module");
					} else {
						outputDir = new File(outputOption.getValue());
					}
					outputDir.mkdirs();
					graphWriters = new ModuleDataWriter.Directory(outputDir, dataSource.getProcessName());
					Log.log("Transform %s to %s", runDir.getAbsolutePath(), outputDir.getAbsolutePath());
					if (dataSource.hasStreamType(ExecutionTraceStreamType.XHASH)) {
						ApplicationModuleSet.getInstance().loadCrossModuleLabels(
								dataSource.getDataInputStream(ExecutionTraceStreamType.XHASH));
						Files.copy(dataSource.getDataInputStream(ExecutionTraceStreamType.XHASH),
								graphWriters.dataSink.getHashLabelPath());
					}
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
				nodesByModule.clear();
				edgesByModule.clear();
				nodesByRawTag.clear();
				fakeAnonymousModuleTags.clear();
				fakeAnonymousTagIndex = ModuleNode.FAKE_ANONYMOUS_TAG_START;
				syscallSingletons.clear();
				jitSingletons.clear();
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

		writeGraph();
		// writeNodes();
		// writeEdges();
		writeMetadata();
		graphWriters.flush();
	}

	private void loadMetadata(ExecutionTraceStreamType streamType) throws IOException {
		LittleEndianInputStream input = dataSource.getLittleEndianInputStream(streamType);
		if (input == null)
			return;

		long mainModuleStartAddress = input.readLong();
		ModuleInstance mainModuleInstance = executionModules.getModule(mainModuleStartAddress, 0,
				ExecutionTraceStreamType.GRAPH_NODE);
		mainModule = ApplicationModuleSet.getInstance().modulesByName.get(mainModuleInstance.name);
		graphWriters.establishModuleWriters(establishModuleData(mainModule));
		// establishEdgeSet(mainModule);
		Log.log("Main module is %s", mainModule);

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

		RawModuleData nodeData = establishModuleData(ModuleInstance.SYSTEM_MODULE);
		ModuleNode<?> node = new ModuleBasicBlock(ApplicationModule.SYSTEM_MODULE, ModuleNode.PROCESS_ENTRY_SINGLETON,
				0, ModuleNode.PROCESS_ENTRY_SINGLETON, MetaNodeType.SINGLETON);
		IndexedModuleNode nodeId = nodeData.addNode(node);
		RawTag rawTag = new RawTag(ModuleNode.PROCESS_ENTRY_SINGLETON, 0);
		fakeAnonymousModuleTags.put(rawTag, ModuleNode.PROCESS_ENTRY_SINGLETON);
		nodesByRawTag.put(rawTag, nodeId);
		graphWriters.establishModuleWriters(nodesByModule.get(ApplicationModule.SYSTEM_MODULE));

		node = new ModuleBasicBlock(ApplicationModule.SYSTEM_MODULE, ModuleNode.SYSTEM_SINGLETON, 0,
				ModuleNode.SYSTEM_SINGLETON, MetaNodeType.SINGLETON);
		nodeId = nodeData.addNode(node);
		rawTag = new RawTag(ModuleNode.SYSTEM_SINGLETON, 0);
		fakeAnonymousModuleTags.put(rawTag, ModuleNode.SYSTEM_SINGLETON);
		nodesByRawTag.put(rawTag, nodeId);

		node = new ModuleBasicBlock(ApplicationModule.SYSTEM_MODULE, ModuleNode.CHILD_PROCESS_SINGLETON, 0,
				ModuleNode.CHILD_PROCESS_SINGLETON, MetaNodeType.SINGLETON);
		nodeId = nodeData.addNode(node);
		rawTag = new RawTag(ModuleNode.CHILD_PROCESS_SINGLETON, 0);
		fakeAnonymousModuleTags.put(rawTag, ModuleNode.CHILD_PROCESS_SINGLETON);
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
				if ((absoluteTag == ModuleNode.PROCESS_ENTRY_SINGLETON) || (absoluteTag == ModuleNode.SYSTEM_SINGLETON)
						|| (absoluteTag == ModuleNode.CHILD_PROCESS_SINGLETON)) {
					moduleInstance = ModuleInstance.SYSTEM;
				} else if ((absoluteTag >= ModuleNode.JIT_SINGLETON_START)
						&& (absoluteTag < ModuleNode.JIT_SINGLETON_END)) {
					moduleInstance = ModuleInstance.ANONYMOUS;
				} else {
					throw new InvalidGraphException("Error: unknown singleton with tag 0x%x!", absoluteTag);
				}
			} else if ((absoluteTag >= ModuleNode.JIT_SINGLETON_START) // FIXME: temporary hack
					&& (absoluteTag < ModuleNode.JIT_SINGLETON_END)) {
				moduleInstance = ModuleInstance.ANONYMOUS;
			} else {
				moduleInstance = executionModules.getModule(absoluteTag, entryIndex, streamType);
			}
			if (moduleInstance == null) {
				Log.log("Error: cannot find the module for node 0x%x-v%d (type %s)", absoluteTag, tagVersion, nodeType);
				continue;
			}

			ApplicationModule module = ApplicationModuleSet.getInstance().modulesByName.get(moduleInstance.name);

			int relativeTag;
			ModuleBoundaryNode.HashLabel jitLabel = null;
			ApplicationModule jitOwner = null;
			boolean isNewJITSingleton = false;
			if (module.isAnonymous) {
				if ((absoluteTag == ModuleNode.PROCESS_ENTRY_SINGLETON) || (absoluteTag == ModuleNode.SYSTEM_SINGLETON)
						|| (absoluteTag == ModuleNode.CHILD_PROCESS_SINGLETON)) {
					module = ApplicationModule.SYSTEM_MODULE;
					moduleInstance = ModuleInstance.SYSTEM;
				} else {
					module = ApplicationModule.ANONYMOUS_MODULE;
					moduleInstance = ModuleInstance.ANONYMOUS;
					if (nodeType == MetaNodeType.SINGLETON) {
						if (!ApplicationModuleSet.getInstance().isToAnonymous(nodeEntry.second)) {
							new InvalidGraphException("Error: cannot find the owner of black box with entry 0x%x!",
									nodeEntry.second);
						}

						// TODO: removing extraneous nodes requires patching the data set for the whole
						// anonymous module
					}
				}

				RawTag lookup = new RawTag(absoluteTag, tagVersion);
				Integer tag = fakeAnonymousModuleTags.get(lookup);
				if (tag == null) {
					tag = fakeAnonymousTagIndex++;
					fakeAnonymousModuleTags.put(lookup, tag);
					Log.log("Mapping 0x%x-v%d => 0x%x for module %s (hash 0x%x)", absoluteTag, tagVersion, tag,
							moduleInstance.filename, nodeEntry.second);
				}
				relativeTag = tag;
			} else {
				relativeTag = (int) (absoluteTag - moduleInstance.start);
			}

			nodeData = establishModuleData(module);
			// moduleModule = establishModuleData(module).moduleList.establishModule(moduleInstance.unit);
			node = new ModuleBasicBlock(module, relativeTag, module.isAnonymous ? 0 : tagVersion, nodeEntry.second,
					nodeType);
			if (isNewJITSingleton)
				jitSingletons.put(jitOwner, node);
			nodeId = nodeData.addNode(node);

			if (module.isAnonymous)
				nodesByRawTag.put(new RawTag(absoluteTag, tagVersion), nodeId);
			else
				graphWriters.establishModuleWriters(nodesByModule.get(module));
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
			IndexedModuleNode fromNodeId = identifyNode(absoluteFromTag, fromTagVersion, entryIndex, streamType);

			EdgeType type = CrowdSafeTraceUtil.getTagEdgeType(edgeEntry.first);
			int ordinal = CrowdSafeTraceUtil.getEdgeOrdinal(edgeEntry.first);
			if (type == EdgeType.GENCODE_PERM) // hack
				ordinal = 3;
			else if (type == EdgeType.GENCODE_WRITE)
				ordinal = 4;

			long absoluteToTag = CrowdSafeTraceUtil.getTag(edgeEntry.second);
			int toTagVersion = CrowdSafeTraceUtil.getTagVersion(edgeEntry.second);
			IndexedModuleNode toNodeId = identifyNode(absoluteToTag, toTagVersion, entryIndex, streamType);

			// if (type == EdgeType.UNEXPECTED_RETURN)
			// Log.log("Loaded unexpected return from 0x%x to 0x%x", absoluteFromTag, absoluteToTag);

			if (fromNodeId == null) {
				if (toNodeId == null) {
					Log.log("Error: both nodes missing in edge (0x%x-v%d) -%s-%d-> (0x%x-v%d)", absoluteFromTag,
							fromTagVersion, type.code, ordinal, absoluteToTag, toTagVersion);
				} else {
					Log.log("Error in module %s: missing 'from' node: (0x%x-v%d) -%s-%d-> (0x%x-v%d)",
							toNodeId.module.filename, absoluteFromTag, fromTagVersion, type.code, ordinal,
							absoluteToTag, toTagVersion);
				}
				continue;
			}

			if (toNodeId == null) {
				Log.log("Error in module %s: missing 'to' node: (0x%x-v%d) -%s-%d-> (0x%x-v%d)",
						fromNodeId.module.filename, absoluteFromTag, fromTagVersion, type.code, ordinal, absoluteToTag,
						toTagVersion);
				continue;
			}

			if (type.isHighOrdinal(ordinal))
				Log.log("Warning: high ordinal in %s edge (0x%x-v%d) -%s-%d-> (0x%x-v%d)", fromNodeId.module.filename,
						absoluteFromTag, fromTagVersion, type.code, ordinal, absoluteToTag, toTagVersion);

			if (fromNodeId.module == toNodeId.module) {
				RawEdge edge;
				if (fromNodeId.module.isAnonymous)
					edge = addEdge(ApplicationModule.ANONYMOUS_MODULE, fromNodeId, toNodeId, type, ordinal);
				else
					edge = addEdge(fromNodeId.module, fromNodeId, toNodeId, type, ordinal);

				RawUnexpectedIndirectBranch uib = null;
				while (!intraModuleUIBQueue.isEmpty() && (intraModuleUIBQueue.peekFirst().rawEdgeIndex == entryIndex))
					uib = intraModuleUIBQueue.removeFirst();
				while (!intraModuleSuspiciousSyscallQueue.isEmpty()
						&& intraModuleSuspiciousSyscallQueue.peekFirst().edgeIndex == entryIndex) {
					RawSuspiciousSystemCall ssc = intraModuleSuspiciousSyscallQueue.removeFirst();
					ssc.entryEdge = ssc.exitEdge = edge;
					establishSSCs(fromNodeId.module).add(ssc);

					// Log.log("SSC: raising edge: %s (index %d); next IM SSC edge index is %d",
					// edge,
					// ssc.edgeIndex,
					// intraModuleSuspiciousSyscallQueue.isEmpty() ? 0 : intraModuleSuspiciousSyscallQueue
					// .peekFirst().edgeIndex);
				}

				if (uib != null) {
					uib.moduleEdge = edge;
					establishUIBs(fromNodeId.module).add(uib);
				}
			} else {
				Log.log("Error! Intra-module edge from %s to %s crosses a module boundary (%s to %s)!",
						fromNodeId.node, toNodeId.node, fromNodeId.module, toNodeId.module);
				// throw new IllegalStateException(String.format(
				// "Intra-module edge from %s to %s crosses a module boundary!", fromNodeId.node, toNodeId.node));
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
			IndexedModuleNode fromNodeId = identifyNode(absoluteFromTag, fromTagVersion, entryIndex, streamType);

			long absoluteToTag = CrowdSafeTraceUtil.getTag(edgeEntry.second);
			int toTagVersion = CrowdSafeTraceUtil.getTagVersion(edgeEntry.second);
			IndexedModuleNode toNodeId = identifyNode(absoluteToTag, toTagVersion, entryIndex, streamType);

			long hash = edgeEntry.third;

			if (fromNodeId == null) {
				if (toNodeId == null) {
					Log.log("Error: both nodes missing in cross-module %s edge 0x%x-v%d -> 0x%x-v%d", type.code,
							absoluteFromTag, fromTagVersion, absoluteToTag, toTagVersion);
				} else {
					Log.log("Error: missing 'from' node 0x%x-v%d in cross-module %s edge to %s(0x%x-v%d) ",
							absoluteFromTag, fromTagVersion, type.code, toNodeId.module.filename, absoluteToTag,
							toTagVersion);
				}
				continue;
			}

			if (toNodeId == null) {
				Log.log("Error: missing 'to' node 0x%x-v%d in cross-module %s edge from %s 0x%x-v%d", absoluteToTag,
						toTagVersion, type.code, fromNodeId.module.filename, absoluteFromTag, fromTagVersion);
				continue;
			}

			if (type.isHighOrdinal(ordinal))
				Log.log("Warning: high ordinal in cross-module edge %s(0x%x-v%d) -%s-%d-> %s(0x%x-v%d)",
						fromNodeId.module.filename, absoluteFromTag, fromTagVersion, type.code, ordinal,
						toNodeId.module.filename, absoluteToTag, toTagVersion);

			if (fromNodeId.module == toNodeId.module) { // TODO: why is this possible? what about UIB?
				addEdge(fromNodeId.module, fromNodeId, toNodeId, type, ordinal);
			} else if (fromNodeId.module.isAnonymous && toNodeId.module.isAnonymous) {
				addEdge(ApplicationModule.ANONYMOUS_MODULE, fromNodeId, toNodeId, type, ordinal);
			} else {
				ModuleBoundaryNode entry = new ModuleBoundaryNode(hash, MetaNodeType.MODULE_ENTRY);
				IndexedModuleNode entryId = nodesByModule.get(toNodeId.module).addNode(entry);
				RawEdge rawEntry = addEdge(toNodeId.module, entryId, toNodeId, type, type.getModuleEntryOrdinal());

				ModuleBoundaryNode exit = new ModuleBoundaryNode(hash, MetaNodeType.MODULE_EXIT);
				IndexedModuleNode exitId = nodesByModule.get(fromNodeId.module).addNode(exit);
				RawEdge rawExit = addEdge(fromNodeId.module, fromNodeId, exitId, type, ordinal);

				if (toNodeId.module == ApplicationModule.SYSTEM_MODULE)
					Log.log("Creating an edge into the system module: %s", rawExit);

				RawUnexpectedIndirectBranch uib = null;
				while ((!crossModuleUIBQueue.isEmpty()) && (crossModuleUIBQueue.peekFirst().rawEdgeIndex == entryIndex))
					uib = crossModuleUIBQueue.removeFirst();

				if (uib != null) {
					uib.moduleEdge = rawEntry;
					establishUIBs(toNodeId.module).add(uib);

					uib = new RawUnexpectedIndirectBranch(uib);
					uib.moduleEdge = rawExit;
					establishUIBs(fromNodeId.module).add(uib);
				}

				if ((!gencodeEntryQueue.isEmpty()) && (gencodeEntryQueue.peekFirst().edgeIndex == entryIndex)) {
					RawSuspiciousGencodeEntry gencodeEntry = gencodeEntryQueue.removeFirst();
					gencodeEntry.edge = rawExit;
					establishSGEs(fromNodeId.module).add(gencodeEntry);
				}
				while (!crossModuleSuspiciousSyscallQueue.isEmpty()
						&& crossModuleSuspiciousSyscallQueue.peekFirst().edgeIndex == entryIndex) {
					RawSuspiciousSystemCall ssc = crossModuleSuspiciousSyscallQueue.removeFirst();
					ssc.entryEdge = rawEntry;
					ssc.exitEdge = rawExit;
					establishSSCs(fromNodeId.module).add(ssc);

					// Log.log("SSC: raising edges: [%s] and [%s] (index %d); next CM SSC edge index: %d", rawEntry,
					// rawExit, ssc.edgeIndex, crossModuleSuspiciousSyscallQueue.isEmpty() ? 0
					// : crossModuleSuspiciousSyscallQueue.peekFirst().edgeIndex);
				}
			}
		}
	}

	private RawModuleData establishModuleData(ApplicationModule module) {
		RawModuleData data = nodesByModule.get(module);
		if (data == null) {
			data = new RawModuleData(module);
			nodesByModule.put(module, data);
		}
		return data;
	}

	private RawEdge addEdge(ApplicationModule module, IndexedModuleNode fromNode, IndexedModuleNode toNode,
			EdgeType type, int ordinal) {
		Map<RawEdge, RawEdge> moduleEdges = establishEdgeSet(module);
		RawEdge edge = new RawEdge(fromNode, toNode, type, ordinal);
		RawEdge existing = moduleEdges.get(edge);
		if (existing == null) {
			moduleEdges.put(edge, edge);
			return edge;
		} else {
			return existing;
		}
	}

	private Map<RawEdge, RawEdge> establishEdgeSet(ApplicationModule module) {
		Map<RawEdge, RawEdge> set = edgesByModule.get(module);
		if (set == null) {
			set = new HashMap<RawEdge, RawEdge>();
			edgesByModule.put(module, set);
		}
		return set;
	}

	private UnexpectedIndirectBranches establishUIBs(ApplicationModule module) {
		UnexpectedIndirectBranches uibs = uibsByModule.get(module);
		if (uibs == null) {
			uibs = new UnexpectedIndirectBranches();
			uibsByModule.put(module, uibs);
			metadataModules.add(module);
		}
		return uibs;
	}

	private Set<RawSuspiciousGencodeEntry> establishSGEs(ApplicationModule module) {
		Set<RawSuspiciousGencodeEntry> sges = sgesByModule.get(module);
		if (sges == null) {
			sges = new HashSet<RawSuspiciousGencodeEntry>();
			sgesByModule.put(module, sges);
			metadataModules.add(module);
		}
		return sges;
	}

	private Set<RawSuspiciousSystemCall> establishSSCs(ApplicationModule module) {
		Set<RawSuspiciousSystemCall> sscs = sscsByModule.get(module);
		if (sscs == null) {
			sscs = new HashSet<RawSuspiciousSystemCall>();
			sscsByModule.put(module, sscs);
			metadataModules.add(module);
		}
		return sscs;
	}

	private IndexedModuleNode identifyNode(long absoluteTag, int tagVersion, long entryIndex,
			ExecutionTraceStreamType streamType) {
		if ((absoluteTag >= ModuleNode.SYSCALL_SINGLETON_START) && (absoluteTag < ModuleNode.SYSCALL_SINGLETON_END)) {
			IndexedModuleNode nodeId = syscallSingletons.get(absoluteTag);
			if (nodeId == null) {
				RawModuleData nodeData = establishModuleData(ApplicationModule.SYSTEM_MODULE);
				ModuleBasicBlock node = new ModuleBasicBlock(ApplicationModule.SYSTEM_MODULE, absoluteTag, 0, 0L,
						MetaNodeType.SINGLETON);
				nodeId = nodeData.addNode(node);
				syscallSingletons.put(absoluteTag, nodeId);
			}
			return nodeId;
		}

		ModuleInstance moduleInstance;
		ApplicationModule module;
		if ((absoluteTag == ModuleNode.PROCESS_ENTRY_SINGLETON) || (absoluteTag == ModuleNode.SYSTEM_SINGLETON)
				|| (absoluteTag == ModuleNode.CHILD_PROCESS_SINGLETON)) {
			moduleInstance = ModuleInstance.SYSTEM;
			module = ApplicationModule.SYSTEM_MODULE;
		} else if ((absoluteTag >= ModuleNode.JIT_SINGLETON_START) && (absoluteTag < ModuleNode.JIT_SINGLETON_END)) {
			return nodesByRawTag.get(new RawTag(absoluteTag, tagVersion));
		} else {
			moduleInstance = executionModules.getModule(absoluteTag, entryIndex, streamType);
			if (moduleInstance == null)
				return null;
			if (moduleInstance.isAnonymous)
				module = ApplicationModule.ANONYMOUS_MODULE;
			else
				module = ApplicationModuleSet.getInstance().modulesByName.get(moduleInstance.name);
		}

		// ApplicationModule moduleModule = nodesByModule.get(module).moduleList.getModule(moduleInstance.unit);

		if (moduleInstance.isAnonymous) {
			IndexedModuleNode node = nodesByRawTag.get(new RawTag(absoluteTag, tagVersion));
			return node;
		}

		long tag = (absoluteTag - moduleInstance.start);
		ModuleBasicBlock.Key key = new ModuleBasicBlock.Key(module, tag, tagVersion);
		RawModuleData moduleData = nodesByModule.get(module);
		if (moduleData == null)
			return null;
		IndexedModuleNode node = moduleData.getNode(key);
		return node;
	}

	private void writeGraph() throws IOException {
		ModuleNode<?> transformedNode, fromNode, toNode;
		Edge<ModuleNode<?>> transformedEdge;
		List<ModuleNode<?>> transformedNodes = new ArrayList<ModuleNode<?>>();
		String name = "Raw graph loaded from " + dataSource.getDirectory().getAbsolutePath();

		for (ApplicationModule module : nodesByModule.keySet()) {
			ModuleGraph<ModuleNode<?>> graph = new ModuleGraph<ModuleNode<?>>(name, module);
			transformedNodes.clear();

			for (IndexedModuleNode node : nodesByModule.get(module).getSortedNodeList()) {
				switch (node.getType()) {
					case MODULE_ENTRY:
					case MODULE_EXIT:
						transformedNode = new ModuleBoundaryNode(node.getHash(), node.getType());
						break;
					default:
						transformedNode = new ModuleBasicBlock(module, node.getRelativeTag(), node.getInstanceId(),
								node.getHash(), node.getType());
				}
				transformedNodes.add(transformedNode);
				graph.addNode(transformedNode);
			}

			Map<RawEdge, RawEdge> edgeList = edgesByModule.get(module);
			Map<Edge<ModuleNode<?>>, RawEdge> transformedEdgeMap = new HashMap<Edge<ModuleNode<?>>, RawEdge>();
			for (RawEdge edge : edgeList.values()) {
				fromNode = transformedNodes.get(edge.getFromNode().index);
				toNode = transformedNodes.get(edge.getToNode().index);
				transformedEdge = new Edge<ModuleNode<?>>(fromNode, toNode, edge.getEdgeType(), edge.getOrdinal());
				transformedEdgeMap.put(transformedEdge, edge);
				fromNode.addOutgoingEdge(transformedEdge);
				toNode.addIncomingEdge(transformedEdge);
			}

			if (module == ApplicationModule.ANONYMOUS_MODULE) {
				ApplicationAnonymousGraphs anonymousGraphs = new ApplicationAnonymousGraphs();
				anonymousGraphs.inflate(graph);
				AnonymousGraphWriter anonymousWriter = new AnonymousGraphWriter(anonymousGraphs);
				graphWriters.establishModuleWriters(anonymousWriter);
				anonymousWriter.initialize(graphWriters.dataSink);
				anonymousWriter.writeGraph();
				// not setting edge indexes b/c there's no edge-specific metadata in the anonymous module
			} else {
				ModuleGraphWriter writer = new ModuleGraphWriter(graph, graphWriters.dataSink);
				Map<Edge<ModuleNode<?>>, Integer> edgeIndexMap = writer.writeGraphBody();

				// update each edge with the index at which it was written
				for (Map.Entry<Edge<ModuleNode<?>>, Integer> edgeIndex : edgeIndexMap.entrySet()) {
					transformedEdgeMap.get(edgeIndex.getKey()).setEdgeIndex(edgeIndex.getValue());
				}
			}
		}
	}

	private void writeNodes() throws IOException {
		for (ApplicationModule module : nodesByModule.keySet()) {
			ModuleDataWriter writer = graphWriters.getWriter(module);
			for (IndexedModuleNode node : nodesByModule.get(module).getSortedNodeList()) {
				writer.writeNode(node);
			}
		}
	}

	private void writeEdges() throws IOException {
		for (Map.Entry<ApplicationModule, Map<RawEdge, RawEdge>> moduleEdgeList : edgesByModule.entrySet()) {
			// List<RawEdge> orderedEdges = new ArrayList<RawEdge>(moduleEdgeList.getValue().values());
			// Collections.sort(orderedEdges, RawEdge.EdgeIndexSorter.INSTANCE);
			int edgeIndex = 0;
			ModuleDataWriter writer = graphWriters.getWriter(moduleEdgeList.getKey());
			for (RawEdge edge : moduleEdgeList.getValue().values()) {
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
		for (ApplicationModule module : metadataModules) {
			UnexpectedIndirectBranches uibs = uibsByModule.get(module);
			Set<RawSuspiciousGencodeEntry> sges = sgesByModule.get(module);
			Set<RawSuspiciousSystemCall> sscs = sscsByModule.get(module);
			if (module == mainModule) {
				uibsMain = uibs;
				sgesMain = sges;
				sscsMain = sscs;
				continue;
			}

			ModuleDataWriter writer = graphWriters.getWriter(module);
			writer.writeMetadataHeader(false);
			writer.writeSequenceMetadataHeader(1, true);
			Collection<RawUnexpectedIndirectBranch> uibsSorted = null;
			if (uibs != null)
				uibsSorted = uibs.sortAndMerge();
			writer.writeExecutionMetadataHeader(executionId, (uibsSorted == null) ? 0 : uibsSorted.size(), 0,
					(sscs == null) ? 0 : sscs.size(), (sges == null) ? 0 : sges.size());
			if (uibsSorted != null) {
				for (RawUnexpectedIndirectBranch uib : uibsSorted)
					writer.writeUIB(uib.getModuleEdgeIndex(), uib.isAdmitted(), uib.getTraversalCount(),
							uib.getInstanceCount());
			}
			if (sscs != null) {
				for (RawSuspiciousSystemCall ssc : sscs)
					writer.writeSSC(ssc.sysnum, ssc.exitEdge.getEdgeIndex());
			}
			if (sges != null) {
				for (RawSuspiciousGencodeEntry sge : sges)
					writer.writeSGE(sge.edge.getEdgeIndex(), sge.uibCount, sge.suibCount);
			}
		}
		if (mainModule != null) {
			ModuleDataWriter writer = graphWriters.getWriter(mainModule);
			// if (writer == null)
			// writer = graphWriters.createMetadataWriter(mainModule);
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
					writer.writeUIB(uib.getModuleEdgeIndex(), uib.isAdmitted(), uib.getTraversalCount(),
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
					writer.writeSGE(sge.edge.getEdgeIndex(), sge.uibCount, sge.suibCount);
			}
		} else {
			Log.log("Warning: main module not found!");
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
