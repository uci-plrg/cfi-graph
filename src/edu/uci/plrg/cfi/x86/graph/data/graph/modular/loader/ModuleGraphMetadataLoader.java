package edu.uci.plrg.cfi.x86.graph.data.graph.modular.loader;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import edu.uci.plrg.cfi.common.io.LittleEndianInputStream;
import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.x86.graph.data.graph.Edge;
import edu.uci.plrg.cfi.x86.graph.data.graph.EdgeType;
import edu.uci.plrg.cfi.x86.graph.data.graph.OrdinalEdgeList;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ModuleNode;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.metadata.ModuleMetadataExecution;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.metadata.ModuleMetadataSequence;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.metadata.ModuleSGE;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.metadata.ModuleSSC;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.metadata.ModuleUIB;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.metadata.ModuleUIBInterval;

public class ModuleGraphMetadataLoader {

	private static final int ENTRY_BYTE_COUNT = 0x8;

	private final LittleEndianInputStream input;
	private final List<Edge<ModuleNode<?>>> edgeList;

	ModuleGraphMetadataLoader(List<Edge<ModuleNode<?>>> edgeList, LittleEndianInputStream input) {
		this.edgeList = edgeList;
		this.input = input;
	}

	boolean ready() throws IOException {
		return input.ready(ENTRY_BYTE_COUNT);
	}

	boolean isMetadataMain() throws IOException {
		long header = input.readLong();
		return (header == 1L); // MAIN
	}

	ModuleMetadataSequence loadSequence() throws IOException {
		long header = input.readLong();
		boolean isRoot = (header & 1L) == 1L;
		int executionCount = ((int) (header >> 0x20));
		if (executionCount == 0)
			return null;

		ModuleMetadataExecution rootExecution = loadExecution();
		ModuleMetadataSequence sequence = new ModuleMetadataSequence(rootExecution.id, isRoot);
		sequence.executions.add(rootExecution);

		for (int i = 1; i < executionCount; i++) {
			sequence.executions.add(loadExecution());
		}

		return sequence;
	}

	private ModuleMetadataExecution loadExecution() throws IOException {
		long entryCounts = input.readLong();
		int uibCount = (int) (entryCounts & 0x7fffffffL);
		int intervalCount = (int) ((entryCounts >> 0x20) & 0x7fffffffL);

		entryCounts = input.readLong();
		int sscCount = (int) (entryCounts & 0x7fffffffL);
		int sgeCount = (int) ((entryCounts >> 0x20) & 0x7fffffffL);

		long executionIdHigh, executionIdLow;

		if (sscCount < 0 || sscCount > 1000) {
			sscCount = sgeCount = 0;
			executionIdHigh = entryCounts;
		} else {
			executionIdHigh = input.readLong();
		}
		executionIdLow = input.readLong();

		UUID executionId = new UUID(executionIdHigh, executionIdLow);
		ModuleMetadataExecution execution = new ModuleMetadataExecution(executionId);

		for (int i = 0; i < uibCount; i++) {
			long uibData = input.readLong();
			int edgeIndex = ((int) (uibData & 0xfffffL));
			int instanceCount = (int) ((uibData >> 0x14) & 0xfffL);
			int traversalCount = ((int) ((uibData >> 0x20) & 0x7fffffff));
			boolean isAdmitted = ((uibData & 0x8000000000000000L) == 0x8000000000000000L);

			// hack--correcting the cluster entry edge type modeling error
			Edge<ModuleNode<?>> uibEdge = edgeList.get(edgeIndex);
			if ((uibEdge != null) && !isAdmitted) { // UIB-FIX: if left graph, need to check edges on the right
				OrdinalEdgeList<ModuleNode<?>> edges = uibEdge.getToNode().getIncomingEdges();
				try {
					for (Edge<ModuleNode<?>> edge : edges) {
						if ((edge != uibEdge) && (edge.getEdgeType() == EdgeType.INDIRECT)) {
							isAdmitted = true;
							break;
						}
					}
				} finally {
					edges.release();
				}
			}

			ModuleUIB uib = new ModuleUIB(uibEdge, isAdmitted, traversalCount, instanceCount);
			execution.uibs.add(uib);
		}

		for (int i = 0; i < intervalCount; i++) {
			long intervalData = input.readLong();
			int typeId = ((int) (intervalData & 0xffL));
			int span = ((int) ((intervalData >> 8) & 0xffL));
			int maxConsecutive = ((int) ((intervalData >> 0x10) & 0xffffL));
			int count = ((int) ((intervalData >> 0x20) & 0x7fffffffL));
			ModuleUIBInterval interval = new ModuleUIBInterval(typeId, span, count, maxConsecutive);
			execution.addInterval(interval);
		}

		for (int i = 0; i < sscCount; i++) {
			long sscData = input.readLong();
			int sysnum = (int) (sscData & 0xffffL);
			int edgeIndex = (int) ((sscData >> 0x10) & 0xfffffL);
			int sscUibCount = 0; // old format: (int) ((sscData >> 0x10) & 0xffffL);
			int sscSuibCount = 0; // old format: (int) ((sscData >> 0x20) & 0xffffL);
			ModuleSSC ssc = new ModuleSSC(edgeList.get(edgeIndex), sysnum, sscUibCount, sscSuibCount);
			execution.sscs.add(ssc);
		}

		// Log.log("Loading %d suspicious gencode entries.", sgeCount);
		for (int i = 0; i < sgeCount; i++) {
			long sgeData = input.readLong();
			int edgeIndex = (int) (sgeData & 0xfffffL);
			int sgeUibCount = (int) ((sgeData >> 0x14) & 0xffffL);
			int sgeSuibCount = (int) ((sgeData >> 0x24) & 0xffffL);
			ModuleSGE sge = new ModuleSGE(edgeList.get(edgeIndex), sgeUibCount, sgeSuibCount);
			execution.sges.add(sge);
		}

		return execution;
	}

	void close() throws IOException {
		if (input.ready())
			Log.log("Warning: input stream %s has %d bytes remaining.", input.description, input.available());

		input.close();
	}
}
