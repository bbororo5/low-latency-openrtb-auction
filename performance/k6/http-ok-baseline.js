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
    http_ok_baseline: {
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
  const response = http.get(`${BASE_URL}/ok`, {
    tags: {
      test_type: "http_ok_baseline",
    },
  });

  check(response, {
    "status is 200": (res) => res.status === 200,
    "body is OK": (res) => res.body === "OK",
  });
}
