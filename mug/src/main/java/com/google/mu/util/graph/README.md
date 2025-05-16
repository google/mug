# Walker: Stream-Based Graph Traversal

`Walker` is a stream-based traversal utility designed to discover trees and graph structures in an on-demand fashion,
such as web link graphs or dependency DAGs.

This document summarizes its major capabilities and stream-based usage examples.

---

## Feature Summary

| Feature                        | Guava `Traverser`                                  | Mug `Walker`                                                        |
| ------------------------------ | -------------------------------------------------- | ------------------------------------------------------------------- |
| Pre-order traversal            | ✅ `Traverser.forTree(...).preOrderTraversal(...)`  | ✅ `preOrderFrom(...)`                                             |
| Post-order traversal           | ✅ `Traverser.forTree(...).postOrderTraversal(...)` | ✅ `postOrderFrom(...)`                                            |
| Breadth-first traversal        | ✅ `Traverser.forGraph(...).breadthFirst(...)`      | ✅ `breadthFirstFrom(...)`                                         |
| Binary tree in-order traversal | ❌                                                  | ✅ `inBinaryTree(...).inOrderFrom(...)`                            |
| Strongly connected components  | ❌                                                  | ✅ `stronglyConnectedComponentsFrom(...)`                          |
| Cycle detection                | ❌                                                  | ✅ `detectCycleFrom(...)`                                          |
| Topological order              | ❌                                                  | ✅ `topologicalOrderFrom(...)`                                     |
| Shortest path (weighted)       | ❌                                                  | ✅ `ShortestPath.shortestPathsFrom(...)`                           |
| Return value                   | `Iterable`                                          | `Stream`                                                           |

---

## Stream Usage Examples

### Breadth-first search with filtering
```java
List<Node> reachableSamples = Walker.inGraph(Node::getNeighborNodes)
    .breadthFirstFrom(startNode)
    .filter(this::isAcceptable)
    .limit(10)
    .toList();
```

### Topological ordering of a DAG
```java
List<Node> sorted = Walker.inGraph(Node::getDependencies)
    .topologicalOrderFrom(nodes);
```

### Shortest paths from a source node
```java
List<Location> nearbySushiPlaces = ShortestPath.shortestPathsFrom(myLocation, Location::locationsAroundMe)
    .map(ShortestPath::to)
    .filter(this::isSushiRestaurant)
    .limit(3)
    .toList();
```

---

## More Discussions

**➡️ [Finding a Path in a Graph](./find_path_in_graph.md)**
**➡️ [Walking a Super Massive Graph](./walking_massive_graph.md)**

