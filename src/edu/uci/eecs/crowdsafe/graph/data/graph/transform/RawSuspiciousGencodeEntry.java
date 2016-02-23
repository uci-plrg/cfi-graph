package edu.uci.eecs.crowdsafe.graph.data.graph.transform;

import java.util.Comparator;

public class RawSuspiciousGencodeEntry {

	static class ExecutionEdgeIndexSorter implements Comparator<RawSuspiciousGencodeEntry> {
		static ExecutionEdgeIndexSorter INSTANCE = new ExecutionEdgeIndexSorter();

		@Override
		public int compare(RawSuspiciousGencodeEntry first, RawSuspiciousGencodeEntry second) {
			return first.edgeIndex - second.edgeIndex;
		}
	}

	public static RawSuspiciousGencodeEntry parse(long rawData) {
		int suibCount = (int) ((rawData >> 8) & 0xffffL);
		int uibCount = (int) ((rawData >> 0x18) & 0xffffL);
		int edgeIndex = (int) ((rawData >> 0x28) & 0xffffffL);
		return new RawSuspiciousGencodeEntry(edgeIndex, uibCount, suibCount);
	}

	final int edgeIndex;
	final int uibCount;
	final int suibCount;

	RawEdge edge;

	private RawSuspiciousGencodeEntry(int edgeIndex, int uibCount, int suibCount) {
		this.edgeIndex = edgeIndex;
		this.uibCount = uibCount;
		this.suibCount = suibCount;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + edgeIndex;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RawSuspiciousGencodeEntry other = (RawSuspiciousGencodeEntry) obj;
		if (edgeIndex != other.edgeIndex)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "SuspiciousGencodeEntry[edgeIndex=" + edgeIndex + ", #uib: " + uibCount + ", #suib: " + suibCount + "]";
	}
}
