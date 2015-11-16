package edu.uci.eecs.crowdsafe.graph.data.graph.transform;

import java.util.Comparator;

public class RawSuspiciousSystemCall {

	static class ExecutionEdgeIndexSorter implements Comparator<RawSuspiciousSystemCall> {
		static ExecutionEdgeIndexSorter INSTANCE = new ExecutionEdgeIndexSorter();

		@Override
		public int compare(RawSuspiciousSystemCall first, RawSuspiciousSystemCall second) {
			return first.edgeIndex - second.edgeIndex;
		}
	}

	public static RawSuspiciousSystemCall parse(long rawData) {
		// old format
		// int suibCount = (int) ((rawData >> 8) & 0xffffL);
		// int uibCount = (int) ((rawData >> 0x18) & 0xffffL);
		// int sysnum = (int)((rawData >> 0x28) & 0xffffL);

		// new format
		boolean isCrossModule = ((rawData >> 8) & 0x1L) > 0;
		int edgeIndex = (int) ((rawData >> 0x10) & 0xffffffL);
		int sysnum = (int) ((rawData >> 0x28) & 0xffffL);

		return new RawSuspiciousSystemCall(sysnum, edgeIndex, isCrossModule);
	}

	final int sysnum;
	final int edgeIndex;
	final boolean isCrossModule;

	RawEdge entryEdge;
	RawEdge exitEdge;

	private RawSuspiciousSystemCall(int sysnum, int edgeIndex, boolean isCrossModule) {
		this.sysnum = sysnum;
		this.edgeIndex = edgeIndex;
		this.isCrossModule = isCrossModule;
	}
}
