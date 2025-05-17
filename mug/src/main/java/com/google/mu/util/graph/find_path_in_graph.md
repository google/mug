# To Find a Path in a Graph

## Problem: Discovering Unsafe Web Link Paths

Suppose we want to check whether a company's official site can indirectly link to any known phishing domain.

We already have a `WebPageUrlService` that looks like:

```
List<String> fetchPageUrls(String url);
```

And we have a `Set<String> knownPhishingSites` passed in as parameter.

To do this, we start from a list of company web page urls, and for each webpage,
we call the service to fetch all the links on that page, rinse and repeat,
until we run into one of known phishing web sites.

Our goal is to be efficient and only call the service once for each url.

Before we begin, the main challenge in this is that we don't just need to return a boolean to
to indicate reachability: we need the full path from which one of the company web pages,
along with all the intermediary web pages until it reaches the phishing site.

---

## Every computer problem starts with data modeling. We need to first model every web page we visit.

We will need to keep track of which link has led us to the current page.

This parent information is crucial for reconstructing the full url path once we've found the phishing site.

But during the traversal, most of the visited pages won't need to reconstruct the url path, which is
expensive.

```java {.good}
record VisitedPage(String url, ReferredPage referrer) {
  static VisitedPage from(String root) {
    return new VisitedPage(root, null);
  }

  /** RPC: explore the links from this page. */
  Stream<VisitedPage> exploreLinks() {
    return fetchLinks(current).stream()
        .map(child -> new VisitedPage(child, this));
  }

  /** Reconstruct the url path from the root. This is O(path length). */
  List<String> urlPath() {
    List<String> path = new ArrayList<>();
    for (VisitedPage p = this; p != null; p = p.parent) {
        path.add(p.current);
    }
    Collections.reverse(path);
    return path;
  }
}
```

---

## Traversing and Reconstructing the Path

Next we'll use the Mug `Walker` class to traverse the url graph, in a breadth first order.
The `inGraph()` method will keep track of nodes that have been visited, so we won't run ito cycles.


```java
List<VisitedPage> rootPages = officialCompanyUrls.stream().map(VisitedPage::from).toList();
Optional<VisitedPage> result = Walker.inGraph(VisitedPage::exploreLinks)
    .breadthFirstFrom(rootPages)
    // assume we have a small helper to parse the domain from a url
    .filter(page -> knownPhishingSites.contains(parseDomain(page))
    .findFirst();
```

The idea is that we will start from the root pages, traverse the graph and at each step, keep
track of which url has led us to it. So then when we finally reaches a phishing site domain,
we can simply call:

```java
result.ifPresent(
   page -> System.out.println("Found a path: " + page.urlPath()));
```

Except, there is one problem: the `VisitedPage` record would compare both the current page url *and the url that leads to it*,
which means if we are visiting from two urls to the same web page, Walker won't recognize that the page has been visited
and thus will re-explore the page, wasting resource and end up calling the service multiple times for the same url.

## Walker Functional Equivalence

Luckily, `Walker` allows functional equivalence, we can change the `inGraph()` as the following:

```java {.good}
List<VisitedPage> rootPages = officialCompanyUrls.stream().map(VisitedPage::from).toList();
Set<String> visitedUrls = new HashSet<>();
Optional<VisitedPage> result =
    Walker.inGraph(VisitedPage::exploreLinks, page -> visitedUrls.add(page.url()))
        .breadthFirstFrom(rootPages)
        // assume we have a small helper to parse the domain from a url
        .filter(page -> knownPhishingSites.contains(parseDomain(page))
        .findFirst();
```

By using the `visitedUrls` from each page's url, we can still detect already visited urls.

Problem solved.

## Concurrent Graph Walking

With IO (calling the WebPageUrlService) being the bottleneck, it may make sense to crawl the pages
concurrently, as long as the service tolerates the extra load.

To make the code thread safe, we need to use `ConcurrentHashSet.newKeySet()`.
Each thread can then create their own Walker instance, sharing the same concurrent set instance
in the lambda passed to `inGraph()`.

The concurrent set will ensure that no two threads will attempt to visit the same url.

---

## Traverser

It's worth noting that we could also use Guava's `Traverser` to solve it.

The code will be quite similar (Traverser returns `Iterable` but if we want we can turn it into `Stream` easily).

Although, Traverser doesn't support functional equivalence,
so we cannot use our `VisitedPage` record to reconstruct the path.

Instead, we might need to cache the child -> parent mapping in the lambda passed to `Traverser.forGraph()`.

And then we need to reconstruct the path using the cached mapping.

---

## Recap

- Track state in an immutable, lightweight object
  that can be used to reconstruct the path later.
- No need for extra data structure.
- Walker returns `Stream` so you can more easily `filter()`, `map()`, `findFirst()` etc.
