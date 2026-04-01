package modforge.backend.service;

import java.util.ArrayDeque;
import java.util.Deque;

public final class NavigationService {
	private final Deque<String> back = new ArrayDeque<>();
	private final Deque<String> forward = new ArrayDeque<>();
	private String current;

	@FunctionalInterface
	public interface NavigationListener {
		void onNavigate(String uri);
	}

	private NavigationService.NavigationListener listener;

	public NavigationService(String initialUri) {
		this.current = initialUri;
	}

	public void setNavigationListener(NavigationService.NavigationListener l) {
		this.listener = l;
	}

	public boolean canGoBack() {
		return !back.isEmpty();
	}

	public boolean canGoForward() {
		return !forward.isEmpty();
	}

	public String getCurrent() {
		return current;
	}

	public void navigateTo(String uri) {
		if (current != null) back.push(current);
		forward.clear();
		current = uri;
		fire(uri);
	}

	public void goBack() {
		if (!canGoBack()) return;
		forward.push(current);
		current = back.pop();
		fire(current);
	}

	public void goForward() {
		if (!canGoForward()) return;
		back.push(current);
		current = forward.pop();
		fire(current);
	}

	private void fire(String uri) {
		if (listener != null) listener.onNavigate(uri);
	}
}
