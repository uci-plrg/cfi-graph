package edu.uci.eecs.crowdsafe.graph.data.application;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
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
	public final Map<String, ApplicationModule> modulesByFilename = new HashMap<String, ApplicationModule>();
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
		modulesByFilename.put(ApplicationModule.SYSTEM_MODULE.filename, ApplicationModule.SYSTEM_MODULE);
		modulesByName.put(ApplicationModule.ANONYMOUS_MODULE.name, ApplicationModule.ANONYMOUS_MODULE);
		modulesByFilename.put(ApplicationModule.ANONYMOUS_MODULE.filename, ApplicationModule.ANONYMOUS_MODULE);

		for (int i = 0; i < ModuleNode.SYSCALL_COUNT; i++)
			sysnumsBySyscallHash.put(CrowdSafeTraceUtil.stringHash(String.format("syscall#%d", i)), i);
	}

	public ApplicationModule establishModuleById(String instanceName) {
		if (instanceName.startsWith(ApplicationModule.ANONYMOUS_MODULE_ID))
			instanceName = instanceName.replace(ApplicationModule.ANONYMOUS_MODULE_ID,
					ApplicationModule.ANONYMOUS_MODULE_NAME);

		return establishModuleByFileSystemName(instanceName);
	}

	public synchronized ApplicationModule establishModuleByFileSystemName(String name) {
		ApplicationModule existing = modulesByName.get(name);
		if (existing != null)
			return existing;

		ApplicationModule module;
		if (name.startsWith(ApplicationModule.ANONYMOUS_MODULE_NAME)) {
			module = ApplicationModule.ANONYMOUS_MODULE;
		} else {
			module = new ApplicationModule(name, name);

			crossModuleLabels.put(module.anonymousEntryHash.hash, module.anonymousEntryHash);
			crossModuleLabels.put(module.anonymousExitHash.hash, module.anonymousExitHash);
			crossModuleLabels.put(module.interceptionHash.hash, module.interceptionHash);
		}
		crossModuleLabels.put(module.anonymousGencodeHash.hash, module.anonymousGencodeHash);

		modulesByName.put(module.name, module);
		modulesByFilename.put(module.filename, module);

		return module;
	}

	public void loadCrossModuleLabels(InputStream xhashStream) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(xhashStream));
		try {
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				ModuleBoundaryNode.HashLabel label = new ModuleBoundaryNode.HashLabel(line);
				crossModuleLabels.put(label.hash, label);
			}
		} finally {
			reader.close();
		}
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
			return label.fromModuleFilename;
	}

	public String getToModuleName(long crossModuleHash) {
		ModuleBoundaryNode.HashLabel label = ApplicationModuleSet.getInstance().crossModuleLabels.get(crossModuleHash);
		if (label == null)
			return "<unknown>";
		else
			return label.toModuleFilename;
	}
}
