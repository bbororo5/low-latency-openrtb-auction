import http from "k6/http";
import { check } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const INGRESS_MODE = __ENV.INGRESS_MODE || "slot";
const RPS = Number(__ENV.RPS || 10);
const DURATION = __ENV.DURATION || "1m";
const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || Math.max(10, RPS));
const MAX_VUS = Number(__ENV.MAX_VUS || Math.max(50, RPS * 2));

export const options = {
  summaryTrendStats: ["avg", "min", "med", "max", "p(90)", "p(95)", "p(99)"],
  scenarios: {
    auction_load_baseline: {
      executor: "constant-arrival-rate",
      rate: RPS,
      timeUnit: "1s",
      duration: DURATION,
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS,
    },
  },
  thresholds: {
    checks: ["rate==1.0"],
    http_req_failed: ["rate==0"],
  },
};

export default function () {
  const response = http.post(`${BASE_URL}${auctionPath()}`, payload(), {
    headers: {
      "Content-Type": "application/json",
    },
    tags: {
      test_type: "load_baseline",
      media_type: "banner",
      ingress_mode: INGRESS_MODE,
    },
  });

  let body = {};
  try {
    body = response.json();
  } catch (error) {
    body = {};
  }

  check(response, {
    "status is 200": (res) => res.status === 200,
    "auction returns winner": () => body.status === "WINNER",
    "dsp-b wins default topology": () => body.winnerDspId === "dsp-b",
    "two DSPs bid": () => body.dspResultCounts?.bidCount === 2,
    "one DSP returns no-bid": () => body.dspResultCounts?.noBidCount === 1,
    "one DSP times out": () => body.dspResultCounts?.timeoutCount === 1,
    "no invalid bids": () => body.dspResultCounts?.invalidBidCount === 0,
    "no dsp errors": () => body.dspResultCounts?.errorCount === 0,
  });
}

function auctionPath() {
  return INGRESS_MODE === "openrtb" ? "/openrtb/auction" : "/publisher/auction";
}

function payload() {
  if (INGRESS_MODE === "openrtb") {
    const requestId = `req-k6-load-${__VU}-${__ITER}-${Date.now()}`;
    return JSON.stringify({
      id: requestId,
      imp: [
        {
          id: "imp-001",
          banner: {
            w: 300,
            h: 250,
          },
          bidfloor: 0.5,
          bidfloorcur: "USD",
        },
      ],
      tmax: 120,
      at: 1,
    });
  }

  return JSON.stringify({
    providerId: "publisher-demo",
    placementId: "home-top-banner",
    mediaType: "banner",
    width: 300,
    height: 250,
    tmax: 120,
  });
}
