package edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata;

import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;

public class ClusterSSC {

	public final Edge<ClusterNode<?>> suspicionRaisingEdge;
	public final int sysnum;
	public final int uibCount;
	public final int suibCount;

	public ClusterSSC(Edge<ClusterNode<?>> suspicionRaisingEdge, int sysnum, int uibCount, int suibCount) {
		this.suspicionRaisingEdge = suspicionRaisingEdge;
		this.sysnum = sysnum;
		this.uibCount = uibCount;
		this.suibCount = suibCount;
	}
}
