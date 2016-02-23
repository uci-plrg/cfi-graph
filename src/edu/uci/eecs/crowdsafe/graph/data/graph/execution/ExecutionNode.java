package edu.uci.eecs.crowdsafe.graph.data.graph.execution;

import edu.uci.eecs.crowdsafe.common.exception.InvalidGraphException;
import edu.uci.eecs.crowdsafe.graph.data.graph.Edge;
import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeSet;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.Node;

/**
 * This is
 * 
 * @author peizhaoo
 * 
 */
public class ExecutionNode extends Node<ExecutionNode> {

	public static class Key implements Node.Key {
		public static Key create(long tag, int tagVersion, ModuleInstance module) {
			long relativeTag = (tag - module.start);
			if ((relativeTag < 0L) || (relativeTag > (module.end - module.start))) {
				throw new InvalidGraphException("Relative tag 0x%x is outside the module's relative bounds [0-%d]",
						relativeTag, (module.end - module.start));
			}

			return new Key(relativeTag, tagVersion, module);
		}

		public final long relativeTag;

		public final int version;

		public final ModuleInstance module;

		private Key(long relativeTag, int tagVersion, ModuleInstance module) {
			this.relativeTag = relativeTag;
			this.version = tagVersion;
			this.module = module;
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
			result = prime * result + ((module == null) ? 0 : module.hashCode());
			result = prime * result + (int) (relativeTag ^ (relativeTag >>> 32));
			result = prime * result + version;
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
			if (module == null) {
				if (other.module != null)
					return false;
			} else if (!module.equals(other.module))
				return false;
			if (relativeTag != other.relativeTag)
				return false;
			if (version != other.version)
				return false;
			return true;
		}

		@Override
		public String toString() {
			return String.format("%s(0x%x-v%d)", module.filename, relativeTag, version);
		}
	}

	private final long timestamp;

	private final Key key;

	private final long hash;

	private final MetaNodeType metaNodeType;

	public ExecutionNode(ModuleInstance module, MetaNodeType metaNodeType, long tag, int tagVersion, long hash,
			long timestamp) {
		Key key;
		switch (metaNodeType) {
			case MODULE_ENTRY:
			case MODULE_EXIT:
				key = new Key(hash, 0, module);
				break;
			default:
				key = Key.create(tag, tagVersion, module);
		}
		this.key = key;
		this.metaNodeType = metaNodeType;
		this.hash = hash;
		this.timestamp = timestamp;
	}

	@Override
	public ExecutionNode.Key getKey() {
		return key;
	}

	@Override
	public boolean isModuleRelativeEquivalent(Node<?> other) {
		if (!(other instanceof ExecutionNode))
			return super.isModuleRelativeEquivalent(other);

		ExecutionNode n = (ExecutionNode) other;
		return (key.relativeTag == n.key.relativeTag) && key.module.isEquivalent(n.key.module)
				&& (getType() == n.getType()) && (getHash() == n.getHash());
	}

	@Override
	public boolean isModuleRelativeMismatch(Node<?> other) {
		if (!(other instanceof ExecutionNode))
			return super.isModuleRelativeMismatch(other);

		ExecutionNode n = (ExecutionNode) other;
		if (key.module.isAnonymous || n.key.module.isAnonymous)
			return false;

		return !(key.relativeTag == n.key.relativeTag) && key.module.equals(n.key.module) && (getType() == n.getType())
				&& (getHash() == n.getHash());
	}

	public String identify() {
		switch (metaNodeType) {
			case MODULE_ENTRY:
				return String.format("ClusterEntry(0x%x)", hash);
			case MODULE_EXIT:
				return String.format("ClusterExit(0x%x)", hash);
			default:
				return String.format("%s(0x%x-v%d|0x%x)", key.module.filename, key.relativeTag, key.version, hash);
		}
	}

	public int getRelativeTag() {
		return (int) key.relativeTag;
	}

	@Override
	public int getInstanceId() {
		return key.version;
	}

	public ModuleInstance getModule() {
		return key.module;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public ExecutionNode changeHashCode(long newHash) {
		return new ExecutionNode(key.module, metaNodeType, key.module.start + key.relativeTag, key.version, newHash,
				timestamp);
	}

	public void addIncomingEdge(Edge<ExecutionNode> e) {
		edges.addEdge(EdgeSet.Direction.INCOMING, e);
	}

	public void addOutgoingEdge(Edge<ExecutionNode> e) {
		edges.addEdge(EdgeSet.Direction.OUTGOING, e);
	}

	public MetaNodeType getType() {
		return this.metaNodeType;
	}

	public long getHash() {
		return hash;
	}

	/**
	 * In a single execution, tag combined with the version number is the only identifier for the normal nodes. This is
	 * particularly used in the initialization of the graph, where hashtables are needed.
	 * 
	 * For signature nodes, the node should be empty if they are both signature nodes and they have the same hash
	 * signature.
	 */
	// 5% hot during load!
	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (o.getClass() != getClass()) {
			return false;
		}
		ExecutionNode node = (ExecutionNode) o;
		if ((node.metaNodeType == metaNodeType)
				&& ((metaNodeType == MetaNodeType.MODULE_ENTRY) || (metaNodeType == MetaNodeType.MODULE_EXIT))) {
			return (node.hash == hash);
		}

		return node.key.equals(key);
	}

	public int hashCode() {
		switch (metaNodeType) {
			case MODULE_ENTRY:
			case MODULE_EXIT:
				final int prime = 31;
				int result = super.hashCode();
				result = prime * result + (int) (hash ^ (hash >>> 32));
				return result;
			default:
				return key.hashCode();
		}
	}

	@Override
	public String toString() {
		return identify();
	}
}