package edu.uci.eecs.crowdsafe.graph.data.application;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.config.CrowdSafeConfiguration;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleBoundaryNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.modular.ModuleNode;
import edu.uci.eecs.crowdsafe.graph.util.CrowdSafeTraceUtil;

public class ApplicationModuleSet {

	public static void initialize() {
		initialize(new File(new File(
				CrowdSafeConfiguration.getInstance().environmentValues
						.get(CrowdSafeConfiguration.Environment.CROWD_SAFE_COMMON_DIR)), "config"));
	}

	public static void initialize(File configDir) {
		INSTANCE = new ApplicationModuleSet(configDir);
	}

	public static ApplicationModuleSet getInstance() {
		return INSTANCE;
	}

	private static ApplicationModuleSet INSTANCE;

	public final File configDir;
	public final Map<String, ApplicationModule> modulesByName = new HashMap<String, ApplicationModule>();
	// public final Map<Long, ApplicationModule> unitsByAnonymousEntryHash = new HashMap<Long, ApplicationModule>();
	// public final Map<Long, ApplicationModule> unitsByAnonymousExitHash = new HashMap<Long, ApplicationModule>();
	// public final Map<Long, ApplicationModule> unitsByInterceptionHash = new HashMap<Long, ApplicationModule>();
	// public final Map<Long, ApplicationModule> unitsByAnonymousGencodeHash = new HashMap<Long, ApplicationModule>();
	public final Map<Long, Integer> sysnumsBySyscallHash = new HashMap<Long, Integer>();
	public final Map<ApplicationModule, ApplicationModule> distributionsByUnit = new HashMap<ApplicationModule, ApplicationModule>();
	public final Map<Long, ModuleBoundaryNode.HashLabel> crossModuleLabels = new HashMap<Long, ModuleBoundaryNode.HashLabel>();

	private ApplicationModuleSet(File configDir) {
		this.configDir = configDir;

		modulesByName.put(ApplicationModule.SYSTEM_MODULE.name, ApplicationModule.SYSTEM_MODULE);
		modulesByName.put(ApplicationModule.ANONYMOUS_MODULE.name, ApplicationModule.ANONYMOUS_MODULE);

		for (int i = 0; i < ModuleNode.SYSCALL_COUNT; i++)
			sysnumsBySyscallHash.put(CrowdSafeTraceUtil.stringHash(String.format("syscall#%d", i)), i);
	}

	public ApplicationModule establishModuleById(String unitName) {
		if (unitName.startsWith(ApplicationModule.ANONYMOUS_MODULE_ID))
			unitName = unitName.replace(ApplicationModule.ANONYMOUS_MODULE_ID, ApplicationModule.ANONYMOUS_MODULE_NAME);

		return establishModuleByFileSystemName(unitName);
	}

	public synchronized ApplicationModule establishModuleByFileSystemName(String name) {
		ApplicationModule existing = modulesByName.get(name);
		if (existing != null)
			return existing;

		boolean isDynamic = name.startsWith(ApplicationModule.ANONYMOUS_MODULE_NAME);
		ApplicationModule module;
		if (name.equals(ApplicationModule.ANONYMOUS_MODULE.name))
			module = ApplicationModule.ANONYMOUS_MODULE;
		else
			module = new ApplicationModule(name, name, isDynamic);

		modulesByName.put(module.name, module);
		// unitsByAnonymousEntryHash.put(unit.anonymousEntryHash, unit);
		// unitsByAnonymousExitHash.put(unit.anonymousExitHash, unit);
		// unitsByAnonymousGencodeHash.put(unit.anonymousGencodeHash, unit);
		// unitsByInterceptionHash.put(unit.interceptionHash, unit);
		crossModuleLabels.put(module.anonymousEntryHash.hash, module.anonymousEntryHash);
		crossModuleLabels.put(module.anonymousExitHash.hash, module.anonymousExitHash);
		crossModuleLabels.put(module.anonymousGencodeHash.hash, module.anonymousGencodeHash);
		crossModuleLabels.put(module.interceptionHash.hash, module.interceptionHash);

		return module;
	}

	public boolean isFromAnonymous(long crossModuleHash) {
		ModuleBoundaryNode.HashLabel label = ApplicationModuleSet.getInstance().crossModuleLabels.get(crossModuleHash);
		return label != null && label.isFromAnonymous();
	}

	public boolean isToAnonymous(long crossModuleHash) {
		ModuleBoundaryNode.HashLabel label = ApplicationModuleSet.getInstance().crossModuleLabels.get(crossModuleHash);
		return label != null && label.isToAnonymous();
	}

	public String getFromModuleName(long crossModuleHash) {
		ModuleBoundaryNode.HashLabel label = ApplicationModuleSet.getInstance().crossModuleLabels.get(crossModuleHash);
		if (label == null)
			return "<unknown>";
		else
			return label.fromModuleName;
	}

	public String getToModuleName(long crossModuleHash) {
		ModuleBoundaryNode.HashLabel label = ApplicationModuleSet.getInstance().crossModuleLabels.get(crossModuleHash);
		if (label == null)
			return "<unknown>";
		else
			return label.toModuleName;
	}
}
