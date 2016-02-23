package edu.uci.eecs.crowdsafe.graph.data.graph.execution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.graph.data.dist.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.dist.ApplicationModuleSet;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.graph.data.results.Graph;
import edu.uci.eecs.crowdsafe.graph.io.execution.ExecutionTraceDataSource;
import edu.uci.eecs.crowdsafe.graph.io.execution.ExecutionTraceStreamType;

/**
 * <p>
 * This class abstracts the binary-level labeled control flow graph of any execution of a binary executable.
 * </p>
 * 
 * <p>
 * There are a few assumptions: 1. Within one execution, the tag, which is address of the block of code in the code
 * cache of DynamoRIO, can uniquely represents an actual block of code in run-time memory. This might not be true if the
 * same address has different pieces of code at different time. 2. In windows, we already have a list of known core
 * utility DLL's, which means we will match modules according to the module names plus its version number. This might
 * not be a universally true assumption, but it's still reasonable at this point. We will treat unknown modules as
 * inline code, which is part of the main graph.
 * </p>
 * 
 * <p>
 * This class will have a list of its subclass, ModuleGraph, which is the graph representation of each run-time module.
 * </p>
 * 
 * <p>
 * This class should have the signature2Node filed which maps the signature hash to the bogus signature node. The basic
 * matching strategy separates the main module and all other kernel modules. All these separate graphs have a list of
 * callbacks or export functions from other modules, which have a corresponding signature hash. For those nodes, we try
 * to match them according to their signature hash.
 * </p>
 * 
 * @author peizhaoo
 * 
 */

public class ProcessExecutionGraph {

	public static final EnumSet<ExecutionTraceStreamType> EXECUTION_GRAPH_FILE_TYPES = EnumSet.of(
			ExecutionTraceStreamType.MODULE, ExecutionTraceStreamType.GRAPH_NODE, ExecutionTraceStreamType.GRAPH_EDGE,
			ExecutionTraceStreamType.CROSS_MODULE_EDGE, ExecutionTraceStreamType.META);

	public static final EnumSet<ExecutionTraceStreamType> EXECUTION_GRAPH_REQUIRED_FILE_TYPES = EnumSet.of(
			ExecutionTraceStreamType.MODULE, ExecutionTraceStreamType.GRAPH_NODE, ExecutionTraceStreamType.GRAPH_EDGE,
			ExecutionTraceStreamType.CROSS_MODULE_EDGE);

	private final Map<ApplicationModule, ModuleGraph<ExecutionNode>> moduleGraphs = new HashMap<ApplicationModule, ModuleGraph<ExecutionNode>>();

	// Used to normalize the tag in a single graph
	protected final ProcessExecutionModuleSet modules;

	public final ExecutionTraceDataSource dataSource;

	public ProcessExecutionGraph(String name, ExecutionTraceDataSource dataSource, ProcessExecutionModuleSet modules) {
		this.dataSource = dataSource;
		this.modules = modules;

		for (ApplicationModule module : ApplicationModuleSet.getInstance().modulesByName.values()) {
			ModuleGraph<ExecutionNode> moduleCluster = new ModuleGraph<ExecutionNode>(name, module);
			moduleGraphs.put(module, moduleCluster);
		}
	}

	public void trimEmptyClusters() {
		for (Map.Entry<ApplicationModule, ModuleGraph<ExecutionNode>> entry : new ArrayList<Map.Entry<ApplicationModule, ModuleGraph<ExecutionNode>>>(
				moduleGraphs.entrySet())) {
			if (entry.getValue().hasNodes())
				moduleGraphs.remove(entry.getKey());
		}
	}

	public ProcessExecutionModuleSet getModules() {
		return modules;
	}

	public ModuleGraph<ExecutionNode> getModuleGraph(ApplicationModule module) {
		ModuleGraph<ExecutionNode> graph = moduleGraphs.get(module);
		if (graph == null)
			return moduleGraphs.get(ApplicationModule.MAIN_PROGRAM);
		else
			return graph;
	}

	public Collection<ApplicationModule> getRepresentedClusters() {
		return moduleGraphs.keySet();
	}

	public int calculateTotalNodeCount() {
		int count = 0;
		for (ModuleGraph<ExecutionNode> cluster : moduleGraphs.values()) {
			count += cluster.getNodeCount();
		}
		return count;
	}

	public Graph.Process summarizeProcess() {
		Graph.Process.Builder processBuilder = Graph.Process.newBuilder();
		processBuilder.setId(dataSource.getProcessId());
		processBuilder.setName(dataSource.getProcessName());

		for (ApplicationModule dist : moduleGraphs.keySet()) {
			ModuleGraph<ExecutionNode> cluster = moduleGraphs.get(dist);
			cluster.analyzeGraph(cluster.module.isAnonymous);
			processBuilder.addCluster(cluster.summarize(cluster.module.isAnonymous));
		}
		
		return processBuilder.build();
	}

	public String toString() {
		return dataSource.getProcessName() + "-" + dataSource.getProcessId();
	}
}