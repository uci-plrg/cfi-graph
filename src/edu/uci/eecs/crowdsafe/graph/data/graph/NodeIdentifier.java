package edu.uci.eecs.crowdsafe.graph.data.graph;

import edu.uci.eecs.crowdsafe.graph.data.application.ApplicationModule;

public interface NodeIdentifier {

	long getHash();

	ApplicationModule getModule();

	int getRelativeTag();
	
	int getInstanceId();

	MetaNodeType getType();
}
