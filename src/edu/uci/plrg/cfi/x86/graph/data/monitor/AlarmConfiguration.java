package edu.uci.plrg.cfi.x86.graph.data.monitor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

public class AlarmConfiguration {

	enum UnitPredicate {
		UIB("uib"),
		SUIB("suib"),
		UR("ur"),
		GENCODE_WRITE("gw"),
		GENCODE_PERM("gp"),
		TRAMPOLINE("trmpl"),
		FORK("fork");

		final String id;

		private UnitPredicate(String id) {
			this.id = id;
		}
	}

	enum UIBInterval {
		MICRO(3),
		SHORT(4),
		LONG(5),
		MACRO(6);

		final int order;

		private UIBInterval(int order) {
			this.order = order;
		}
	}

	final Map<UnitPredicate, Integer> predicateInstanceCounts = new EnumMap<UnitPredicate, Integer>(UnitPredicate.class);
	final Map<UnitPredicate, Integer> predicateInvocationCounts = new EnumMap<UnitPredicate, Integer>(
			UnitPredicate.class);
	final Map<UIBInterval, Integer> uibIntervalCounts = new EnumMap<UIBInterval, Integer>(UIBInterval.class);
	final Map<UIBInterval, Integer> suibIntervalCounts = new EnumMap<UIBInterval, Integer>(UIBInterval.class);
	final int suspiciousSyscallCounts[] = new int[0x1a3];

	private final Properties configurationInput = new Properties();

	AlarmConfiguration(File configurationFile) throws FileNotFoundException, IOException {
		configurationInput.load(new FileInputStream(configurationFile));

    int defaultCount = getCount("*");
		for (UnitPredicate unitPredicate : UnitPredicate.values()) {
			predicateInstanceCounts.put(unitPredicate, getCount(unitPredicate.id + "-inst", defaultCount));
			predicateInvocationCounts.put(unitPredicate, getCount(unitPredicate.id + "-inv", defaultCount));
		}

		for (UIBInterval interval : UIBInterval.values()) {
			uibIntervalCounts.put(interval, getCount("uib*10^" + interval.order, defaultCount));
			suibIntervalCounts.put(interval, getCount("suib*10^" + interval.order, defaultCount));
		}

    int defaultSyscallCount = getCount("ssc*", defaultCount);
		for (int i = 0; i < suspiciousSyscallCounts.length; i++) {
			suspiciousSyscallCounts[i] = getCount(String.format("ssc#0x%x", i), defaultSyscallCount);
		}
	}

	private int getCount(String propertyName) {
    return getCount(propertyName, 0);
  }

	private int getCount(String propertyName, int defaultCount) {
		String number = configurationInput.getProperty(propertyName);
		if (number == null)
			return defaultCount;
		else
			return Integer.parseInt(number);
	}
}
