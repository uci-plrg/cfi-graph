package edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata;

public class ClusterUIBInterval {

	public final EvaluationType type;
	public final int span; // log10 of the interval
	public final int count;
	public final int maxConsecutive;

	public ClusterUIBInterval(int typeId, int span, int count, int maxConsecutive) {
		this.type = EvaluationType.forId(typeId);
		this.span = span;
		this.count = count;
		this.maxConsecutive = maxConsecutive;
	}
}
