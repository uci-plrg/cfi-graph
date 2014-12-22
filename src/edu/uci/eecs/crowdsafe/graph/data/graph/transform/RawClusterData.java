package edu.uci.eecs.crowdsafe.graph.data.graph.transform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.graph.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.graph.data.dist.SoftwareModule;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterModule;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterModuleList;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.writer.ClusterDataWriter;

public class RawClusterData implements ClusterDataWriter.ClusterData<IndexedClusterNode> {

	private final AutonomousSoftwareDistribution cluster;

	private final Map<ClusterNode.Key, IndexedClusterNode> nodesByKey = new HashMap<ClusterNode.Key, IndexedClusterNode>();
	private final Map<Long, IndexedClusterNode> entryPointHashes = new HashMap<Long, IndexedClusterNode>();
	private final Map<Long, IndexedClusterNode> exitPointHashes = new HashMap<Long, IndexedClusterNode>();

	final ClusterModuleList moduleList = new ClusterModuleList();
	private final List<IndexedClusterNode> nodeList = new ArrayList<IndexedClusterNode>();
	private final Map<IndexedClusterNode, Integer> nodeIndexMap = new HashMap<IndexedClusterNode, Integer>();
	
	RawClusterData(AutonomousSoftwareDistribution cluster) {
		this.cluster = cluster;
	}

	IndexedClusterNode addNode(ClusterNode<?> node) {
		IndexedClusterNode existing = null;
		switch (node.getType()) {
			case CLUSTER_ENTRY:
				existing = entryPointHashes.get(node.getHash());
				break;
			case CLUSTER_EXIT:
				existing = exitPointHashes.get(node.getHash());
				break;
			default:
				existing = nodesByKey.get(node.getKey());
		}
		if (existing != null)
			return existing;

		IndexedClusterNode rawNode = new IndexedClusterNode(cluster, node, nodeList.size());
		nodeIndexMap.put(rawNode, nodeList.size());
		nodeList.add(rawNode);
		switch (node.getType()) {
			case CLUSTER_ENTRY:
				entryPointHashes.put(node.getHash(), rawNode);
				break;
			case CLUSTER_EXIT:
				exitPointHashes.put(node.getHash(), rawNode);
				break;
			default:
				nodesByKey.put(node.getKey(), rawNode);
		}
		return rawNode;
	}
	
	void replace(IndexedClusterNode original, IndexedClusterNode replacement) {
		nodesByKey.put(replacement.node.getKey(), replacement);
		nodeList.set(replacement.index, replacement);
		nodeIndexMap.put(replacement, replacement.index);
	}
	
	@Override
	public AutonomousSoftwareDistribution getCluster() {
		return cluster;
	}

	IndexedClusterNode getNode(ClusterNode.Key key) {
		return nodesByKey.get(key);
	}

	@Override
	public int getModuleIndex(SoftwareModule module) {
		return ((ClusterModule) module).id;
	}

	@Override
	public Iterable<ClusterModule> getSortedModuleList() {
		return moduleList.sortById();
	}

	@Override
	public int getNodeIndex(IndexedClusterNode node) {
		Integer index = nodeIndexMap.get(node);
		if (index == null) {
			return 0;
		}
		return index;
	}

	public Iterable<IndexedClusterNode> getSortedNodeList() {
		return nodeList;
	}

	int size() {
		return nodesByKey.size();
	}
}
