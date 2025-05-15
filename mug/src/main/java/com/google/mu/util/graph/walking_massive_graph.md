# Walking Massive Graphs with BloomFilter-Based Tracking

When working with massive graphs—such as web link graphs, social networks, or distributed dependency graphs—the cost of tracking visited nodes can dominate the memory budget. Traditional `Set<T>` may not be able to hold all nodes in memory.

In scenarios where the graph is discovered lazily (e.g. via RPC or streaming edge discovery), a Bloom filter offers an appealing trade-off: sublinear memory in exchange for a controlled false positive rate.

`Walker` supports custom node tracker so this can be easily achieved using Guava `BloomFilter`:

```java
BloomFilter visited = ...;
Walker<WebPage> walker =
    Walker.inGraph(
        webPage -> webPage.exploreLinks(),
        page -> visited.mightContain(page.url()));
```

The bloom filter structure offers 0 false-negative so infinite loop is impossible, but the false
positives might result in certain nodes being discarded at low probability.

## The Problem with False Positives

Bloom filters occasionally return "seen" for a node that was never actually visited.
In the context of graph traversal, this can cause a issue:
**entire subgraphs may become permanently unreachable** if the only path to them is mistakenly pruned.

This is especially problematic during cold start, when the Bloom filter is relatively sparse but already
begins to emit false positives as nodes accumulate.

Can we mitigate it?

## Strategy: Probabilistically Trust Bloom

To mitigate this, we adopt a hybrid approach:

- Use a **Bloom filter** as the primary visited structure.
- Augment with a **Set<T>** that tracks a limited number of nodes at cold start.
- Define a **minimum threshold** (`minConfirmSize`) before trusting the Bloom filter alone.

### Lifecycle

1. **Cold Start Phase**:
   - During the first N visits (`confirmed.size() < minConfirmSize`),
     every node is added to both the `confirmed` set and the Bloom filter.
   - The Bloom filter is not yet trusted.

2. **Steady-State Phase**:
   - On visiting a node:
     - If Bloom says it hasn't seen the node, visit it.
     - If Bloom says it's seen the node, roll a dice to determine if we should trust Bloom.
       - If we trust Bloom, prune the node
       - Otherwise visit it

By configuring the probability (say 50%), we can achieve a few things:

1. A frequently reached node is unlikely to be false positively pruned.
2. Even if a few-visited node is false positively pruned this time, it won't be forever lost on the next run of the program.
3. The higher probability we configure, the more redundant processing we need to tolerate.

In massive, lazily-discovered graphs, this strategy balances correctness with scalability—and gives every path a second chance to succeed.
