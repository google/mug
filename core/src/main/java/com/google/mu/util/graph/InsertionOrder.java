package com.google.mu.util.graph;

import java.util.Deque;

interface InsertionOrder {
  <N> void insertInto(Deque<N> deque, N value);
}