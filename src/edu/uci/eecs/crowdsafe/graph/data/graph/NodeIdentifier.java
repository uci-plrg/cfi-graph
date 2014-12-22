package edu.uci.eecs.crowdsafe.graph.data.graph;

import edu.uci.eecs.crowdsafe.graph.data.dist.SoftwareModule;

public interface NodeIdentifier {

	long getHash();

	SoftwareModule getModule();

	int getRelativeTag();
	
	int getInstanceId();

	MetaNodeType getType();
}
