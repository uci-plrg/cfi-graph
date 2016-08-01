package edu.uci.plrg.cfi.x86.graph.io.execution;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import edu.uci.plrg.cfi.common.io.LittleEndianInputStream;

public interface ExecutionTraceDataSource {

	File getDirectory();
	
	int getProcessId();

	String getProcessName();
	
	boolean hasStreamType(ExecutionTraceStreamType streamType);

	InputStream getDataInputStream(ExecutionTraceStreamType streamType) throws IOException;

	LittleEndianInputStream getLittleEndianInputStream(ExecutionTraceStreamType streamType) throws IOException;
}
