package edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata;

public class ClusterSSC {

	public final int sysnum;
	public final int uibCount;
	public final int suibCount;

	public ClusterSSC(int sysnum, int uibCount, int suibCount) {
		this.sysnum = sysnum;
		this.uibCount = uibCount;
		this.suibCount = suibCount;
	}
}
