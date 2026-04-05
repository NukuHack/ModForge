package modforge.backend.model.attributes;

import modforge.backend.model.MathOperation;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Simple value object for buff parameters (stat_key op value)
public non-sealed class BuffParam extends BaseAttribute<BuffParam.Param> {
	
	private static final Pattern BUFF_PARAM_RE = Pattern.compile("(\\w+)([+\\-=*%<>!])([\\-+]?\\d+(?:\\.\\d+)?)");
	
	protected BuffParam(String name, Param value) {
		super(name, value);
	}
	
	public BuffParam(String name, MathOperation operation, double value) {
		super(name, new Param(operation, value));
	}
	
	// Parse the compact game format:  "Strength+5,Agility-2.5,Charisma=10"
	public static BuffParam fromString(String part) {
		if (part == null || (part = part.trim()).isEmpty())
			return null;
		final var m = BUFF_PARAM_RE.matcher(part);
		if (m.find()) {
			return new BuffParam(m.group(1), MathOperation.fromSymbol(m.group(2)), Double.parseDouble(m.group(3)));
		} else {
			return new BuffParam(part, MathOperation.SET, 1);
		}
	}
	
	public static List<BuffParam> parse(final String data) {
		return Arrays.stream(data.split(",")).map(BuffParam::fromString).filter(Objects::nonNull).toList();
	}
	
	public static String toAttrString(List<BuffParam> params) {
		if (params == null || params.isEmpty())
			return "";
		return params.stream().map(b -> b.toAttrString()).collect(Collectors.joining(","));
	}
	
	public static String toAttrString(BuffParam params) {
		if (params == null)
			return "";
		return params.toAttrString();
	}
	
	/**
	 * Serialize back to the game's attribute-string format, e.g. "Strength+5,Agility-2"
	 */
	public String toAttrString() {
		return this.name + this.value.operation.getSymbol() + this.value.value;
	}
	
	@Override
	public BuffParam deepClone() {
		return this; // value is immutable, returning as is, is fine
	}
	
	@Override
	public BuffParam deepClone(Param newValue) {
		return new BuffParam(name, newValue);
	}
	
	@Override
	public String toString() {
		return toAttrString();
	}
	
	public record Param(MathOperation operation, double value) {
	}
}
