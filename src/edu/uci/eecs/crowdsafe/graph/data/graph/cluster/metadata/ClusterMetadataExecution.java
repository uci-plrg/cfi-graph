package edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;

public class ClusterMetadataExecution {

	public final UUID id;

	// non-null means the execution represents a main module, even if the map remains empty of intervals
	private Map<EvaluationType, List<ClusterUIBInterval>> intervals = null;

	public final List<ClusterUIB> uibs = new ArrayList<ClusterUIB>();
	public final List<ClusterSSC> sscs = new ArrayList<ClusterSSC>();
	public final List<ClusterSGE> sges = new ArrayList<ClusterSGE>();

	public ClusterMetadataExecution() {
		this(UUID.randomUUID());
	}

	public ClusterMetadataExecution(UUID id) {
		this.id = id;
	}

	public int getIntervalCount() {
		if (intervals == null)
			return 0;

		int count = 0;
		for (List<ClusterUIBInterval> group : intervals.values()) {
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

	public List<ClusterUIBInterval> getIntervals(EvaluationType type) {
		if (intervals == null)
			return Collections.emptyList();

		return intervals.get(type);
	}

	public void addInterval(ClusterUIBInterval interval) {
		initializeIntervals();

		intervals.get(interval.type).add(interval);
	}

	public void retainMergedUIBs(Collection<Edge<ClusterNode<?>>> mergedEdges) {
		for (int i = uibs.size() - 1; i >= 0; i--) {
			if (!mergedEdges.contains(uibs.get(i).edge)) {
				Log.log("Removing UIB (%d) %s because it was not merged", uibs.get(i).traversalCount, uibs.get(i).edge);
				uibs.remove(i);
			}
		}
	}

	private void initializeIntervals() {
		if (intervals == null) {
			intervals = new EnumMap<EvaluationType, List<ClusterUIBInterval>>(EvaluationType.class);
			for (EvaluationType type : EvaluationType.values())
				intervals.put(type, new ArrayList<ClusterUIBInterval>());
		}
	}
}
