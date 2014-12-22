package edu.uci.eecs.crowdsafe.graph.util;

import edu.uci.eecs.crowdsafe.graph.data.graph.EdgeType;
import edu.uci.eecs.crowdsafe.graph.data.graph.MetaNodeType;

public class CrowdSafeTraceUtil {

	// Return ordinal of the edge by passing the from tag
	public static int getEdgeOrdinal(long tag) {
		return (int) ((tag >>> 0x28) & 0xff);
	}

	// Return type of the edge by passing the from tag
	public static EdgeType getTagEdgeType(long tag) {
		return EdgeType.values()[(int) ((tag >>> 0x30) & 0xff)];
	}

	public static MetaNodeType getNodeMetaType(long tag) {
		return MetaNodeType.values()[(int) ((tag >>> 0x30) & 0xff)];
	}

	// get the lower 4 byte of the tag, which is a long integer
	public static long getTag(long annotatedTag) {
		return annotatedTag & 0xffffffffL;
	}

	public static int getTagVersion(long annotatedTag) {
		return (int) (annotatedTag >>> 0x38);
	}

	public static long stringHash(String string) {
		long hash = 0L;
		for (int i = 0; i < string.length(); i++) {
			hash = hash ^ (hash << 5) ^ ((int) string.charAt(i));
		}
		return hash;
	}
}
