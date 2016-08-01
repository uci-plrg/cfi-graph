package edu.uci.plrg.cfi.x86.graph.data.graph.modular.metadata;

public class ModuleUIBInterval {

	public final EvaluationType type;
	public final int span; // log10 of the interval
	public final int count;
	public final int maxConsecutive;

	public ModuleUIBInterval(int typeId, int span, int count, int maxConsecutive) {
		this.type = EvaluationType.forId(typeId);
		this.span = span;
		this.count = count;
		this.maxConsecutive = maxConsecutive;
	}
}
