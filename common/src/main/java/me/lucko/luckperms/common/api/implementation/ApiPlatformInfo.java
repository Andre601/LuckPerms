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

package me.lucko.luckperms.common.api.implementation;

import me.lucko.luckperms.api.platform.PlatformInfo;
import me.lucko.luckperms.api.platform.PlatformType;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public class ApiPlatformInfo implements PlatformInfo {
    private final LuckPermsPlugin plugin;

    public ApiPlatformInfo(LuckPermsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NonNull String getVersion() {
        return this.plugin.getBootstrap().getVersion();
    }

    @Override
    public double getApiVersion() {
        return 5.0;
    }

    @Override
    public @NonNull PlatformType getType() {
        return this.plugin.getBootstrap().getType();
    }

    @Override
    public @NonNull Set<UUID> getUniqueConnections() {
        return Collections.unmodifiableSet(this.plugin.getConnectionListener().getUniqueConnections());
    }

    @Override
    public long getStartTime() {
        return this.plugin.getBootstrap().getStartupTime();
    }
}
