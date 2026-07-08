import http from "k6/http";
import { check } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const RPS = Number(__ENV.RPS || 100);
const DURATION = __ENV.DURATION || "1m";
const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || Math.max(20, RPS));
const MAX_VUS = Number(__ENV.MAX_VUS || Math.max(100, RPS * 2));

export const options = {
  summaryTrendStats: ["avg", "min", "med", "max", "p(90)", "p(95)", "p(99)"],
  scenarios: {
    openrtb_json_baseline: {
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
  const requestId = `req-k6-json-baseline-${__VU}-${__ITER}-${Date.now()}`;
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

  const response = http.post(`${BASE_URL}/baseline/openrtb-json`, payload, {
    headers: {
      "Content-Type": "application/json",
    },
    tags: {
      test_type: "openrtb_json_baseline",
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
    "response id matches request": () => body.id === requestId,
    "baseline seat returned": () => body.seatbid?.[0]?.seat === "baseline-seat",
    "baseline bid returned": () => body.seatbid?.[0]?.bid?.[0]?.id === "baseline-bid-001",
    "baseline bid references imp": () => body.seatbid?.[0]?.bid?.[0]?.impid === "imp-001",
  });
}
