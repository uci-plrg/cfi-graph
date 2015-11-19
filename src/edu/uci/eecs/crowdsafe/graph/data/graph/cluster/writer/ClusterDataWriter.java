package edu.uci.eecs.crowdsafe.graph.data.graph.cluster.writer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianOutputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.graph.data.dist.SoftwareModule;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.NodeIdentifier;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterBoundaryNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterMetadata;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterMetadataExecution;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterMetadataSequence;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterSGE;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterSSC;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterUIB;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.ClusterUIBInterval;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata.EvaluationType;
import edu.uci.eecs.crowdsafe.graph.io.cluster.ClusterTraceDataSink;
import edu.uci.eecs.crowdsafe.graph.io.cluster.ClusterTraceDirectory;
import edu.uci.eecs.crowdsafe.graph.io.cluster.ClusterTraceStreamType;

public class ClusterDataWriter<NodeType extends NodeIdentifier> {

	public interface ClusterData<NodeType extends NodeIdentifier> {
		AutonomousSoftwareDistribution getCluster();

		int getModuleIndex(SoftwareModule module);

		Iterable<? extends SoftwareModule> getSortedModuleList();

		int getNodeIndex(NodeType node);
	}

	public interface Edge<NodeType extends NodeIdentifier> {
		NodeType getFromNode();

		NodeType getToNode();

		EdgeType getEdgeType();

		int getOrdinal();

		boolean isClusterEntry();

		boolean isClusterExit();
	}

	public static class Directory<NodeType extends NodeIdentifier> {
		private final ClusterTraceDataSink dataSink;
		private final String filenameFormat;

		private final Map<AutonomousSoftwareDistribution, ClusterDataWriter<NodeType>> outputsByCluster = new HashMap<AutonomousSoftwareDistribution, ClusterDataWriter<NodeType>>();

		public Directory(File directory, String processName) {
			dataSink = new ClusterTraceDirectory(directory);
			filenameFormat = String.format("%s.%%s.%%s.%%s", processName);
		}

		public void establishClusterWriters(ClusterData<NodeType> data) throws IOException {
			ClusterDataWriter<NodeType> writer = getWriter(data.getCluster());
			if (writer == null) {
				dataSink.addCluster(data.getCluster(), filenameFormat);
				writer = new ClusterDataWriter<NodeType>(data, dataSink);
				outputsByCluster.put(data.getCluster(), writer);
			}
		}

		public ClusterDataWriter<NodeType> getWriter(AutonomousSoftwareDistribution cluster) {
			return outputsByCluster.get(cluster);
		}

		public void flush() throws IOException {
			for (ClusterDataWriter<NodeType> output : outputsByCluster.values()) {
				output.flush();
			}
		}
	}

	final LittleEndianOutputStream nodeStream;
	final LittleEndianOutputStream edgeStream;
	final LittleEndianOutputStream metaStream;
	final BufferedWriter moduleWriter;

	private final ClusterData<NodeType> data;

	ClusterDataWriter(ClusterData<NodeType> data, ClusterTraceDataSink dataSink) throws IOException {
		this.data = data;

		nodeStream = dataSink.getLittleEndianOutputStream(data.getCluster(), ClusterTraceStreamType.GRAPH_NODE);
		edgeStream = dataSink.getLittleEndianOutputStream(data.getCluster(), ClusterTraceStreamType.GRAPH_EDGE);
		metaStream = dataSink.getLittleEndianOutputStream(data.getCluster(), ClusterTraceStreamType.META);
		moduleWriter = new BufferedWriter(new OutputStreamWriter(dataSink.getDataOutputStream(data.getCluster(),
				ClusterTraceStreamType.MODULE)));
	}

	public void writeNode(NodeType node) throws IOException {
		long word = data.getModuleIndex(node.getModule());
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

	public void writeMetadataHistory(ClusterMetadata metadata,
			Map<edu.uci.eecs.crowdsafe.graph.data.graph.Edge<ClusterNode<?>>, Integer> edgeIndexMap) throws IOException {
		writeMetadataHeader(metadata.isMain());
		for (ClusterMetadataSequence sequence : metadata.sequences.values()) {
			writeSequenceMetadataHeader(sequence.executions.size(), sequence.isRoot());
			for (ClusterMetadataExecution execution : sequence.executions) {
				writeExecutionMetadataHeader(execution.id, execution.uibs.size(), execution.getIntervalCount(),
						execution.getSuspiciousSyscallCount(), execution.getSuspiciousGencodeEntryCount());
				for (ClusterUIB uib : execution.uibs) {
					writeUIB(edgeIndexMap.get(uib.edge), uib.isAdmitted, uib.traversalCount, uib.instanceCount);
				}
				for (EvaluationType type : EvaluationType.values()) {
					for (ClusterUIBInterval interval : execution.getIntervals(type)) {
						writeUIBInterval(interval.type.id, interval.span, interval.count, interval.maxConsecutive);
					}
				}
				for (ClusterSSC ssc : execution.sscs) {
					if (edgeIndexMap.get(ssc.suspicionRaisingEdge) == null)
						Log.error("Failed to locate the index for the supicion-raising edge %s",
								ssc.suspicionRaisingEdge);
					else
						writeSSC(ssc.sysnum, edgeIndexMap.get(ssc.suspicionRaisingEdge));
					// old format: ssc.uibCount, ssc.suibCount);
				}
				for (ClusterSGE sge : execution.sges) {
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

	public void writeModules() throws IOException {
		for (SoftwareModule module : data.getSortedModuleList()) {
			if (module.equals(ClusterBoundaryNode.BOUNDARY_MODULE))
				continue;

			moduleWriter.write(module.unit.name);
		}
	}

	public void flush() throws IOException {
		nodeStream.flush();
		nodeStream.close();
		edgeStream.flush();
		edgeStream.close();
		metaStream.flush();
		metaStream.close();
		moduleWriter.flush();
		moduleWriter.close();
	}
}
