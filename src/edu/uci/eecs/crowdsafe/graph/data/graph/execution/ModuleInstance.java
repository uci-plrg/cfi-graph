package edu.uci.eecs.crowdsafe.graph.data.graph.execution;

import edu.uci.eecs.crowdsafe.graph.data.dist.SoftwareModule;
import edu.uci.eecs.crowdsafe.graph.data.dist.SoftwareUnit;

// TODO: check the usage of ModuleInstance hashcode/equals: maybe use alternate key for equivocating all instances of the same software unit?
public class ModuleInstance extends SoftwareModule {
	public static ModuleInstance SYSTEM = new ModuleInstance(SoftwareModule.SYSTEM_MODULE.unit, 0L, Long.MAX_VALUE, 0L,
			Long.MAX_VALUE, 0L, Long.MAX_VALUE, 0L, Long.MAX_VALUE);
	public static ModuleInstance ANONYMOUS = new ModuleInstance(SoftwareModule.ANONYMOUS_MODULE.unit, 0L,
			Long.MAX_VALUE, 0L, Long.MAX_VALUE, 0L, Long.MAX_VALUE, 0L, Long.MAX_VALUE);

	public static class Span {
		public final long loadTimestamp;
		public final long unloadTimestamp;

		Span() {
			loadTimestamp = 0L;
			unloadTimestamp = Long.MAX_VALUE;
		}

		public Span(long loadTimestamp, long unloadTimestamp) {
			this.loadTimestamp = loadTimestamp;
			this.unloadTimestamp = unloadTimestamp;
		}

		public boolean contains(long timestamp) {
			return (timestamp >= loadTimestamp) && (timestamp < unloadTimestamp);
		}
	}

	public final long start;
	public final long end;
	public final Span blockSpan;
	public final Span edgeSpan;
	public final Span crossModuleEdgeSpan;

	public ModuleInstance(SoftwareUnit unit, long start, long end, long blockLoadTime, long blockUnloadTime,
			long edgeLoadTime, long edgeUnloadTime, long crossModuleEdgeLoadTime, long crossModuleEdgeUnloadTime) {
		super(unit);
		this.start = start;
		this.end = end;
		this.blockSpan = new Span(blockLoadTime, blockUnloadTime);
		this.edgeSpan = new Span(edgeLoadTime, edgeUnloadTime);
		this.crossModuleEdgeSpan = new Span(crossModuleEdgeLoadTime, crossModuleEdgeUnloadTime);
	}

	public boolean containsTag(long tag) {
		return ((tag >= start) && (tag <= end));
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((unit == null) ? 0 : unit.hashCode());
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
		ModuleInstance other = (ModuleInstance) obj;
		if (unit == null) {
			if (other.unit != null)
				return false;
		} else if (!unit.equals(other.unit))
			return false;
		return true;
	}

	public String toString() {
		return String.format("%s: 0x%x - 0x%x", unit.name, start, end);
	}
}
