package app.wordfeudwordfinder.extension.core;

/** Build constants. */
public final class BuildInfo {
    /**
     * Patch version shown in "Copy details" (spec section 10). Hand-kept: the release pipeline (semantic-release)
     * does not update it, so it stays "dev" until a build step generates it from {@code gradle.properties}.
     */
    public static final String PATCH_VERSION = "dev";

    private BuildInfo() {}
}
