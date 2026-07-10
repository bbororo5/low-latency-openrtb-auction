package com.bbororo.rtb.ssp.bidjudge;

@FunctionalInterface
public interface DspTerminalResultObserver {

    void record(String dspId, DspTerminalResult terminalResult);
}
