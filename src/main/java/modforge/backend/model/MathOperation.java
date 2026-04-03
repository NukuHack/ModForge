package modforge.backend.model;

public enum MathOperation {
	ADD("+"),
	SUBTRACT("-"),
	SET("="),
	MULTIPLY("*"),
	DIVIDE("%"),
	MINIMUM("<"),
	MAXIMUM(">"),
	NEGATE("!");
	
	private final String symbol;
	
	MathOperation(String symbol) {
		this.symbol = symbol;
	}
	
	public static MathOperation fromSymbol(String s) {
		for (var op : values()) {
			if (op.symbol.equals(s))
				return op;
		}
		throw new IllegalArgumentException("Unknown operation symbol: " + s);
	}
	
	public String getSymbol() {
		return symbol;
	}
}
