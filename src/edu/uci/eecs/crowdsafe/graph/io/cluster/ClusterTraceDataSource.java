package edu.uci.eecs.crowdsafe.graph.io.cluster;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.graph.data.dist.ApplicationModule;

public interface ClusterTraceDataSource {
	
	File getDirectory();
	
	Collection<ApplicationModule> getReprsentedModules();
	
	String parseTraceName();
	
	InputStream getDataInputStream(ApplicationModule cluster, ClusterTraceStreamType streamType)
			throws IOException;

	LittleEndianInputStream getLittleEndianInputStream(ApplicationModule cluster,
			ClusterTraceStreamType streamType) throws IOException;
}
