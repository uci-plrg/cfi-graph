package edu.uci.eecs.crowdsafe.graph.data.graph;

import edu.uci.eecs.crowdsafe.graph.data.dist.SoftwareUnit;

/**
 * <p>
 * ModuleGraph is a special ExecutionGraph which starts from multiple different signature hash node. An indirect edge
 * links the signature hash node to the real entry nodes of the module. If there are conflicts on signature hash, there
 * will be multiple indirect edges from the same signature hash node to different target entry nodes. This class has a
 * special field, signature2Node, which maps from signature hash to the "bogus" node representing that signature.
 * </p>
 * 
 * <p>
 * When matching the module graph, we suppose that the "moduleName" field is the universal identity of the graph, which
 * means we can will and only will match the graphs that have the same module names. The matching procedure is almost
 * the same as that of the ExecutionGraph except that we should think all the signature nodes are already matched.
 * </p>
 * 
 * 
 * @author peizhaoo
 * 
 */

// TODO: this class seems kind of pointless now
public class ModuleGraph {
	public final SoftwareUnit softwareUnit;

	private int executableBlockCount = 0;

	public ModuleGraph(SoftwareUnit softwareUnit) {
		this.softwareUnit = softwareUnit;
	}

	void incrementExecutableBlockCount() {
		executableBlockCount++;
	}

	public int getExecutableBlockCount() {
		return executableBlockCount;
	}

	public String toString() {
		return "Module_" + softwareUnit.name;
	}
}
