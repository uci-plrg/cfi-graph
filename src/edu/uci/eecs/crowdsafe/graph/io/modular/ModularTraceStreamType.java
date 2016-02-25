package edu.uci.eecs.crowdsafe.graph.io.modular;

public enum ModularTraceStreamType {
	GRAPH_NODE("graph-node", "dat"),
	GRAPH_EDGE("graph-edge", "dat"),
	META("meta", "dat"),
	XHASH("xhash", "tab");

	public final String id;
	public final String extension;

	private ModularTraceStreamType(String id, String extension) {
		this.id = id;
		this.extension = extension;
	}
}
