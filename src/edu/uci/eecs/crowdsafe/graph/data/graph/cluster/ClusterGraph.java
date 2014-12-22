package edu.uci.eecs.crowdsafe.graph.data.graph.cluster;

import java.util.EnumSet;

import edu.uci.eecs.crowdsafe.graph.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.graph.data.dist.SoftwareModule;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraph;
import edu.uci.eecs.crowdsafe.graph.data.graph.ModuleGraphCluster;
import edu.uci.eecs.crowdsafe.graph.data.results.Graph;
import edu.uci.eecs.crowdsafe.graph.io.cluster.ClusterTraceStreamType;

public class ClusterGraph {

	public static EnumSet<ClusterTraceStreamType> CLUSTER_GRAPH_STREAM_TYPES = EnumSet
			.allOf(ClusterTraceStreamType.class);

	public final ModuleGraphCluster<ClusterNode<?>> graph;
	public final ClusterModuleList moduleList;

	public ClusterGraph(ModuleGraphCluster<ClusterNode<?>> graph) {
		this.graph = graph;

		moduleList = new ClusterModuleList();
		for (ModuleGraph module : graph.getGraphs()) {
			moduleList.addModule(module.softwareUnit);
		}
	}

	public ClusterGraph(String name, AutonomousSoftwareDistribution cluster) {
		graph = new ModuleGraphCluster<ClusterNode<?>>(name, cluster);
		moduleList = new ClusterModuleList();
	}

	public ClusterGraph(String name, AutonomousSoftwareDistribution cluster, ClusterModuleList moduleList) {
		graph = new ModuleGraphCluster<ClusterNode<?>>(name, cluster);
		this.moduleList = moduleList;

		for (ClusterModule module : moduleList.getModules()) {
			graph.addModule(new ModuleGraph(module.unit));
		}
	}
	
	public ClusterNode<?> addNode(long hash, SoftwareModule module, int relativeTag, MetaNodeType type) {
		switch (type) {
			case CLUSTER_ENTRY:
				ClusterBoundaryNode.Key entryKey = new ClusterBoundaryNode.Key(hash, type);
				ClusterNode<?> entry = graph.getNode(entryKey);
				if (entry == null) {
					entry = new ClusterBoundaryNode(hash, type);
					graph.addClusterEntryNode(entry);
					graph.addNode(entry);
				}
				return entry;
			case CLUSTER_EXIT:
				ClusterBoundaryNode.Key exitKey = new ClusterBoundaryNode.Key(hash, type);
				ClusterNode<?> exit = graph.getNode(exitKey);
				if (exit == null) {
					exit = new ClusterBoundaryNode(hash, type);
					graph.addNode(exit);
				}
				return exit;
		}

		ClusterModule mergedModule = moduleList.establishModule(module.unit);
		if (graph.getModuleGraph(module.unit) == null)
			graph.addModule(new ModuleGraph(module.unit));

		ClusterBasicBlock.Key key = new ClusterBasicBlock.Key(mergedModule, relativeTag, 0);
		while (graph.hasNode(key))
			key = new ClusterBasicBlock.Key(mergedModule, relativeTag, key.instanceId + 1);

		ClusterBasicBlock node = new ClusterBasicBlock(key, hash, type);
		graph.addNode(node);
		return node;
	}

	public Graph.Node summarizeProcess() {
		return null;
	}
}
