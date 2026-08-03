package com.cobblemonpokerogue.bridge.api;

/** A milestone as configured in {@code milestones.json}. Tier is 1 (minor) to 3 (major). */
public record Milestone(String id, String display, int tier) {}
