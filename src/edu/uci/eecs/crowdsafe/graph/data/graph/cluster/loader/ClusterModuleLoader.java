package edu.uci.eecs.crowdsafe.graph.data.graph.cluster.loader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import edu.uci.eecs.crowdsafe.graph.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.graph.data.dist.ConfiguredSoftwareDistributions;
import edu.uci.eecs.crowdsafe.graph.data.dist.SoftwareUnit;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterModuleList;
import edu.uci.eecs.crowdsafe.graph.io.cluster.ClusterTraceDataSource;
import edu.uci.eecs.crowdsafe.graph.io.cluster.ClusterTraceStreamType;

public class ClusterModuleLoader {

	private final ClusterTraceDataSource dataSource;

	ClusterModuleLoader(ClusterTraceDataSource dataSource) {
		this.dataSource = dataSource;
	}

	ClusterModuleList loadModules(AutonomousSoftwareDistribution cluster, ClusterTraceDataSource dataSource)
			throws IOException {
		ClusterModuleList modules = new ClusterModuleList();

		BufferedReader input = new BufferedReader(new InputStreamReader(dataSource.getDataInputStream(cluster,
				ClusterTraceStreamType.MODULE)));
		while (input.ready()) {
			String moduleLine = input.readLine();
			if (moduleLine.length() == 0)
				continue;

			SoftwareUnit unit = ConfiguredSoftwareDistributions.getInstance().establishUnitByFileSystemName(moduleLine);
			modules.addModule(unit);
		}
		input.close();

		return modules;
	}
}
