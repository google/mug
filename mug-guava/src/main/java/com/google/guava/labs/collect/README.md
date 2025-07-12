# 🧭 Solving LeetCode with [`BinarySearch`](https://google.github.io/mug/apidocs/com/google/mu/collect/BinarySearch.html)

Binary search is a classic algorithm—but writing it correctly is surprisingly hard:

- Off-by-one errors are common
- Edge cases are messy  
- Loop termination and insertion logic are easy to get wrong (**infinite loop!**)
- Be careful with overflow

This post shows how to solve **three representative LeetCode problems** using the safe and declarative `BinarySearch` class — with no manual loop logic, and no risk of infinite loops.

---

## 🔍 Understanding [`insertionPointFor(...)`](https://google.github.io/mug/apidocs/com/google/mu/collect/BinarySearch.Table.html#insertionPointFor(K)) and How It Works

At the core of `BinarySearch.Table` is this method:

```java
insertionPointFor((lo, mid, hi) -> ...)
```

You're responsible for telling the search which direction to go at each probe point `mid`.

TIP: For everyday use, there are simpler methods like `find()` and `rangeOf()`. The `insertionPointFor()` is for advanced binary search problems.

---

### ❗ Two styles of binary search

`BinarySearch.Table` supports two distinct binary search patterns:

---

#### 1. **Target-based search** (classic)

```java
insertionPointFor((lo, mid, hi) -> compare(target, mid));
```

This is the traditional form of binary search—looking for an exact match in a sorted array or list.

Use this when you're trying to locate a specific value.  
Returning `0` indicates a match, `< 0` means go left, and `> 0` means go right.

---

#### 2. **Condition-based optimization search**

```java
insertionPointFor((lo, mid, hi) -> condition(mid) ? -1 : 1);
```

In this form, you're **not searching for a specific value**, but for the **minimum or maximum value for a condition to still hold**.

This works best when:

- The condition is monotonic (once true, always true—or once false, always false)
- You're trying to solve optimization problems like:
  - The smallest feasible solution
  - The largest possible value before something breaks

For example:

```java
insertionPointFor((lo, mid, hi) -> canShipWithinDays(mid) ? -1 : 1);
```

This guides the search toward the **smallest `x` where the condition holds**, i.e., the minimum required capacity.

Return `-1` to search left (try smaller), `1` to search right (try larger).

---

No need to return `0` in this style—you only care about direction, not equality.

---

## 🍌 Example 1: 875. Koko Eating Bananas

> Find the minimum eating speed that allows Koko to finish all bananas within `h` hours.

```java
int minSpeed(List<Integer> piles, int h) {
  return BinarySearch.forInts(Range.closed(1, max(piles)))
      // if Koko can finish, eat slower, else eat faster
      .insertionPointFor((lo, speed, hi) -> canFinish(piles, speed, h) ? -1 : 1)
      // floor() is the max speed that can't finish; ceiling() is the min that can
      .ceiling();
}

boolean canFinish(List<Integer> piles, int speed, int h) {
  int time = 0;
  for (int pile : piles) {
    time += (pile + speed - 1) / speed;
  }
  return time <= h;
}
```

---

## 🧮 Example 2: 69. Integer Square Root

> Compute `floor(sqrt(x))` without using built-in functions.

```java
int mySqrt(int x) {
  return BinarySearch.forInts(Range.closed(0, x))
      // if square is larger, try smaller sqrt
      .insertionPointFor((lo, mid, hi) -> Long.compare(x, (long) mid * mid))
      // floor() is the max whose square <= x
      .floor();
}
```

This returns the largest `r` such that `r² <= x`.

---

## 📦 Example 3: 410. Split Array Largest Sum

> Split `nums[]` into `k` parts, minimizing the largest sum among parts.

```java
int splitArray(int[] nums, int k) {
  int total = Arrays.stream(nums).sum();
  int max = Arrays.stream(nums).max().getAsInt();
  return BinarySearch.forInts(Range.closed(max, total))
      // if canSplit, try smaller sum, else try larger sum
      .insertionPointFor((lo, maxSum, hi) -> canSplit(nums, maxSum, k) ? -1 : 1)
      // floor is the largest that can't split, ceiling is the min that can
      .ceiling();
}

boolean canSplit(int[] nums, int maxSum, int k) {
  int count = 1, sum = 0;
  for (int num : nums) {
    if (sum + num > maxSum) {
      count++;
      sum = 0;
    }
    sum += num;
  }
  return count <= k;
}
```

We use `.ceiling()` to find the **smallest feasible maximum subarray sum**.

---

## 📌 Type and Return Value Summary

Each `BinarySearch.Table` variant should match the nature of the problem:

| Problem                        | Domain Type | Comparison Strategy                          | Return       | Reason                              |
|-------------------------------|-------------|----------------------------------------------|--------------|-------------------------------------|
| Koko Eating Bananas           | `forInts`   | `canFinish(speed) ? -1 : 1`                  | `.ceiling()` | smallest speed that canFinish()       |
| Integer Square Root           | `forInts`   | `Long.compare(x, (long) mid * mid)`          | `.floor()`   | largest number n where n*n <= x |
| Split Array Largest Sum       | `forInts`   | `canSplit(maxSum) ? -1 : 1`                  | `.ceiling()` | smallest sum that canSplit()  |

### Notes:
- `forInts()` when the domain is naturally integer-bounded. There are also `forLongs()` and `forDoubles()`.
- [`forDoubles()`](https://google.github.io/mug/apidocs/com/google/mu/collect/BinarySearch.html#forDoubles()) can be used to search the entire double value space. IEEE 754 guarantees convergence within 64 iterations without needing an epsilon.
- For more LeetCode binary search solutions, see [`LeetCodeTest`](https://github.com/google/mug/blob/master/mug-guava/src/test/java/com/google/mu/collect/LeetCodeTest.java).

## Can you use it?

Obviously you can't directly use it on leetcode (there's no Maven to pull in third-party libs). But you could still use it locally as a prototyping tool.

Explore your binary search idea quickly, make sure the logic is sound, *then* convert to a manual loop. It also helps you think in isolation.

For interviewers: are you evaluating the candidate's thinking process and abstraction capability? If so, being able to express the algorithm clearly (in terms of `BinarySearch`) already proves that. If you rarely have to deal with this level of nuances and edge cases at work (such code would require extensive reviews), why should the candicate?

## Final Thoughts

Mastering the **thinking model of binary search** is absolutely worthwhile.
The ability to structure a search around monotonicity and directional reasoning
is a form of higher-order abstraction that's fun and useful.

But you shouldn't have to spend your time wrestling with
off-by-one bugs, loop bounds, or whether to do `high = mid` or `low = mid + 1`.
Those are tedious, error prone and anything but fun (nor practically useful).
They drag you down into the mud of mind-numbing, imperative twiddling.

The BinarySearch class aims to get those details done once and for all, so you can focus on the fun and creative part of binary search.

---
