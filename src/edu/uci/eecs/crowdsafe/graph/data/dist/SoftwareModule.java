package edu.uci.eecs.crowdsafe.graph.data.dist;

import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterModule;

public abstract class SoftwareModule {

	public static final String SYSTEM_MODULE_NAME = "|system|";
	public static final String ANONYMOUS_MODULE_NAME = "|anonymous|";

	public static final String EMPTY_VERSION = "0-0-0";

	public static final ClusterModule SYSTEM_MODULE = new ClusterModule(0, new SoftwareUnit(
			SoftwareUnit.SYSTEM_UNIT_NAME, false));
	public static final ClusterModule ANONYMOUS_MODULE = new ClusterModule(0, new SoftwareUnit(
			SoftwareUnit.ANONYMOUS_UNIT_NAME, true));

	public final SoftwareUnit unit;

	protected SoftwareModule(SoftwareUnit unit) {
		this.unit = unit;
	}

	public boolean isEquivalent(SoftwareModule other) {
		return unit.equals(other.unit) && !unit.isAnonymous;
	}

	@Override
	public int hashCode() {
		return unit.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SoftwareModule other = (SoftwareModule) obj;
		if (!unit.equals(other.unit))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return unit.toString();
	}
}
