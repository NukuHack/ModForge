package modforge.backend.model;

import java.util.List;
import java.util.stream.Collectors;

// Simple value object for buff parameters (stat_key op value)
public record BuffParam(String key, MathOperation operation, double value) {

	/**
	 * Serialize back to the game's attribute-string format, e.g. "Strength+5,Agility-2"
	 */
	public String toAttributeString() {
		return key + operation.getSymbol() + value;
	}

	@Override
	public String toString() {
		return toAttributeString();
	}

	public static String listToString(List<BuffParam> params) {
		if (params == null || params.isEmpty()) return "";
		return params.stream().map(BuffParam::toAttributeString).collect(Collectors.joining(","));
	}
}
