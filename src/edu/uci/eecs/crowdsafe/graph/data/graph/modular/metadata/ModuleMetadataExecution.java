package edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;

public class ModuleMetadataExecution {

	public final UUID id;

	// non-null means the execution represents a main module, even if the map remains empty of intervals
	private Map<EvaluationType, List<ModuleUIBInterval>> intervals = null;

	public final List<ModuleUIB> uibs = new ArrayList<ModuleUIB>();
	public final List<ModuleSSC> sscs = new ArrayList<ModuleSSC>();
	public final List<ModuleSGE> sges = new ArrayList<ModuleSGE>();

	public ModuleMetadataExecution() {
		this(UUID.randomUUID());
	}

	public ModuleMetadataExecution(UUID id) {
		this.id = id;
	}

	public int getIntervalCount() {
		if (intervals == null)
			return 0;

		int count = 0;
		for (List<ModuleUIBInterval> group : intervals.values()) {
			count += group.size();
		}
		return count;
	}

	public int getSuspiciousSyscallCount() {
		return sscs.size();
	}

	public int getSuspiciousGencodeEntryCount() {
		return sges.size();
	}

	public List<ModuleUIBInterval> getIntervals(EvaluationType type) {
		if (intervals == null)
			return Collections.emptyList();

		return intervals.get(type);
	}

	public void addInterval(ModuleUIBInterval interval) {
		initializeIntervals();

		intervals.get(interval.type).add(interval);
	}

	public void retainMergedUIBs(Collection<Edge<ModuleNode<?>>> mergedEdges, boolean removeSuspiciousEdges) {
		ModuleUIB uib;
		boolean unmerged;
		for (int i = uibs.size() - 1; i >= 0; i--) {
			uib = uibs.get(i);
			if (!uib.isAdmitted && removeSuspiciousEdges) {
				unmerged = !mergedEdges.remove(uib.edge);
			} else {
				unmerged = !mergedEdges.contains(uib.edge);
			}
			if (unmerged) {
				Log.log("Removing UIB (%d) %s because it was not merged", uibs.get(i).traversalCount, uib.edge);
				uibs.remove(i);
			}
		}
	}

	private void initializeIntervals() {
		if (intervals == null) {
			intervals = new EnumMap<EvaluationType, List<ModuleUIBInterval>>(EvaluationType.class);
			for (EvaluationType type : EvaluationType.values())
				intervals.put(type, new ArrayList<ModuleUIBInterval>());
		}
	}
}
