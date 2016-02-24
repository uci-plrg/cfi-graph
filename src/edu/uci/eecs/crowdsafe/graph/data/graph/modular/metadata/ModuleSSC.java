package edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata;

import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;

public class ModuleSSC {

	public final Edge<ModuleNode<?>> suspicionRaisingEdge;
	public final int sysnum;
	public final int uibCount;
	public final int suibCount;

	public ModuleSSC(Edge<ModuleNode<?>> suspicionRaisingEdge, int sysnum, int uibCount, int suibCount) {
		this.suspicionRaisingEdge = suspicionRaisingEdge;
		this.sysnum = sysnum;
		this.uibCount = uibCount;
		this.suibCount = suibCount;
	}
}
