/**
 * List prices used for the admin page's cost estimate, in US dollars per
 * million tokens. They are a convenience for a glance, not a bill: check
 * the provider's pricing page and update these two numbers when the model
 * (tracker.ai.model) or its price changes. The page prints the rates it
 * assumed next to the estimate so nobody mistakes it for an invoice.
 */
export const PRICE_PER_MILLION_TOKENS = {
  input: 5,
  output: 25,
};

export function estimateCost(inputTokens: number, outputTokens: number): number {
  return (inputTokens * PRICE_PER_MILLION_TOKENS.input + outputTokens * PRICE_PER_MILLION_TOKENS.output) / 1_000_000;
}
