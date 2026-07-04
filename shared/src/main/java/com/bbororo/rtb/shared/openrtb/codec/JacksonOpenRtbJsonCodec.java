package com.bbororo.rtb.shared.openrtb.codec;

import com.bbororo.rtb.shared.openrtb.Banner;
import com.bbororo.rtb.shared.openrtb.Bid;
import com.bbororo.rtb.shared.openrtb.BidRequest;
import com.bbororo.rtb.shared.openrtb.BidResponse;
import com.bbororo.rtb.shared.openrtb.Imp;
import com.bbororo.rtb.shared.openrtb.NativeAd;
import com.bbororo.rtb.shared.openrtb.SeatBid;
import com.bbororo.rtb.shared.openrtb.Video;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class JacksonOpenRtbJsonCodec implements OpenRtbJsonCodec {

    private final ObjectMapper objectMapper;

    public JacksonOpenRtbJsonCodec() {
        this(new ObjectMapper());
    }

    public JacksonOpenRtbJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String encodeRequest(BidRequest bidRequest) {
        try {
            return objectMapper.writeValueAsString(requestToJson(bidRequest));
        } catch (JsonProcessingException e) {
            throw new OpenRtbJsonCodecException("Failed to encode BidRequest", e);
        }
    }

    @Override
    public BidRequest decodeRequest(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            return new BidRequest(
                    text(root, "id"),
                    impressions(root.path("imp")),
                    integer(root, "tmax"),
                    integer(root, "at")
            );
        } catch (JsonProcessingException e) {
            throw new OpenRtbJsonCodecException("Failed to decode BidRequest", e);
        }
    }

    @Override
    public String encodeResponse(BidResponse bidResponse) {
        try {
            return objectMapper.writeValueAsString(responseToJson(bidResponse));
        } catch (JsonProcessingException e) {
            throw new OpenRtbJsonCodecException("Failed to encode BidResponse", e);
        }
    }

    @Override
    public BidResponse decodeResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            return new BidResponse(
                    text(root, "id"),
                    seatBids(root.path("seatbid")),
                    text(root, "cur")
            );
        } catch (JsonProcessingException e) {
            throw new OpenRtbJsonCodecException("Failed to decode BidResponse", e);
        }
    }

    private ObjectNode requestToJson(BidRequest bidRequest) {
        ObjectNode root = objectMapper.createObjectNode();
        put(root, "id", bidRequest.id());
        root.set("imp", impressionsToJson(bidRequest.imp()));
        put(root, "tmax", bidRequest.tmax());
        put(root, "at", bidRequest.at());
        return root;
    }

    private ArrayNode impressionsToJson(List<Imp> impressions) {
        ArrayNode array = objectMapper.createArrayNode();
        if (impressions == null) {
            return array;
        }
        for (Imp imp : impressions) {
            ObjectNode node = objectMapper.createObjectNode();
            put(node, "id", imp.id());
            put(node, "bidfloor", imp.bidfloor());
            put(node, "bidfloorcur", imp.bidfloorcur());
            putMediaObjects(node, imp);
            array.add(node);
        }
        return array;
    }

    private List<Imp> impressions(JsonNode impressionsNode) {
        List<Imp> impressions = new ArrayList<>();
        if (!impressionsNode.isArray()) {
            return impressions;
        }
        for (JsonNode node : impressionsNode) {
            impressions.add(new Imp(
                    text(node, "id"),
                    banner(node.path("banner")),
                    video(node.path("video")),
                    nativeAd(node.path("native")),
                    decimal(node, "bidfloor"),
                    text(node, "bidfloorcur")
            ));
        }
        return impressions;
    }

    private void putMediaObjects(ObjectNode node, Imp imp) {
        if (imp.banner() != null) {
            node.set("banner", banner(imp.banner()));
        }
        if (imp.video() != null) {
            node.set("video", video(imp.video()));
        }
        if (imp.nativeAd() != null) {
            node.set("native", nativeAd(imp.nativeAd()));
        }
    }

    private ObjectNode banner(Banner banner) {
        ObjectNode node = objectMapper.createObjectNode();
        put(node, "w", banner.w());
        put(node, "h", banner.h());
        return node;
    }

    private Banner banner(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        return new Banner(integer(node, "w"), integer(node, "h"));
    }

    private ObjectNode video(Video video) {
        ObjectNode node = objectMapper.createObjectNode();
        put(node, "w", video.w());
        put(node, "h", video.h());
        putArray(node, "mimes", video.mimes());
        put(node, "minduration", video.minduration());
        put(node, "maxduration", video.maxduration());
        putArray(node, "protocols", video.protocols());
        return node;
    }

    private Video video(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        return new Video(
                integer(node, "w"),
                integer(node, "h"),
                strings(node.path("mimes")),
                integer(node, "minduration"),
                integer(node, "maxduration"),
                integers(node.path("protocols"))
        );
    }

    private ObjectNode nativeAd(NativeAd nativeAd) {
        ObjectNode node = objectMapper.createObjectNode();
        put(node, "request", nativeAd.request());
        return node;
    }

    private NativeAd nativeAd(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        return new NativeAd(text(node, "request"));
    }

    private ObjectNode responseToJson(BidResponse bidResponse) {
        ObjectNode root = objectMapper.createObjectNode();
        put(root, "id", bidResponse.id());
        root.set("seatbid", seatBidsToJson(bidResponse.seatbid()));
        put(root, "cur", bidResponse.cur());
        return root;
    }

    private ArrayNode seatBidsToJson(List<SeatBid> seatBids) {
        ArrayNode array = objectMapper.createArrayNode();
        if (seatBids == null) {
            return array;
        }
        for (SeatBid seatBid : seatBids) {
            ObjectNode node = objectMapper.createObjectNode();
            put(node, "seat", seatBid.seat());
            node.set("bid", bidsToJson(seatBid.bid()));
            array.add(node);
        }
        return array;
    }

    private ArrayNode bidsToJson(List<Bid> bids) {
        ArrayNode array = objectMapper.createArrayNode();
        if (bids == null) {
            return array;
        }
        for (Bid bid : bids) {
            ObjectNode node = objectMapper.createObjectNode();
            put(node, "id", bid.id());
            put(node, "impid", bid.impid());
            put(node, "price", bid.price());
            put(node, "cid", bid.cid());
            put(node, "crid", bid.crid());
            putArray(node, "adomain", bid.adomain());
            put(node, "mtype", bid.mtype());
            put(node, "adm", bid.adm());
            array.add(node);
        }
        return array;
    }

    private List<SeatBid> seatBids(JsonNode seatBidNode) {
        List<SeatBid> seatBids = new ArrayList<>();
        if (!seatBidNode.isArray()) {
            return seatBids;
        }
        for (JsonNode node : seatBidNode) {
            seatBids.add(new SeatBid(text(node, "seat"), bids(node.path("bid"))));
        }
        return seatBids;
    }

    private List<Bid> bids(JsonNode bidNode) {
        List<Bid> bids = new ArrayList<>();
        if (!bidNode.isArray()) {
            return bids;
        }
        for (JsonNode node : bidNode) {
            bids.add(new Bid(
                    text(node, "id"),
                    text(node, "impid"),
                    decimal(node, "price"),
                    text(node, "cid"),
                    text(node, "crid"),
                    strings(node.path("adomain")),
                    integer(node, "mtype"),
                    text(node, "adm")
            ));
        }
        return bids;
    }

    private List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (!node.isArray()) {
            return values;
        }
        for (JsonNode value : node) {
            values.add(value.asText());
        }
        return values;
    }

    private List<Integer> integers(JsonNode node) {
        List<Integer> values = new ArrayList<>();
        if (!node.isArray()) {
            return values;
        }
        for (JsonNode value : node) {
            values.add(value.asInt());
        }
        return values;
    }

    private void put(ObjectNode node, String field, String value) {
        if (value != null) {
            node.put(field, value);
        }
    }

    private void put(ObjectNode node, String field, Integer value) {
        if (value != null) {
            node.put(field, value);
        }
    }

    private void put(ObjectNode node, String field, BigDecimal value) {
        if (value != null) {
            node.put(field, value);
        }
    }

    private <T> void putArray(ObjectNode node, String field, List<T> values) {
        if (values != null) {
            node.set(field, objectMapper.valueToTree(values));
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.decimalValue();
    }
}
