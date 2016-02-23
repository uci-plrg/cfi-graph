package edu.uci.eecs.crowdsafe.graph.data.graph.execution;

import edu.uci.eecs.crowdsafe.graph.data.dist.ApplicationModule;

// TODO: check the usage of ModuleInstance hashcode/equals: maybe use alternate key for equivocating all instances of the same software unit?
public class ModuleInstance extends ApplicationModule {

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

	public static ModuleInstance SYSTEM = new ModuleInstance(ApplicationModule.SYSTEM_MODULE, 0L, Long.MAX_VALUE, 0L,
			Long.MAX_VALUE, 0L, Long.MAX_VALUE, 0L, Long.MAX_VALUE);
	public static ModuleInstance ANONYMOUS = new ModuleInstance(ApplicationModule.ANONYMOUS_MODULE, 0L, Long.MAX_VALUE,
			0L, Long.MAX_VALUE, 0L, Long.MAX_VALUE, 0L, Long.MAX_VALUE);

	public final long start;
	public final long end;
	public final Span blockSpan;
	public final Span edgeSpan;
	public final Span crossModuleEdgeSpan;

	public ModuleInstance(ApplicationModule original, long start, long end, long blockLoadTime, long blockUnloadTime,
			long edgeLoadTime, long edgeUnloadTime, long crossModuleEdgeLoadTime, long crossModuleEdgeUnloadTime) {
		super(original);
		this.start = start;
		this.end = end;
		this.blockSpan = new Span(blockLoadTime, blockUnloadTime);
		this.edgeSpan = new Span(edgeLoadTime, edgeUnloadTime);
		this.crossModuleEdgeSpan = new Span(crossModuleEdgeLoadTime, crossModuleEdgeUnloadTime);
	}

	public boolean containsTag(long tag) {
		return ((tag >= start) && (tag <= end));
	}

	public String toString() {
		return String.format("%s: 0x%x - 0x%x", name, start, end);
	}
}
