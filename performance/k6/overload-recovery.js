import http from "k6/http";
import { check } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const OVERLOAD_RPS = Number(__ENV.OVERLOAD_RPS || 200);
const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || OVERLOAD_RPS);
const MAX_VUS = Number(__ENV.MAX_VUS || OVERLOAD_RPS * 2);

export const options = {
  summaryTrendStats: ["avg", "min", "med", "max", "p(90)", "p(95)", "p(99)"],
  scenarios: {
    overload: {
      executor: "constant-arrival-rate",
      exec: "overload",
      rate: OVERLOAD_RPS,
      timeUnit: "1s",
      duration: "30s",
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS,
    },
    recovery: {
      executor: "constant-arrival-rate",
      exec: "recovery",
      startTime: "30s",
      rate: 1,
      timeUnit: "1s",
      duration: "30s",
      preAllocatedVUs: 2,
      maxVUs: 4,
    },
  },
  thresholds: {
    "checks{scenario:recovery}": ["rate==1.0"],
    "http_req_failed{scenario:recovery}": ["rate==0"],
    "iterations{scenario:recovery}": ["count>=30"],
  },
};

export function overload() {
  request("overload");
}

export function recovery() {
  const response = request("recovery");
  let body = {};
  try {
    body = response.json();
  } catch (error) {
    body = {};
  }

  check(response, {
    "recovery status is 200": (res) => res.status === 200,
    "recovery auction returns winner": () => body.status === "WINNER",
    "recovery winner is deterministic": () => body.winnerDspId === "dsp-b",
    "recovery terminal count is complete": () => terminalCount(body.dspResultCounts) === 3,
  });
}

function request(phase) {
  return http.post(`${BASE_URL}/publisher/auction`, JSON.stringify({
    providerId: "publisher-demo",
    placementId: "home-top-banner",
    mediaType: "banner",
    width: 300,
    height: 250,
    tmax: 120,
  }), {
    headers: { "Content-Type": "application/json" },
    tags: { test_type: "overload_recovery", phase },
  });
}

function terminalCount(counts = {}) {
  return (counts.validBidCount || 0)
    + (counts.noBidCount || 0)
    + (counts.timeoutCount || 0)
    + (counts.invalidBidCount || 0)
    + (counts.errorCount || 0);
}
