package edu.uci.plrg.cfi.x86.graph.data.graph.modular.metadata;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import edu.uci.plrg.cfi.x86.graph.data.graph.Edge;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ModuleNode;
import edu.uci.plrg.cfi.x86.graph.data.results.Graph;

public class ModuleMetadata {

	private boolean isMain = false;
	private ModuleMetadataSequence rootSequence;
	public final Map<UUID, ModuleMetadataSequence> sequences = new HashMap<UUID, ModuleMetadataSequence>();

	public void mergeSequence(ModuleMetadataSequence newSequence) {
		if (newSequence.executions.isEmpty())
			return;

		ModuleMetadataSequence existingSequence = sequences.get(newSequence.id);
		if (existingSequence == null) {
			if (newSequence.isRoot()) {
				if (rootSequence == null) {
					rootSequence = newSequence;
				} else {
					newSequence.setRoot(false);
				}
			}
			sequences.put(newSequence.id, newSequence);
			// } else if (!newSequence.equals(existingSequence)) {
			// throw new IllegalArgumentException("Attempt to merge a different version of an existing sequence!");
		}
	}

	public ModuleMetadataSequence getRootSequence() {
		return rootSequence;
	}

	public boolean isMain() {
		return isMain;
	}

	public void setMain(boolean isMain) {
		this.isMain = isMain;
	}

	public boolean isEmpty() {
		return sequences.isEmpty();
	}

	public boolean isSingletonExecution() {
		return (sequences.size() == 1) && (rootSequence.executions.size() == 1);
	}

	public ModuleMetadataExecution getSingletonExecution() {
		return rootSequence.executions.get(0);
	}

	public void retainMergedUIBs(Collection<Edge<ModuleNode<?>>> mergedEdges) {
		if (!isSingletonExecution())
			throw new IllegalArgumentException("Can only retain merged UIBs on a singleton execution.");

		rootSequence.executions.get(0).retainMergedUIBs(mergedEdges, false);
	}

	public Graph.ProcessMetadata summarizeProcess() {
		Graph.ProcessMetadata.Builder metadataBuilder = Graph.ProcessMetadata.newBuilder();
		Graph.IntervalGroup.Builder intervalGroupBuilder = Graph.IntervalGroup.newBuilder();
		Graph.Interval.Builder intervalBuilder = Graph.Interval.newBuilder();
		Graph.SuspiciousSyscall.Builder syscallBuilder = Graph.SuspiciousSyscall.newBuilder();

		if (rootSequence != null) { // hack! FIXME
			ModuleMetadataExecution execution = rootSequence.executions.get(rootSequence.executions.size() - 1);
			metadataBuilder.setSequenceIdHigh(rootSequence.id.getMostSignificantBits());
			metadataBuilder.setSequenceIdLow(rootSequence.id.getLeastSignificantBits());
			metadataBuilder.setExecutionIdHigh(execution.id.getMostSignificantBits());
			metadataBuilder.setExecutionIdLow(execution.id.getLeastSignificantBits());
			metadataBuilder.setExecutionIndex(rootSequence.executions.size());
			for (EvaluationType type : EvaluationType.values()) {
				intervalGroupBuilder.setType(type.getResultType());
				for (ModuleUIBInterval interval : execution.getIntervals(type)) {
					intervalBuilder.setSpan(interval.span);
					intervalBuilder.setOccurrences(interval.count);
					intervalBuilder.setMaxConsecutive(interval.maxConsecutive);
					intervalGroupBuilder.addInterval(intervalBuilder.build());
					intervalBuilder.clear();
				}
				metadataBuilder.addIntervalGroup(intervalGroupBuilder.build());
				intervalGroupBuilder.clear();
			}

			for (ModuleSSC ssc : execution.sscs) {
				syscallBuilder.setSysnum(ssc.sysnum);
				syscallBuilder.setUibCount(ssc.uibCount);
				syscallBuilder.setSuibCount(ssc.suibCount);
				metadataBuilder.addSyscalls(syscallBuilder.build());
				syscallBuilder.clear();
			}
		}

		return metadataBuilder.build();
	}
}
