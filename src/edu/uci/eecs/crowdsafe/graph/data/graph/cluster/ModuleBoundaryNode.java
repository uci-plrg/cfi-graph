package edu.uci.eecs.crowdsafe.graph.data.graph.cluster;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.uci.eecs.crowdsafe.graph.data.dist.ApplicationModule;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.Node;
import edu.uci.eecs.crowdsafe.graph.util.CrowdSafeTraceUtil;

public class ModuleBoundaryNode extends ModuleNode<ModuleBoundaryNode.Key> {

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

	public enum HashLabelProperty {
		HASH_LABEL_FROM_ANONYMOUS,
		HASH_LABEL_TO_ANONYMOUS,
		HASH_LABEL_CALLBACK,
		HASH_LABEL_INTERCEPTION,
		HASH_LABEL_GENCODE;
	}

	public static class HashLabel {

		public static HashLabel createAnonymousEntry(String fromFilename) {
			String label = String.format("%s/<anonymous>!callback", fromFilename);
			long hash = CrowdSafeTraceUtil.stringHash(label);

			HashLabel hashLabel = new HashLabel(label, hash, 0, fromFilename, "anonymous");
			hashLabel.properties.add(HashLabelProperty.HASH_LABEL_TO_ANONYMOUS);
			hashLabel.properties.add(HashLabelProperty.HASH_LABEL_CALLBACK);
			return hashLabel;
		}

		public static HashLabel createAnonymousExit(String toFilename) {
			String label = String.format("<anonymous>/%s!callback", toFilename);
			long hash = CrowdSafeTraceUtil.stringHash(label);

			HashLabel hashLabel = new HashLabel(label, hash, 0, "anonymous", toFilename);
			hashLabel.properties.add(HashLabelProperty.HASH_LABEL_FROM_ANONYMOUS);
			hashLabel.properties.add(HashLabelProperty.HASH_LABEL_CALLBACK);
			return hashLabel;
		}

		public static HashLabel createGencodeEntry(String fromFilename) {
			String label = String.format("%s/<anonymous>!gencode", fromFilename);
			long hash = CrowdSafeTraceUtil.stringHash(label);

			HashLabel hashLabel = new HashLabel(label, hash, 0, fromFilename, "anonymous");
			hashLabel.properties.add(HashLabelProperty.HASH_LABEL_TO_ANONYMOUS);
			hashLabel.properties.add(HashLabelProperty.HASH_LABEL_GENCODE);
			return hashLabel;
		}

		public static HashLabel createInterception(String fromFilename) {
			String label = String.format("%s!interception", fromFilename);
			long hash = CrowdSafeTraceUtil.stringHash(label);

			HashLabel hashLabel = new HashLabel(label, hash, 0, null, null);
			hashLabel.properties.add(HashLabelProperty.HASH_LABEL_INTERCEPTION);
			return hashLabel;
		}

		private static final Pattern ENTRY_PATTERN = Pattern.compile("^(0x[0-9a-f]+) ([^ ]+) (0x[0-9a-f]+)$");

		public final String label;
		public final long hash;
		public final int offset;
		public final String fromModuleName;
		public final String toModuleName;

		private final Set<HashLabelProperty> properties = EnumSet.noneOf(HashLabelProperty.class);

		private HashLabel(String label, long hash, int offset, String fromModuleName, String toModuleName) {
			this.label = label;
			this.hash = hash;
			this.offset = offset;
			this.fromModuleName = fromModuleName;
			this.toModuleName = toModuleName;
		}

		public HashLabel(String xhashEntry) {
			Matcher matcher = ENTRY_PATTERN.matcher(xhashEntry);

			hash = Long.parseLong(matcher.group(1), 0x10);
			label = matcher.group(2);
			offset = Integer.parseInt(matcher.group(3), 0x10);

			int slashIndex = label.indexOf('/');
			if (slashIndex < 0) {
				fromModuleName = toModuleName = null;
			} else {
				fromModuleName = label.substring(0, slashIndex);
				toModuleName = label.substring(slashIndex + 1, label.indexOf('!'));
			}

			if (label.startsWith("anonymous/"))
				properties.add(HashLabelProperty.HASH_LABEL_FROM_ANONYMOUS);
			if (label.contains("/anonymous"))
				properties.add(HashLabelProperty.HASH_LABEL_TO_ANONYMOUS);
			if (label.endsWith("!callback"))
				properties.add(HashLabelProperty.HASH_LABEL_CALLBACK);
			if (label.endsWith("!gencode"))
				properties.add(HashLabelProperty.HASH_LABEL_GENCODE);
		}

		public boolean isFromAnonymous() {
			return properties.contains(HashLabelProperty.HASH_LABEL_FROM_ANONYMOUS);
		}

		public boolean isToAnonymous() {
			return properties.contains(HashLabelProperty.HASH_LABEL_TO_ANONYMOUS);
		}
	}

	public ModuleBoundaryNode(long hash, MetaNodeType type) {
		super(new Key(hash, type));

		if ((type != MetaNodeType.MODULE_ENTRY) && (type != MetaNodeType.MODULE_EXIT))
			throw new IllegalArgumentException(String.format(
					"Cluster boundary node must have type %s or %s. Given type is %s.", MetaNodeType.MODULE_ENTRY,
					MetaNodeType.MODULE_EXIT, type));
	}

	@Override
	public ApplicationModule getModule() {
		return ApplicationModule.BOUNDARY_MODULE;
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
			case MODULE_ENTRY:
			case MODULE_EXIT:
				return String.format("(0x%x|%s)", key.hash, key.type.code);
			default:
				throw new IllegalStateException(String.format("%s must be of type %s or %s",
						getClass().getSimpleName(), MetaNodeType.MODULE_ENTRY, MetaNodeType.MODULE_EXIT));
		}
	}

	@Override
	public String toString() {
		return identify();
	}
}
