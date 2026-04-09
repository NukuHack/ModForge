package com.nukuhack.modforge.backend.model.storm;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * A custom selector declared at the top of a STORM file
 * (i.e. a named selector template, not a usage instance).
 */
@Getter
@lombok.extern.slf4j.Slf4j
public final class CustomStormSelector {
	
	/** Attribute names defined by this custom selector. */
	private final List<String> attributeNames = new ArrayList<>();
	private String name = "";
	private String comment = "";
	
	public void setName(String name) {
		this.name = name == null ? "" : name;
	}
	
	public void setComment(String comment) {
		this.comment = comment == null ? "" : comment;
	}
	
	@Override
	public String toString() {
		return "CustomStormSelector{name='" + name + "'}";
	}
}
