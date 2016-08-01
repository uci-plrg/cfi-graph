package edu.uci.plrg.cfi.x86.graph.data.graph;

import edu.uci.plrg.cfi.x86.graph.data.application.ApplicationModule;

public interface NodeIdentifier {

	long getHash();

	ApplicationModule getModule();

	int getRelativeTag();
	
	int getInstanceId();

	MetaNodeType getType();
}
