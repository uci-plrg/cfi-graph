package edu.uci.eecs.crowdsafe.graph.data.graph.transform;

import java.util.Comparator;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;

public class RawUnexpectedIndirectBranch {

	static class ExecutionEdgeIndexSorter implements Comparator<RawUnexpectedIndirectBranch> {
		static ExecutionEdgeIndexSorter INSTANCE = new ExecutionEdgeIndexSorter();

		@Override
		public int compare(RawUnexpectedIndirectBranch first, RawUnexpectedIndirectBranch second) {
			return first.rawEdgeIndex - second.rawEdgeIndex;
		}
	}

	static class ClusterEdgeIndexSorter implements Comparator<RawUnexpectedIndirectBranch> {
		static ClusterEdgeIndexSorter INSTANCE = new ClusterEdgeIndexSorter();

		@Override
		public int compare(RawUnexpectedIndirectBranch first, RawUnexpectedIndirectBranch second) {
			return first.clusterEdge.getEdgeIndex() - second.clusterEdge.getEdgeIndex();
		}
	}

	static RawUnexpectedIndirectBranch parse(long rawData) {
		int edgeIndex = ((int) ((rawData >> 8) & 0xffffffL));
		boolean isCrossModule = ((rawData & 0x8000000000000000L) == 0x8000000000000000L);
		boolean isAdmitted = ((rawData & 0x4000000000000000L) == 0x4000000000000000L);
		int traversalCount = ((int) ((rawData >> 0x20) & 0x3fffffffL));
		return new RawUnexpectedIndirectBranch(edgeIndex, isCrossModule, isAdmitted, traversalCount);
	}

	final int rawEdgeIndex;
	public final boolean isCrossModule;
	private boolean isAdmitted;
	private int traversalCount;
	private int instanceCount = 1;

	RawEdge clusterEdge;

	public RawUnexpectedIndirectBranch(int edgeIndex, boolean isCrossModule, boolean isAdmitted, int traversalCount) {
		this.rawEdgeIndex = edgeIndex;
		this.isCrossModule = isCrossModule;
		this.isAdmitted = isAdmitted;
		this.traversalCount = traversalCount;
	}

	public RawUnexpectedIndirectBranch(RawUnexpectedIndirectBranch copyMe) {
		this.rawEdgeIndex = copyMe.rawEdgeIndex;
		this.isCrossModule = copyMe.isCrossModule;
		this.isAdmitted = copyMe.isAdmitted;
		this.traversalCount = copyMe.traversalCount;
	}

	void merge(RawUnexpectedIndirectBranch other) {
		if (isCrossModule != other.isCrossModule)
			throw new IllegalArgumentException(
					"Attempt to merge incompatible UIBs: (cross-module x intra-module) on edge " + clusterEdge);
		if (isAdmitted != other.isAdmitted) {
			if (clusterEdge.getToNode().getType() != MetaNodeType.CLUSTER_EXIT)
				Log.log("Warning: merging incompatible UIBs: (admitted x suspicious) on edge " + clusterEdge);

			isAdmitted = false;
		}

		traversalCount += other.traversalCount;
		instanceCount++;
	}

	public boolean isAdmitted() {
		return isAdmitted;
	}

	public int getClusterEdgeIndex() {
		return clusterEdge.getEdgeIndex();
	}

	public int getTraversalCount() {
		return traversalCount;
	}

	public int getInstanceCount() {
		return instanceCount;
	}
}
