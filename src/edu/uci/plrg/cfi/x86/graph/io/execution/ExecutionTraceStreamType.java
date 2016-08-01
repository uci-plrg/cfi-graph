package edu.uci.plrg.cfi.x86.graph.io.execution;

public enum ExecutionTraceStreamType {
	BLOCK_HASH("block-hash", 1),
	PAIR_HASH("pair-hash", 1),
	GRAPH_EDGE("graph-edge", 2),
	CROSS_MODULE_EDGE("cross-module", 3),
	GRAPH_NODE("graph-node", 2),
	MODULE("module"),
	META("meta", 1),
	XHASH("xhash");

	public final String id;
	public final int entryWordCount;

	private ExecutionTraceStreamType(String id) {
		this(id, -1);
	}

	private ExecutionTraceStreamType(String id, int entryWordCount) {
		this.id = id;
		this.entryWordCount = entryWordCount;
	}
}
