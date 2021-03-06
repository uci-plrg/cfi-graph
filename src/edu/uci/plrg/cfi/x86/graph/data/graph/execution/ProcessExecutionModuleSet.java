package edu.uci.plrg.cfi.x86.graph.data.graph.execution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.x86.graph.data.application.ApplicationModule;
import edu.uci.plrg.cfi.x86.graph.io.execution.ExecutionTraceStreamType;

public class ProcessExecutionModuleSet {

	private final Multimap<ApplicationModule, ModuleInstance> instancesByModule = ArrayListMultimap.create();
	private ModuleInstance modules[] = null; // TODO: optimize by grouping

	public void add(ModuleInstance module) {
		if (modules != null)
			throw new IllegalStateException("This set of modules has been frozen, new modules cannot be added now!");
		instancesByModule.put(module, module);
	}

	public Collection<ModuleInstance> getUnitInstances(ApplicationModule module) {
		return instancesByModule.get(module);
	}

	public void freeze() {
		List<ModuleInstance> instances = new ArrayList<ModuleInstance>();
		instances.addAll(instancesByModule.values()); // maintains add-order within each mapping
		modules = instances.toArray(new ModuleInstance[] {});
	}

	public boolean hashOverlap() {
		List<ModuleInstance> list = new ArrayList<ModuleInstance>();
		for (int i = 0; i < list.size(); i++) {
			for (int j = i + 1; j < list.size(); j++) {
				ModuleInstance mod1 = list.get(i), mod2 = list.get(j);
				if ((mod1.start < mod2.start && mod1.end > mod2.start)
						|| (mod1.start < mod2.end && mod1.end > mod2.end)) {
					return true;
				}
			}
		}
		return false;
	}

	public ModuleInstance getModule(long tag, long streamIndex, ExecutionTraceStreamType streamType) {
		switch (streamType) {
			case GRAPH_NODE:
				for (int i = modules.length - 1; i >= 0; i--) {
					ModuleInstance instance = modules[i];
					if ((tag >= instance.start) && (tag <= instance.end)
							&& (streamIndex >= instance.blockSpan.loadTimestamp)
							&& (streamIndex < instance.blockSpan.unloadTimestamp))
						return instance;
				}
				break;
			case GRAPH_EDGE:
				for (int i = modules.length - 1; i >= 0; i--) {
					ModuleInstance instance = modules[i];
					if ((tag >= instance.start) && (tag <= instance.end)
							&& (streamIndex >= instance.edgeSpan.loadTimestamp)
							&& (streamIndex < instance.edgeSpan.unloadTimestamp))
						return instance;
				}
				break;
			case CROSS_MODULE_EDGE:
				for (int i = modules.length - 1; i >= 0; i--) {
					ModuleInstance instance = modules[i];
					if ((tag >= instance.start) && (tag <= instance.end)
							&& (streamIndex >= instance.crossModuleEdgeSpan.loadTimestamp)
							&& (streamIndex < instance.crossModuleEdgeSpan.unloadTimestamp))
						return instance;
				}
				break;
			default:
				throw new IllegalArgumentException("Cannot identify modules for stream type " + streamType);
		}
		Log.log("Error! Failed to identify the module for tag 0x%x", tag);
		return null;
	}
}
