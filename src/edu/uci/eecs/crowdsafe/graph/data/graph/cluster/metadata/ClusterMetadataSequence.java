package edu.uci.eecs.crowdsafe.graph.data.graph.cluster.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClusterMetadataSequence {

	public final UUID id;
	private boolean isRoot;
	public final List<ClusterMetadataExecution> executions = new ArrayList<ClusterMetadataExecution>();

	public ClusterMetadataSequence(UUID id, boolean isRoot) {
		this.id = id;
		this.isRoot = isRoot;
	}

	public boolean isRoot() {
		return isRoot;
	}

	public void setRoot(boolean isRoot) {
		this.isRoot = isRoot;
	}

	public void addExecution(ClusterMetadataExecution execution) {
		for (ClusterMetadataExecution existing : this.executions) {
			if (existing.id.equals(execution.id))
				return;
		}
		executions.add(execution);
	}

	public ClusterMetadataExecution getHeadExecution() {
		return executions.get(executions.size() - 1);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ClusterMetadataSequence))
			return false;

		ClusterMetadataSequence other = (ClusterMetadataSequence) o;
		if (executions.size() != other.executions.size())
			return false;
		for (int i = 0; i < executions.size(); i++) {
			if (!executions.get(i).id.equals(other.executions.get(i).id))
				return false;
		}
		return true;
	}
}
