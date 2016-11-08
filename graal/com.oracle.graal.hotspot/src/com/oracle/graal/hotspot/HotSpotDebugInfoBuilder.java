/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.hotspot;

import static jdk.vm.ci.code.BytecodeFrame.isPlaceholderBci;

import com.oracle.graal.compiler.gen.DebugInfoBuilder;
import com.oracle.graal.debug.GraalError;
import com.oracle.graal.lir.VirtualStackSlot;
import com.oracle.graal.nodes.FrameState;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.NodeValueMap;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.StackLockValue;
import jdk.vm.ci.code.VirtualObject;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.meta.JavaValue;

/**
 * Extends {@link DebugInfoBuilder} to allocate the extra debug information required for locks.
 */
public class HotSpotDebugInfoBuilder extends DebugInfoBuilder {

    private final HotSpotLockStack lockStack;

    private int maxInterpreterFrameSize;

    private HotSpotCodeCacheProvider codeCacheProvider;

    public HotSpotDebugInfoBuilder(NodeValueMap nodeValueMap, HotSpotLockStack lockStack, HotSpotLIRGenerator gen) {
        super(nodeValueMap);
        this.lockStack = lockStack;
        this.codeCacheProvider = gen.getProviders().getCodeCache();
    }

    public HotSpotLockStack lockStack() {
        return lockStack;
    }

    public int maxInterpreterFrameSize() {
        return maxInterpreterFrameSize;
    }

    @Override
    protected JavaValue computeLockValue(FrameState state, int lockIndex) {
        int lockDepth = lockIndex;
        if (state.outerFrameState() != null) {
            lockDepth += state.outerFrameState().nestedLockDepth();
        }
        VirtualStackSlot slot = lockStack.makeLockSlot(lockDepth);
        ValueNode lock = state.lockAt(lockIndex);
        JavaValue object = toJavaValue(lock);
        boolean eliminated = object instanceof VirtualObject || state.monitorIdAt(lockIndex) == null;
        assert state.monitorIdAt(lockIndex) == null || state.monitorIdAt(lockIndex).getLockDepth() == lockDepth;
        return new StackLockValue(object, slot, eliminated);
    }

    @Override
    protected BytecodeFrame computeFrameForState(FrameState state) {
        if (isPlaceholderBci(state.bci) && state.bci != BytecodeFrame.BEFORE_BCI) {
            // This is really a hard error since an incorrect state could crash hotspot
            throw GraalError.shouldNotReachHere("Invalid state " + BytecodeFrame.getPlaceholderBciName(state.bci) + " " + state);
        }
        BytecodeFrame result = super.computeFrameForState(state);
        maxInterpreterFrameSize = Math.max(maxInterpreterFrameSize, codeCacheProvider.interpreterFrameSize(result));
        return result;
    }
}