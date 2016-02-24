package edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata;

import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;

public class ModuleUIB {

	public final Edge<ModuleNode<?>> edge;
	public final boolean isAdmitted;
	public final int traversalCount;
	public final int instanceCount;

	public ModuleUIB(Edge<ModuleNode<?>> uib, boolean isAdmitted, int traversalCount, int instanceCount) {
		this.edge = uib;
		this.isAdmitted = isAdmitted;
		this.traversalCount = traversalCount;
		this.instanceCount = instanceCount;
	}
}
