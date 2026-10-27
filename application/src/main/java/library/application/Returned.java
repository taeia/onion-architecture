package library.application;

import library.model.Money;

/**
 * What a driver needs to say that a copy came back: who had it, how many days late it
 * was, with 0 for on time, what that member owes now, and the member the copy is now
 * held for, or null if nobody was waiting.
 */
public record Returned(String member, long daysLate, Money debt, String heldFor) {
}
