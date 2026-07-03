package com.bbororo.rtb.ssp.dspgateway;

import java.util.List;

public final class StaticDspEndpointRegistry implements DspEndpointRegistry {

    private final List<DspEndpoint> endpoints;

    public StaticDspEndpointRegistry(List<DspEndpoint> endpoints) {
        this.endpoints = List.copyOf(endpoints);
    }

    @Override
    public List<DspEndpoint> endpoints() {
        return endpoints;
    }
}
