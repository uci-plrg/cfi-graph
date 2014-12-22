package edu.uci.eecs.crowdsafe.graph.data.graph.transform;

import edu.uci.eecs.crowdsafe.graph.data.dist.AutonomousSoftwareDistribution;
import edu.uci.eecs.crowdsafe.graph.data.dist.SoftwareModule;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.NodeIdentifier;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterBasicBlock;
import edu.uci.eecs.crowdsafe.graph.data.graph.cluster.ClusterNode;

public class IndexedClusterNode implements NodeIdentifier {

	public final AutonomousSoftwareDistribution cluster;
	public final ClusterNode<?> node;
	public final int index;

	IndexedClusterNode(AutonomousSoftwareDistribution cluster, ClusterNode<?> node, int index) {
		this.cluster = cluster;
		this.node = node;
		this.index = index;
	}

	@Override
	public SoftwareModule getModule() {
		return node.getModule();
	}

	@Override
	public int getRelativeTag() {
		return node.getRelativeTag();
	}

	@Override
	public int getInstanceId() {
		return node.getInstanceId();
	}

	@Override
	public long getHash() {
		return node.getHash();
	}

	@Override
	public MetaNodeType getType() {
		return node.getType();
	}

	IndexedClusterNode resetToVersionZero() {
		return new IndexedClusterNode(cluster, new ClusterBasicBlock(node.getModule(), node.getRelativeTag(), 0,
				node.getHash(), node.getType()), index);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((cluster.name == null) ? 0 : cluster.name.hashCode());
		result = prime * result + index;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		IndexedClusterNode other = (IndexedClusterNode) obj;
		if (!cluster.name.equals(other.cluster.name))
			return false;
		if (index != other.index)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return node.toString();
	}
}
