package edu.uci.eecs.crowdsafe.graph.data.graph.cluster;

import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.Node;

public class ClusterBasicBlock extends ClusterNode<ClusterBasicBlock.Key> {

	public static class Key<ClusterBasicBlock> implements Node.Key {
		public final ClusterModule module;

		public final long relativeTag;

		public final int instanceId;

		public Key(ClusterModule module, long relativeTag, int instanceId) {
			this.module = module;
			this.relativeTag = relativeTag;
			this.instanceId = instanceId;
		}

		@Override
		public boolean isModuleRelativeEquivalent(edu.uci.eecs.crowdsafe.graph.data.graph.Node.Key other) {
			if (other instanceof Key) {
				Key otherKey = (Key) other;
				return (relativeTag == otherKey.relativeTag) && module.equals(otherKey.module);
			}
			return false;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + instanceId;
			result = prime * result + ((module == null) ? 0 : module.hashCode());
			result = prime * result + (int) (relativeTag ^ (relativeTag >>> 32));
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			// TODO: strict "==" for anonymous nodes?
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (obj.getClass() == Key.class) {
				Key other = (Key) obj;
				if (instanceId != other.instanceId)
					return false;
				if (!module.equals(other.module))
					return false;
				if (relativeTag != other.relativeTag)
					return false;
				return true;
			} else {
				return false;
			}
		}

		@Override
		public String toString() {
			return String.format("%s(0x%x-i%d)", module.unit.name, relativeTag, instanceId);
		}
	}

	private final MetaNodeType type;

	private final long hash;

	public ClusterBasicBlock(Key key, long hash, MetaNodeType type) {
		super(key);
		this.type = type;
		this.hash = hash;

		if ((type == MetaNodeType.CLUSTER_ENTRY) && (type == MetaNodeType.CLUSTER_EXIT))
			throw new IllegalArgumentException(String.format(
					"Basic block node may not have type %s or %s. Given type is %s.", MetaNodeType.CLUSTER_ENTRY,
					MetaNodeType.CLUSTER_EXIT, type));
	}

	public ClusterBasicBlock(ClusterModule module, long relativeTag, int instanceId, long hash, MetaNodeType type) {
		this(new Key(module, relativeTag, instanceId), hash, type);
	}

	@Override
	public ClusterModule getModule() {
		return key.module;
	}

	@Override
	public int getRelativeTag() {
		return (int) key.relativeTag;
	}

	@Override
	public int getInstanceId() {
		return key.instanceId;
	}

	@Override
	public MetaNodeType getType() {
		return type;
	}

	@Override
	public long getHash() {
		return hash;
	}

	@Override
	public boolean isModuleRelativeEquivalent(Node<?> other) {
		if (!(other instanceof ClusterBasicBlock))
			return super.isModuleRelativeEquivalent(other);

		ClusterBasicBlock n = (ClusterBasicBlock) other;

		return (key.relativeTag == n.key.relativeTag) && key.module.isEquivalent(n.key.module)
				&& (getType() == n.getType()) && (getHash() == n.getHash());
	}

	@Override
	public boolean isModuleRelativeMismatch(Node<?> other) {
		if (!(other instanceof ClusterBasicBlock))
			return super.isModuleRelativeMismatch(other);

		ClusterBasicBlock n = (ClusterBasicBlock) other;
		if (key.module.unit.isAnonymous || n.key.module.unit.isAnonymous)
			return false;

		return !(key.relativeTag == n.key.relativeTag) && key.module.equals(n.key.module) && (getType() == n.getType())
				&& (getHash() == n.getHash());
	}

	public String identify() {
		return String.format("%s(0x%x-i%d|0x%x|%s)", key.module.unit.filename, key.relativeTag, key.instanceId, hash,
				type.code);
	}

	@Override
	public String toString() {
		return identify();
	}
}
