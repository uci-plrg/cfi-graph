package edu.uci.eecs.crowdsafe.graph.data.graph.cluster;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.graph.data.dist.SoftwareModule;
import edu.uci.eecs.crowdsafe.graph.data.dist.SoftwareUnit;

public class ClusterModuleList {

	private static class IdSorter implements Comparator<ClusterModule> {
		static final IdSorter INSTANCE = new IdSorter();

		@Override
		public int compare(ClusterModule first, ClusterModule second) {
			return first.id - second.id;
		}
	}

	private final Map<SoftwareUnit, ClusterModule> modules = new HashMap<SoftwareUnit, ClusterModule>();
	private final List<ClusterModule> moduleList = new ArrayList<ClusterModule>();

	public ClusterModule addModule(SoftwareUnit unit) {
		if (unit.equals(ClusterBoundaryNode.BOUNDARY_MODULE.unit))
			return ClusterBoundaryNode.BOUNDARY_MODULE; // placeholder only, not to be included in the list

		ClusterModule module;
		if (unit.equals(SoftwareModule.ANONYMOUS_MODULE.unit))
			module = SoftwareModule.ANONYMOUS_MODULE;
		else
			module = new ClusterModule(moduleList.size(), unit);

		moduleList.add(module);
		modules.put(module.unit, module);
		return module;
	}

	public synchronized ClusterModule establishModule(SoftwareUnit unit) {
		ClusterModule module = modules.get(unit);
		if (module == null)
			return addModule(unit);
		else
			return module;
	}

	public synchronized ClusterModule getModule(SoftwareUnit unit) {
		return modules.get(unit);
	}

	public List<ClusterModule> sortById() {
		List<ClusterModule> moduleList = new ArrayList<ClusterModule>(modules.values());
		Collections.sort(moduleList, IdSorter.INSTANCE);
		return moduleList;
	}

	public Collection<ClusterModule> getModules() {
		return modules.values();
	}

	public ClusterModule getModule(int index) {
		return moduleList.get(index);
	}
}
