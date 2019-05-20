/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.common.node.utils;

import me.lucko.luckperms.api.Tristate;
import me.lucko.luckperms.api.node.Node;
import me.lucko.luckperms.api.node.metadata.types.InheritedFromMetadata;

import java.util.Objects;
import java.util.Optional;

/**
 * The result of an inheritance lookup
 */
public final class InheritanceInfo {
    public static InheritanceInfo of(Node node) {
        Objects.requireNonNull(node, "node");
        return new InheritanceInfo(Tristate.fromBoolean(node.getValue()), node.metadata(InheritedFromMetadata.KEY).getLocation());
    }

    public static InheritanceInfo empty() {
        return new InheritanceInfo(Tristate.UNDEFINED, null);
    }

    private final Tristate result;
    private final String location;

    private InheritanceInfo(Tristate result, String location) {
        this.result = result;
        this.location = location;
    }

    public Optional<String> getLocation() {
        return Optional.ofNullable(this.location);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof InheritanceInfo)) return false;
        final InheritanceInfo other = (InheritanceInfo) o;
        return this.result == other.result && this.getLocation().equals(other.getLocation());
    }

    @Override
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + this.result.hashCode();
        result = result * PRIME + this.getLocation().hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "InheritanceInfo(result=" + this.result + ", location=" + this.getLocation() + ")";
    }

    public Tristate getResult() {
        return this.result;
    }
}
