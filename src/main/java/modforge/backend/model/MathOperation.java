package modforge.backend.model;

public enum MathOperation {
	ADD_ABSOLUTE("+"),
	SUBTRACT_ABSOLUTE("-"),
	SET_ABSOLUTE("="),
	ADD_RELATIVE_TO_BASE("*"),
	MULTIPLY_CURRENT("%"),
	MINIMUM("<"),
	MAXIMUM(">"),
	NEGATE_RELATIVE_TO_VALUE("!");

	private final String symbol;

	MathOperation(String symbol) {
		this.symbol = symbol;
	}

	public String getSymbol() {
		return symbol;
	}

	public static MathOperation fromSymbol(String s) {
		for (var op : values()) {
			if (op.symbol.equals(s)) return op;
		}
		throw new IllegalArgumentException("Unknown operation symbol: " + s);
	}
}
