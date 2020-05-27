/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.trie;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class TrieIterator implements PathNodeVisitor {

  private final Deque<Bytes> paths = new ArrayDeque<>();
  private final LeafHandler leafHandler;
  private State state = State.SEARCHING;

  public TrieIterator(final LeafHandler leafHandler) {
    this.leafHandler = leafHandler;
  }

  @Override
  public Node visit(final ExtensionNode node, final Bytes searchPath) {
    Bytes remainingPath = searchPath;
    if (state == State.SEARCHING) {
      final Bytes extensionPath = node.getPath();
      final int commonPathLength = extensionPath.commonPrefixLength(searchPath);
      remainingPath = searchPath.slice(commonPathLength);
    }

    paths.push(node.getPath());
    node.getChild().accept(this, remainingPath);
    paths.pop();
    return node;
  }

  @Override
  public Node visit(final BranchNode node, final Bytes searchPath) {
    byte iterateFrom = 0;
    Bytes remainingPath = searchPath;
    if (state == State.SEARCHING) {
      iterateFrom = searchPath.get(0);
      if (iterateFrom == CompactEncoding.LEAF_TERMINATOR) {
        return node;
      }
      remainingPath = searchPath.slice(1);
    }
    paths.push(node.getPath());
    for (byte i = iterateFrom; i < BranchNode.RADIX && state.continueIterating(); i++) {
      paths.push(Bytes.of(i));
      node.child(i).accept(this, remainingPath);
      paths.pop();
    }
    paths.pop();
    return node;
  }

  @Override
  public Node visit(final LeafNode node, final Bytes path) {
    paths.push(node.getPath());
    state = State.CONTINUE;
    state = leafHandler.onLeaf(keyHash(), node);
    paths.pop();
    return node;
  }

  @Override
  public Node visit(final NullNode node, final Bytes path) {
    state = State.CONTINUE;
    return node;
  }

  private Bytes32 keyHash() {
    final Iterator<Bytes> iterator = paths.descendingIterator();
    Bytes fullPath = iterator.next();
    while (iterator.hasNext()) {
      fullPath = Bytes.wrap(fullPath, iterator.next());
    }
    return fullPath.isZero()
        ? Bytes32.ZERO
        : Bytes32.wrap(CompactEncoding.pathToBytes(fullPath), 0);
  }

  public interface LeafHandler {

    State onLeaf(Bytes32 keyHash, Node node);
  }

  public enum State {
    SEARCHING,
    CONTINUE,
    STOP;

    public boolean continueIterating() {
      return this != STOP;
    }
  }
}
