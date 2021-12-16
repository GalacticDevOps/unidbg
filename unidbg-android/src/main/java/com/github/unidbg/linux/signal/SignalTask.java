package com.github.unidbg.linux.signal;

import com.github.unidbg.AbstractEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.pointer.UnidbgPointer;
import com.github.unidbg.thread.ThreadTask;
import unicorn.Arm64Const;
import unicorn.ArmConst;

public class SignalTask extends ThreadTask {

    private final UnidbgPointer stack;
    private final int signum;
    private final SigAction action;

    public SignalTask(Emulator<?> emulator, int signum, SigAction action) {
        super(emulator.getReturnAddress());
        this.stack = allocateStack(emulator);
        this.signum = signum;
        this.action = action;
    }

    @Override
    public String toString() {
        return "SignalTask sa_handler=" + action.sa_handler + ", stack=" + stack + ", signum=" + signum;
    }

    private MemoryBlock infoBlock;

    @Override
    public void destroy(AbstractEmulator<?> emulator) {
        super.destroy(emulator);

        if (infoBlock != null) {
            infoBlock.free();
            infoBlock = null;
        }
    }

    @Override
    protected Number runThread(AbstractEmulator<?> emulator) {
        Backend backend = emulator.getBackend();
        if (action.needSigInfo()) {
            infoBlock = emulator.getMemory().malloc(128, true);
            infoBlock.getPointer().setInt(0, signum);
        }
        if (emulator.is32Bit()) {
            backend.reg_write(ArmConst.UC_ARM_REG_SP, stack.peer);
            backend.reg_write(ArmConst.UC_ARM_REG_R0, signum);
            backend.reg_write(ArmConst.UC_ARM_REG_R1, infoBlock == null ? 0 : infoBlock.getPointer().peer); // siginfo_t *info
            backend.reg_write(ArmConst.UC_ARM_REG_R2, 0); // void *ucontext
            backend.reg_write(ArmConst.UC_ARM_REG_LR, until);
        } else {
            backend.reg_write(Arm64Const.UC_ARM64_REG_SP, stack.peer);
            backend.reg_write(Arm64Const.UC_ARM64_REG_X0, signum);
            backend.reg_write(Arm64Const.UC_ARM64_REG_X1, infoBlock == null ? 0 : infoBlock.getPointer().peer); // siginfo_t *info
            backend.reg_write(Arm64Const.UC_ARM64_REG_X2, 0); // void *ucontext
            backend.reg_write(Arm64Const.UC_ARM64_REG_LR, until);
        }
        UnidbgPointer handler = (UnidbgPointer) action.sa_handler;
        return emulator.emulate(handler.peer, until);
    }

}
