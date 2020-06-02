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

import static org.hyperledger.besu.crypto.Hash.keccak256;

import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

class LeafNode implements Node {
  private final Bytes path;
  private final Bytes value;
  private final NodeFactory nodeFactory;
  private WeakReference<Bytes> rlp;
  private SoftReference<Bytes32> hash;
  private boolean dirty = false;

  LeafNode(final Bytes path, final Bytes value, final NodeFactory nodeFactory) {
    this.path = path;
    this.value = value;
    this.nodeFactory = nodeFactory;
  }

  @Override
  public Stream<Bytes32> accept(final StreamNodesVisitor visitor) {
    return visitor.visit(this);
  }

  @Override
  public Node accept(final PathNodeVisitor visitor, final Bytes path) {
    return visitor.visit(this, path);
  }

  @Override
  public void accept(final NodeVisitor visitor) {
    visitor.visit(this);
  }

  @Override
  public Bytes getPath() {
    return path;
  }

  @Override
  public Optional<Bytes> getValue() {
    return Optional.of(value);
  }

  @Override
  public List<Node> getChildren() {
    return Collections.emptyList();
  }

  @Override
  public Bytes getRlp() {
    if (rlp != null) {
      final Bytes encoded = rlp.get();
      if (encoded != null) {
        return encoded;
      }
    }

    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeBytes(CompactEncoding.encode(path));
    out.writeBytes(value);
    out.endList();
    final Bytes encoded = out.encoded();
    rlp = new WeakReference<>(encoded);
    return encoded;
  }

  @Override
  public Bytes getRlpRef() {
    if (isReferencedByHash()) {
      return RLP.encodeOne(getHash());
    } else {
      return getRlp();
    }
  }

  @Override
  public Bytes32 getHash() {
    if (hash != null) {
      final Bytes32 hashed = hash.get();
      if (hashed != null) {
        return hashed;
      }
    }
    final Bytes32 hashed = keccak256(getRlp());
    hash = new SoftReference<>(hashed);
    return hashed;
  }

  @Override
  public Node replacePath(final Bytes path) {
    return nodeFactory.createLeaf(path, value);
  }

  @Override
  public String print() {
    return "Leaf:"
        + "\n\tRef: "
        + getRlpRef()
        + "\n\tPath: "
        + CompactEncoding.encode(path)
        + "\n\tValue: "
        + getValue().map(Object::toString).orElse("empty");
  }

  @Override
  public boolean isDirty() {
    return dirty;
  }

  @Override
  public void markDirty() {
    dirty = true;
  }
}
