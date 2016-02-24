package edu.uci.eecs.crowdsafe.graph.data.graph.modular.writer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianOutputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.NodeIdentifier;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleBoundaryNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleMetadata;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleMetadataExecution;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleMetadataSequence;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleSGE;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleSSC;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleUIB;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.ModuleUIBInterval;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata.EvaluationType;
import edu.uci.eecs.crowdsafe.graph.io.modular.ModularTraceDataSink;
import edu.uci.eecs.crowdsafe.graph.io.modular.ModularTraceDirectory;
import edu.uci.eecs.crowdsafe.graph.io.modular.ModularTraceStreamType;

public class ModuleDataWriter<NodeType extends NodeIdentifier> {

	public interface ModularData<NodeType extends NodeIdentifier> {
		ApplicationModule getModule();

		int getNodeIndex(NodeType node);
	}

	public interface Edge<NodeType extends NodeIdentifier> {
		NodeType getFromNode();

		NodeType getToNode();

		EdgeType getEdgeType();

		int getOrdinal();

		boolean isModuleEntry();

		boolean isModuleExit();
	}

	public static class Directory<NodeType extends NodeIdentifier> {
		private final ModularTraceDataSink dataSink;
		private final String filenameFormat;

		private final Map<ApplicationModule, ModuleDataWriter<NodeType>> outputsByCluster = new HashMap<ApplicationModule, ModuleDataWriter<NodeType>>();

		public Directory(File directory, String processName) {
			dataSink = new ModularTraceDirectory(directory);
			filenameFormat = String.format("%s.%%s.%%s.%%s", processName);
		}

		public void establishModuleWriters(ModularData<NodeType> data) throws IOException {
			ModuleDataWriter<NodeType> writer = getWriter(data.getModule());
			if (writer == null) {
				dataSink.addModule(data.getModule(), filenameFormat);
				writer = new ModuleDataWriter<NodeType>(data, dataSink);
				outputsByCluster.put(data.getModule(), writer);
			}
		}

		public ModuleDataWriter<NodeType> getWriter(ApplicationModule cluster) {
			return outputsByCluster.get(cluster);
		}

		public void flush() throws IOException {
			for (ModuleDataWriter<NodeType> output : outputsByCluster.values()) {
				output.flush();
			}
		}
	}

	final LittleEndianOutputStream nodeStream;
	final LittleEndianOutputStream edgeStream;
	final LittleEndianOutputStream metaStream;

	private final ModularData<NodeType> data;

	ModuleDataWriter(ModularData<NodeType> data, ModularTraceDataSink dataSink) throws IOException {
		this.data = data;

		nodeStream = dataSink.getLittleEndianOutputStream(data.getModule(), ModularTraceStreamType.GRAPH_NODE);
		edgeStream = dataSink.getLittleEndianOutputStream(data.getModule(), ModularTraceStreamType.GRAPH_EDGE);
		metaStream = dataSink.getLittleEndianOutputStream(data.getModule(), ModularTraceStreamType.META);
	}

	public void writeNode(NodeType node) throws IOException {
		long word = 0; // data.getModuleIndex(node.getModule()); // obsolete
		word |= ((long) node.getRelativeTag() & 0xffffffffL) << 0x10;
		word |= ((long) node.getInstanceId()) << 0x30;
		word |= ((long) node.getType().ordinal()) << 0x38;
		nodeStream.writeLong(word);
		nodeStream.writeLong(node.getHash());
	}

	public void writeEdge(Edge<NodeType> edge) throws IOException {
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
		nodeStream.close();
		edgeStream.flush();
		edgeStream.close();
		metaStream.flush();
		metaStream.close();
	}
}