import http from "k6/http";
import { check } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const RPS = Number(__ENV.RPS || 10);
const DURATION = __ENV.DURATION || "1m";
const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || Math.max(10, RPS));
const MAX_VUS = Number(__ENV.MAX_VUS || Math.max(50, RPS * 2));
const HOST_ALIAS = __ENV.HOST_ALIAS || "";

export const options = {
  summaryTrendStats: ["avg", "min", "med", "max", "p(90)", "p(95)", "p(99)"],
  scenarios: {
    auction_capacity: {
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
  hosts: hostAliases(),
};

export default function () {
  const requestId = `req-k6-capacity-${__VU}-${__ITER}-${Date.now()}`;
  const payload = JSON.stringify({
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

  const response = http.post(`${BASE_URL}/openrtb/auction`, payload, {
    headers: {
      "Content-Type": "application/json",
    },
    tags: {
      test_type: "capacity",
      media_type: "banner",
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
    "dsp-b wins default capacity topology": () => body.winnerDspId === "dsp-b",
    "two DSPs bid": () => body.dspResultCounts?.bidCount === 2,
    "one DSP returns no-bid": () => body.dspResultCounts?.noBidCount === 1,
    "no DSP times out": () => body.dspResultCounts?.timeoutCount === 0,
    "no invalid bids": () => body.dspResultCounts?.invalidBidCount === 0,
    "no dsp errors": () => body.dspResultCounts?.errorCount === 0,
  });
}

function hostAliases() {
  if (!HOST_ALIAS) {
    return {};
  }

  const [hostname, address] = HOST_ALIAS.split("=");
  if (!hostname || !address) {
    throw new Error("HOST_ALIAS must use hostname=address format");
  }
  return {
    [hostname.trim()]: address.trim(),
  };
}
