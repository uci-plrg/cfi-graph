package edu.uci.plrg.cfi.x86.graph.data.graph.modular.metadata;

import edu.uci.plrg.cfi.x86.graph.data.graph.Edge;
import edu.uci.plrg.cfi.x86.graph.data.graph.modular.ModuleNode;

public class ModuleSGE {

	public final Edge<ModuleNode<?>> edge;
	public final int uibCount;
	public final int suibCount;

	public ModuleSGE(Edge<ModuleNode<?>> edge, int uibCount, int suibCount) {
		this.edge = edge;
		this.uibCount = uibCount;
		this.suibCount = suibCount;
	}
}
