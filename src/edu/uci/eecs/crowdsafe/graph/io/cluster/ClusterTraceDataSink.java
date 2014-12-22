package edu.uci.eecs.crowdsafe.graph.io.cluster;

import java.io.IOException;
import java.io.OutputStream;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianOutputStream;
import edu.uci.eecs.crowdsafe.graph.data.dist.AutonomousSoftwareDistribution;

public interface ClusterTraceDataSink {

	void addCluster(AutonomousSoftwareDistribution cluster, String filenameFormat);
	
	OutputStream getDataOutputStream(AutonomousSoftwareDistribution cluster, ClusterTraceStreamType streamType)
			throws IOException;

	LittleEndianOutputStream getLittleEndianOutputStream(AutonomousSoftwareDistribution cluster,
			ClusterTraceStreamType streamType) throws IOException;
}
