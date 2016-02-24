package edu.uci.eecs.crowdsafe.graph.data.graph.modular.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ModuleMetadataSequence {

	public final UUID id;
	private boolean isRoot;
	public final List<ModuleMetadataExecution> executions = new ArrayList<ModuleMetadataExecution>();

	public ModuleMetadataSequence(UUID id, boolean isRoot) {
		this.id = id;
		this.isRoot = isRoot;
	}

	public boolean isRoot() {
		return isRoot;
	}

	public void setRoot(boolean isRoot) {
		this.isRoot = isRoot;
	}

	public void addExecution(ModuleMetadataExecution execution) {
		for (ModuleMetadataExecution existing : this.executions) {
			if (existing.id.equals(execution.id))
				return;
		}
		executions.add(execution);
	}

	public ModuleMetadataExecution getHeadExecution() {
		return executions.get(executions.size() - 1);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ModuleMetadataSequence))
			return false;

		ModuleMetadataSequence other = (ModuleMetadataSequence) o;
		if (executions.size() != other.executions.size())
			return false;
		for (int i = 0; i < executions.size(); i++) {
			if (!executions.get(i).id.equals(other.executions.get(i).id))
				return false;
		}
		return true;
	}
}
