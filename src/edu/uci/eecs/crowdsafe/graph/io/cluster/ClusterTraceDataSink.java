package edu.uci.eecs.crowdsafe.graph.io.cluster;

import java.io.IOException;
import java.io.OutputStream;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianOutputStream;
import edu.uci.eecs.crowdsafe.graph.data.dist.ApplicationModule;

public interface ClusterTraceDataSink {

	void addCluster(ApplicationModule cluster, String filenameFormat);
	
	OutputStream getDataOutputStream(ApplicationModule cluster, ClusterTraceStreamType streamType)
			throws IOException;

	LittleEndianOutputStream getLittleEndianOutputStream(ApplicationModule cluster,
			ClusterTraceStreamType streamType) throws IOException;
}
