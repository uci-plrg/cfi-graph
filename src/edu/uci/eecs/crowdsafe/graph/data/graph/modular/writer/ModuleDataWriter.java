package edu.uci.eecs.crowdsafe.graph.data.graph.modular.writer;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianOutputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.NodeIdentifier;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.EvaluationType;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleMetadata;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleMetadataExecution;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleMetadataSequence;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleSGE;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleSSC;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleUIB;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleUIBInterval;
import edu.uci.eecs.crowdsafe.graph.io.modular.ModularTraceDataSink;
import edu.uci.eecs.crowdsafe.graph.io.modular.ModularTraceDirectory;
import edu.uci.eecs.crowdsafe.graph.io.modular.ModularTraceStreamType;

public class ModuleDataWriter {

	public interface ModularData {
		ApplicationModule getModule();

		int getNodeIndex(NodeIdentifier node);
	}

	public interface Edge {
		NodeIdentifier getFromNode();

		NodeIdentifier getToNode();

		EdgeType getEdgeType();

		int getOrdinal();

		boolean isModuleEntry();

		boolean isModuleExit();
	}

	public static class Directory {
		public final ModularTraceDataSink dataSink;
		private final String filenameFormat;

		private final Map<ApplicationModule, ModuleDataWriter> outputsByModule = new HashMap<ApplicationModule, ModuleDataWriter>();

		private Directory(String processName, ModularTraceDataSink dataSink) {
			filenameFormat = String.format("%s.%%s.%%s.%%s", processName);
			this.dataSink = dataSink;
		}

		public Directory(File directory, String processName) {
			this(processName, new ModularTraceDirectory(directory));
		}

		public Directory(File directory, String processName, Set<ModularTraceStreamType> requiredStreamTypes,
				Set<ModularTraceStreamType> optionalStreamTypes) {
			this(processName, new ModularTraceDirectory(directory, requiredStreamTypes, optionalStreamTypes));
		}

		public void establishModuleWriters(ModularData data) throws IOException {
			ModuleDataWriter writer = getWriter(data.getModule());
			if (writer == null) {
				dataSink.addModule(data.getModule(), filenameFormat);
				writer = new ModuleDataWriter(data, dataSink);
				outputsByModule.put(data.getModule(), writer);
			}
		}

		public ModuleDataWriter getWriter(ApplicationModule module) {
			return outputsByModule.get(module);
		}

		public void flush() throws IOException {
			for (ModuleDataWriter output : outputsByModule.values()) {
				output.flush();
			}
		}
	}

	final LittleEndianOutputStream nodeStream;
	final LittleEndianOutputStream edgeStream;
	final LittleEndianOutputStream metaStream;

	private final ModularData data;

	ModuleDataWriter(ModularData data, ModularTraceDataSink dataSink) throws IOException {
		this.data = data;

		nodeStream = dataSink.getLittleEndianOutputStream(data.getModule(), ModularTraceStreamType.GRAPH_NODE);
		edgeStream = dataSink.getLittleEndianOutputStream(data.getModule(), ModularTraceStreamType.GRAPH_EDGE);
		metaStream = dataSink.getLittleEndianOutputStream(data.getModule(), ModularTraceStreamType.META);
	}

	public void writeNode(NodeIdentifier node) throws IOException {
		long word = 0; // data.getModuleIndex(node.getModule()); // obsolete
		word |= ((long) node.getRelativeTag() & 0xffffffffL) << 0x10;
		word |= ((long) node.getInstanceId()) << 0x30;
		word |= ((long) node.getType().ordinal()) << 0x38;
		nodeStream.writeLong(word);
		nodeStream.writeLong(node.getHash());
	}

	public void writeEdge(Edge edge) throws IOException {
		long word = (long) data.getNodeIndex(edge.getFromNode());
		word |= ((long) data.getNodeIndex(edge.getToNode())) << 0x1c;
		word |= ((long) edge.getEdgeType().ordinal()) << 0x38;
		word |= ((long) edge.getOrdinal()) << 0x3c;
		edgeStream.writeLong(word);
	}

	public void writeMetadataHeader(boolean isMain) throws IOException {
		metaStream.writeLong(isMain ? 1L : 0L); // MAIN
	}

	public void writeSequenceMetadataHeader(int executionCount, boolean isRoot) throws IOException {
		long header = (((long) executionCount) << 0x20);
		if (isRoot)
			header |= 1;
		metaStream.writeLong(header);
	}

	public void writeExecutionMetadataHeader(UUID executionId, int uibCount, int intervalCount, int sscCount,
			int sgeCount) throws IOException {
		long entryCounts = uibCount;
		entryCounts |= (((long) intervalCount) << 0x20);
		metaStream.writeLong(entryCounts);

		entryCounts = sscCount;
		entryCounts |= (((long) sgeCount) << 0x20);
		metaStream.writeLong(entryCounts);

		metaStream.writeLong(executionId.getMostSignificantBits());
		metaStream.writeLong(executionId.getLeastSignificantBits());
	}

	public void writeMetadataHistory(ModuleMetadata metadata,
			Map<edu.uci.eecs.crowdsafe.graph.data.graph.Edge<ModuleNode<?>>, Integer> edgeIndexMap) throws IOException {
		writeMetadataHeader(metadata.isMain());
		for (ModuleMetadataSequence sequence : metadata.sequences.values()) {
			writeSequenceMetadataHeader(sequence.executions.size(), sequence.isRoot());
			for (ModuleMetadataExecution execution : sequence.executions) {
				writeExecutionMetadataHeader(execution.id, execution.uibs.size(), execution.getIntervalCount(),
						execution.getSuspiciousSyscallCount(), execution.getSuspiciousGencodeEntryCount());
				for (ModuleUIB uib : execution.uibs) {
					writeUIB(edgeIndexMap.get(uib.edge), uib.isAdmitted, uib.traversalCount, uib.instanceCount);
				}
				for (EvaluationType type : EvaluationType.values()) {
					for (ModuleUIBInterval interval : execution.getIntervals(type)) {
						writeUIBInterval(interval.type.id, interval.span, interval.count, interval.maxConsecutive);
					}
				}
				for (ModuleSSC ssc : execution.sscs) {
					if (edgeIndexMap.get(ssc.suspicionRaisingEdge) == null)
						Log.error("Failed to locate the index for the supicion-raising edge %s",
								ssc.suspicionRaisingEdge);
					else
						writeSSC(ssc.sysnum, edgeIndexMap.get(ssc.suspicionRaisingEdge));
					// old format: ssc.uibCount, ssc.suibCount);
				}
				for (ModuleSGE sge : execution.sges) {
					Integer edgeIndex = edgeIndexMap.get(sge.edge);
					if (edgeIndex == null) {
						Log.log("Warning: cannot find the edge for a suspicious gencode entry (uib count %d, suib count %d)",
								sge.uibCount, sge.suibCount);
						edgeIndex = 0;
					}
					writeSGE(edgeIndex, sge.uibCount, sge.suibCount);
				}
			}
		}
	}

	public void writeUIB(int edgeIndex, boolean isAdmitted, int traversalCount, int instanceCount) throws IOException {
		long word = edgeIndex;
		word |= (((long) (instanceCount & 0xfff)) << 0x14);
		word |= (((long) traversalCount) << 0x20);
		if (isAdmitted)
			word |= 0x8000000000000000L;
		metaStream.writeLong(word);

		// Log.log("Wrote UIB for edge #%d: %d traversals\n", edgeIndex, traversalCount);
	}

	public void writeSSC(int sysnum, int edgeIndex) throws IOException {
		long word = sysnum;
		word |= (((long) (edgeIndex & 0xfffff)) << 0x10);
		metaStream.writeLong(word);
	}

	public void writeSGE(int edgeIndex, int uibCount, int suibCount) throws IOException {
		long word = edgeIndex;
		word |= (((long) (uibCount & 0xffff)) << 0x14);
		word |= (((long) (suibCount & 0xffff)) << 0x24);
		metaStream.writeLong(word);
	}

	public void writeUIBInterval(int typeId, int span, int count, int maxConsecutive) throws IOException {
		long word = typeId;
		word |= (((long) span) << 8);
		word |= (maxConsecutive << 0x10);
		word |= (((long) count) << 0x20);
		metaStream.writeLong(word);
	}

	public void flush() throws IOException {
		nodeStream.flush();
		edgeStream.flush();
		metaStream.flush();
	}

	public void close() throws IOException {
		nodeStream.close();
		edgeStream.close();
		metaStream.close();
	}
}
