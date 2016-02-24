package edu.uci.eecs.crowdsafe.graph.main;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import edu.uci.eecs.crowdsafe.common.config.CrowdSafeConfiguration;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModuleSet;
import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;

public class CommonMergeOptions {

	public static final OptionArgumentMap.StringOption crowdSafeCommonDir = OptionArgumentMap.createStringOption('d');
	public static final OptionArgumentMap.StringOption restrictedModuleOption = OptionArgumentMap
			.createStringOption('c');
	public static final OptionArgumentMap.BooleanOption unitModuleOption = OptionArgumentMap.createBooleanOption('u',
			true);
	public static final OptionArgumentMap.StringOption excludeModuleOption = OptionArgumentMap.createStringOption('x');

	private final OptionArgumentMap map;

	private final Set<String> explicitModuleNames = new HashSet<String>();
	private final Set<String> excludedModuleNames = new HashSet<String>();

	public CommonMergeOptions(ArgumentStack args, OptionArgumentMap.Option<?>... options) {
		List<OptionArgumentMap.Option<?>> allOptions = new ArrayList<OptionArgumentMap.Option<?>>();
		for (OptionArgumentMap.Option<?> option : options) {
			allOptions.add(option);
		}
		map = new OptionArgumentMap(args, allOptions);
	}

	public void parseOptions() {
		map.parseOptions();

		if (restrictedModuleOption.hasValue() && excludeModuleOption.hasValue()) {
			Log.log("Option 'module inclusion' (-c) and 'module exclusion' (-x) may not be used together. Exiting now.");
			System.exit(1);
		}
	}

	public void initializeGraphEnvironment() {
		CrowdSafeConfiguration
				.initialize(new CrowdSafeConfiguration.Environment[] { CrowdSafeConfiguration.Environment.CROWD_SAFE_COMMON_DIR });

		if (crowdSafeCommonDir.getValue() == null) {
			ApplicationModuleSet.initialize();
		} else {
			ApplicationModuleSet.initialize(new File(crowdSafeCommonDir.getValue()));
		}

		if (restrictedModuleOption.hasValue()) {
			StringTokenizer moduleNames = new StringTokenizer(restrictedModuleOption.getValue(), ",");
			while (moduleNames.hasMoreTokens()) {
				explicitModuleNames.add(moduleNames.nextToken());
			}
		} else {
			if (excludeModuleOption.hasValue()) {
				StringTokenizer moduleNames = new StringTokenizer(excludeModuleOption.getValue(), ",");
				while (moduleNames.hasMoreTokens()) {
					excludedModuleNames.add(moduleNames.nextToken());
				}
			}
		}
	}

	public boolean includeModule(ApplicationModule module) {
		if (explicitModuleNames.isEmpty()) {
			return !(excludedModuleNames.contains(module.name) || excludedModuleNames.contains(module.filename));
		}

		return (explicitModuleNames.contains(module.name) || explicitModuleNames.contains(module.filename));
	}
}
