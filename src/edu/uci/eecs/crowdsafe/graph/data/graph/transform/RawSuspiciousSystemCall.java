package edu.uci.eecs.crowdsafe.graph.data.graph.transform;

public class RawSuspiciousSystemCall {

	public static RawSuspiciousSystemCall parse(long rawData) {
		int suibCount = (int) ((rawData >> 8) & 0xffffL);
		int uibCount = (int) ((rawData >> 0x18) & 0xffffL);
		int sysnum = (int)((rawData >> 0x28) & 0xffffL);
		return new RawSuspiciousSystemCall(sysnum, uibCount, suibCount);
	}

	final int sysnum;
	final int uibCount;
	final int suibCount;
	
	private RawSuspiciousSystemCall(int sysnum, int uibCount, int suibCount) {
		this.sysnum = sysnum;
		this.uibCount = uibCount;
		this.suibCount = suibCount;
	}
}
