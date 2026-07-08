import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const INGRESS_MODE = __ENV.INGRESS_MODE || "slot";

export const options = {
  scenarios: {
    auction_smoke: {
      executor: "shared-iterations",
      vus: 1,
      iterations: 5,
      maxDuration: "30s",
    },
  },
  thresholds: {
    checks: ["rate==1.0"],
    http_req_failed: ["rate==0"],
    http_req_duration: ["p(95)<1000"],
  },
};

export default function () {
  const response = http.post(`${BASE_URL}${auctionPath()}`, payload(), {
    headers: {
      "Content-Type": "application/json",
    },
    tags: {
      test_type: "smoke",
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
  });

  sleep(0.2);
}

function auctionPath() {
  return INGRESS_MODE === "openrtb" ? "/openrtb/auction" : "/publisher/auction";
}

function payload() {
  if (INGRESS_MODE === "openrtb") {
    const requestId = `req-k6-smoke-${__VU}-${__ITER}-${Date.now()}`;
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
