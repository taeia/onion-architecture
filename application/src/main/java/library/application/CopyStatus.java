package library.application;

/**
 * The three states of a copy, as the application ring reports them. The enum inside {@code Copy}
 * is the copy's own business; this one is what crosses the port.
 */
public enum CopyStatus {
    AVAILABLE, ON_LOAN, ON_HOLD
}
