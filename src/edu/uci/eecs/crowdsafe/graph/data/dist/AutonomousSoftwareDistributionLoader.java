package edu.uci.eecs.crowdsafe.graph.data.dist;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AutonomousSoftwareDistributionLoader {

	public static AutonomousSoftwareDistribution loadDistribution(File configFile) throws IOException {
		Set<SoftwareUnit> distUnits = new HashSet<SoftwareUnit>();
		BufferedReader reader = new BufferedReader(new FileReader(configFile));
		try {
			String line;
			while ((line = reader.readLine()) != null) {
				distUnits.add(new SoftwareUnit(line.toLowerCase()));
			}
		} finally {
			reader.close();
		}

		String distName = configFile.getName().substring(0, configFile.getName().lastIndexOf('.'));
		return new AutonomousSoftwareDistribution(distName, distUnits);
	}

	public static List<AutonomousSoftwareDistribution> loadSingleton(File configFile) throws IOException {
		List<AutonomousSoftwareDistribution> singletons = new ArrayList<AutonomousSoftwareDistribution>();
		BufferedReader reader = new BufferedReader(new FileReader(configFile));
		try {
			String line;
			while ((line = reader.readLine()) != null) {
				SoftwareUnit unit = new SoftwareUnit(line.toLowerCase());
				singletons.add(new AutonomousSoftwareDistribution(unit.name, Collections.singleton(unit)));
			}
		} finally {
			reader.close();
		}

		return singletons;
	}
}
