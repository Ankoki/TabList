package hu.montlikadani.tablist.tablist.groups;

import hu.montlikadani.tablist.tablist.TabText;

public class TeamHandler {

	public String name = "", permission = "";
	public transient TabText prefix, suffix, tabName = TabText.EMPTY;

	public boolean global;

	public int priority;
	private int afkSortPriority = -1;

	public TeamHandler() {
	}

	public TeamHandler(String name, TabText prefix, TabText suffix) {
		this.name = name == null ? "" : name;
		this.prefix = prefix;
		this.suffix = suffix;
	}

	public TeamHandler(String name, TabText prefix, TabText suffix, String permission, int priority) {
		this(name, prefix, suffix);

		this.permission = permission == null ? "" : permission;
		this.priority = priority;
	}

	public void setAfkSortPriority(int afkSortPriority) {
		if (afkSortPriority >= -1) {
			this.afkSortPriority = afkSortPriority;
		}
	}

	public int getAfkSortPriority() {
		return afkSortPriority;
	}
}
