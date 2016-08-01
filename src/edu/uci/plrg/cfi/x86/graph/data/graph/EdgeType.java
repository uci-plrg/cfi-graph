package edu.uci.plrg.cfi.x86.graph.data.graph;

import edu.uci.plrg.cfi.x86.graph.data.results.Graph;

public enum EdgeType {
	INDIRECT("I", 0),
	DIRECT("D", 1),
	CALL_CONTINUATION("CC", -1) {
		@Override
		public boolean isHighOrdinal(int ordinal) {
			return (ordinal > 2);
		}
	},
	EXCEPTION_CONTINUATION("EC", 10, 10),
	UNEXPECTED_RETURN("UR", 2),
	GENCODE_PERM("GP", 11, 11),
	GENCODE_WRITE("GW", 12, 12),
	PROCESS_FORK("F", 13, 13);

	public final String code;
	public final int moduleEntryOrdinal;
	public final int symbolicOrdinal;

	private EdgeType(String code) {
		this(code, -1, 0);
	}

	private EdgeType(String code, int moduleEntryOrdinal) {
		this(code, moduleEntryOrdinal, 0);
	}

	private EdgeType(String code, int moduleEntryOrdinal, int symbolicOrdinal) {
		this.code = code;
		this.moduleEntryOrdinal = moduleEntryOrdinal;
		this.symbolicOrdinal = symbolicOrdinal;
	}

	public boolean isHighOrdinal(int ordinal) {
		if (symbolicOrdinal == 0)
			return (ordinal > 1);
		else
			return ordinal != symbolicOrdinal;
	}

	public boolean isContinuation() {
		return (this == CALL_CONTINUATION) || (this == EXCEPTION_CONTINUATION);
	}

	public Graph.EdgeType mapToResultType() {
		switch (this) {
			case INDIRECT:
				return Graph.EdgeType.INDIRECT;
			case DIRECT:
				return Graph.EdgeType.DIRECT;
			case CALL_CONTINUATION:
				return Graph.EdgeType.CALL_CONTINUATION;
			case EXCEPTION_CONTINUATION:
				return Graph.EdgeType.EXCEPTION_CONTINUATION;
			case UNEXPECTED_RETURN:
				return Graph.EdgeType.UNEXPECTED_RETURN;
			case GENCODE_PERM:
				return Graph.EdgeType.GENCODE_PERM;
			case GENCODE_WRITE:
				return Graph.EdgeType.GENCODE_WRITE;
			case PROCESS_FORK:
				return Graph.EdgeType.INDIRECT; // hack!
		}
		throw new IllegalStateException("Unknown EdgeType " + this);
	}
}
