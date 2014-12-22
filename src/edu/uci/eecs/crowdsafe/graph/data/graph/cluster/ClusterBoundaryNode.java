package edu.uci.eecs.crowdsafe.graph.data.graph.cluster;

import edu.uci.eecs.crowdsafe.graph.data.dist.SoftwareUnit;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.Node;

public class ClusterBoundaryNode extends ClusterNode<ClusterBoundaryNode.Key> {

	// id 0 means: arbitrarily associate with the first module in the cluster
	public static final ClusterModule BOUNDARY_MODULE = new ClusterModule(0, SoftwareUnit.CLUSTER_BOUNDARY);

	public static class Key implements Node.Key {
		private final long hash;

		private final MetaNodeType type;

		public Key(long hash, MetaNodeType type) {
			if (hash == 0L)
				throw new IllegalArgumentException("ClusterBoundaryNode hash cannot be zero!");

			this.hash = hash;
			this.type = type;
		}

		@Override
		public boolean isModuleRelativeEquivalent(edu.uci.eecs.crowdsafe.graph.data.graph.Node.Key other) {
			if (other instanceof Key) {
				Key otherKey = (Key) other;
				return (type == otherKey.type) && (hash == otherKey.hash);
			}
			return false;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (int) (hash ^ (hash >>> 32));
			result = prime * result + ((type == null) ? 0 : type.hashCode());
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
			Key other = (Key) obj;
			if (hash != other.hash)
				return false;
			if (type != other.type)
				return false;
			return true;
		}

		@Override
		public String toString() {
			return String.format("%s(0x%x)", type, hash);
		}
	}

	public ClusterBoundaryNode(long hash, MetaNodeType type) {
		super(new Key(hash, type));

		if ((type != MetaNodeType.CLUSTER_ENTRY) && (type != MetaNodeType.CLUSTER_EXIT))
			throw new IllegalArgumentException(String.format(
					"Cluster boundary node must have type %s or %s. Given type is %s.", MetaNodeType.CLUSTER_ENTRY,
					MetaNodeType.CLUSTER_EXIT, type));
	}

	@Override
	public ClusterModule getModule() {
		return BOUNDARY_MODULE;
	}

	@Override
	public int getRelativeTag() {
		return 0;
	}

	@Override
	public int getInstanceId() {
		return 0;
	}

	@Override
	public long getHash() {
		return key.hash;
	}

	@Override
	public MetaNodeType getType() {
		return key.type;
	}
	
	public String identify() {
		switch (key.type) {
			case CLUSTER_ENTRY:
			case CLUSTER_EXIT:
				return String.format("(0x%x|%s)", key.hash, key.type.code);
			default:
				throw new IllegalStateException(String.format("%s must be of type %s or %s",
						getClass().getSimpleName(), MetaNodeType.CLUSTER_ENTRY, MetaNodeType.CLUSTER_EXIT));
		}
	}

	@Override
	public String toString() {
		return identify();
	}
}
