package edu.uci.eecs.crowdsafe.graph.io.execution;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;

public interface ExecutionTraceDataSource {

	File getDirectory();
	
	int getProcessId();

	String getProcessName();

	InputStream getDataInputStream(ExecutionTraceStreamType streamType) throws IOException;

	LittleEndianInputStream getLittleEndianInputStream(ExecutionTraceStreamType streamType) throws IOException;
}
